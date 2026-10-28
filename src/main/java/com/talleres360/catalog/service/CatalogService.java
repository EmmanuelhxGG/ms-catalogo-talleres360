package com.talleres360.catalog.service;

import com.talleres360.catalog.model.Product;
import com.talleres360.catalog.model.StockConsumption;
import com.talleres360.catalog.model.ReservaStock;
import com.talleres360.catalog.repository.ProductRepository;
import com.talleres360.catalog.repository.StockConsumptionRepository;
import com.talleres360.catalog.repository.ReservaStockRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeSet;

@Service @RequiredArgsConstructor
public class CatalogService {
    private final ProductRepository products;
    private final StockConsumptionRepository consumptions;
    private final ReservaStockRepository reservas;
    private final com.talleres360.catalog.repository.CategoriaRepository categorias;

    public record ProductInput(
            @Size(max = 60) String sku,
            @NotBlank @Size(max = 160) String name,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            @Min(0) int stock,
            boolean active, @Positive Long categoriaId) {}
    public record EstadoProducto(@NotNull Boolean active) {}
    public record ProductView(Long id, String sku, String name, BigDecimal price, int stock, boolean active, boolean available, Long categoriaId, String categoriaNombre, boolean categoriaActiva) {
        public static ProductView from(Product p) {
            boolean activa = p.isActive() && (p.getCategoria() == null || p.getCategoria().isActiva());
            return new ProductView(p.getId(), p.getSku(), p.getName(), p.getPrice(), p.getStock(), p.isActive(), activa && p.getStock() > 0,
                p.getCategoria() == null ? null : p.getCategoria().getId(), p.getCategoria() == null ? "General" : p.getCategoria().getNombre(), p.getCategoria() == null || p.getCategoria().isActiva());
        }
    }
    public record StockItem(@NotNull @Positive Long productId, @NotNull @Positive @Max(1000000) Integer quantity) {}
    /** Vista de venta: no expone existencias exactas ni metadatos internos de inventario. */
    public record CatalogoView(Long id, String sku, String name, BigDecimal price,
                              boolean active, boolean available, Long categoriaId, String categoriaNombre) {
        public static CatalogoView from(ProductView p) {
            return new CatalogoView(p.id(), p.sku(), p.name(), p.price(), p.active(), p.available(), p.categoriaId(), p.categoriaNombre());
        }
    }

    @Transactional(readOnly = true)
    public List<CatalogoView> catalogo() {
        return list().stream().filter(p -> p.active() && p.categoriaActiva()).map(CatalogoView::from).toList();
    }

    /** Carga explícita, idempotente por SKU. Nunca repone ni sobrescribe productos existentes. */
    @Transactional
    public int cargarRepuestosEjemplo() {
        var iniciales = List.of(
            new ProductInput("DEMO-ACEITE-5W30", "Aceite de motor 5W30 4 L", new BigDecimal("32990"), 20, true, null),
            new ProductInput("DEMO-FILTRO-ACEITE", "Filtro de aceite", new BigDecimal("7990"), 30, true, null),
            new ProductInput("DEMO-FILTRO-AIRE", "Filtro de aire", new BigDecimal("11990"), 18, true, null),
            new ProductInput("DEMO-PASTILLAS", "Juego de pastillas de freno", new BigDecimal("24990"), 12, true, null),
            new ProductInput("DEMO-BUJIA", "Bujía de encendido", new BigDecimal("4990"), 40, true, null),
            new ProductInput("DEMO-REFRIGERANTE", "Refrigerante 1 L", new BigDecimal("5990"), 24, true, null),
            new ProductInput("DEMO-LIMPIAPARABRISAS", "Limpiaparabrisas universal", new BigDecimal("9990"), 16, true, null),
            new ProductInput("DEMO-LIQUIDO-FRENOS", "Líquido de frenos DOT 4", new BigDecimal("6990"), 15, true, null));
        int agregados = 0;
        for (var entrada : iniciales) {
            if (!products.existsBySkuIgnoreCase(entrada.sku())) { create(entrada); agregados++; }
        }
        return agregados;
    }
    public record ConsumptionRequest(@NotBlank String eventId, @NotNull @Positive Long orderId,
                                     @NotNull @Size(max = 200) List<@NotNull @Valid StockItem> items) {}
    public record SolicitudReserva(@NotNull @Positive Long orderId, @Positive long revision,
                                   @NotNull @Size(max = 200) List<@NotNull @Valid StockItem> items) {}
    public record ReservaView(long revision, Map<Long, Integer> quantities) {}

    @Transactional(readOnly = true)
    public ReservaView reserva(Long ordenId) {
        return reservas.findById(ordenId)
                .map(r -> new ReservaView(r.getRevision(), Map.copyOf(r.getCantidades())))
                .orElseGet(() -> new ReservaView(0, Map.of()));
    }

    @Transactional
    public void sincronizarReserva(SolicitudReserva input) {
        var reserva = reservas.bloquear(input.orderId()).orElseGet(() -> {
            var nueva = new ReservaStock();
            nueva.setOrdenId(input.orderId());
            // Si dos primeras asignaciones compiten, una inserción falla y el outbox la reintenta.
            return reservas.saveAndFlush(nueva);
        });
        if (input.revision() <= reserva.getRevision()) return;
        var deseadas = new HashMap<Long, Integer>();
        for (StockItem item : input.items()) deseadas.merge(item.productId(), item.quantity(), Math::addExact);
        var ids = new TreeSet<>(reserva.getCantidades().keySet());
        ids.addAll(deseadas.keySet());
        for (Long id : ids) {
            Product p = products.findWithLockById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
            int diferencia = deseadas.getOrDefault(id, 0) - reserva.getCantidades().getOrDefault(id, 0);
            if (diferencia > 0 && (!ProductView.from(p).active() || p.getStock() < diferencia))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente: " + p.getName());
            p.setStock(Math.subtractExact(p.getStock(), diferencia));
        }
        reserva.getCantidades().clear();
        reserva.getCantidades().putAll(deseadas);
        reserva.setRevision(input.revision());
    }

    @Transactional(readOnly = true)
    public List<ProductView> list() {
        return products.findAllByOrderByNameAsc().stream().map(ProductView::from).toList();
    }
    @Transactional(readOnly = true)
    public ProductView get(Long id) { return ProductView.from(find(id)); }

    @Transactional
    public ProductView create(ProductInput input) {
        if (input.sku() != null && !input.sku().isBlank() && products.existsBySkuIgnoreCase(input.sku().trim()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El SKU ya existe");
        Product p = new Product();
        apply(p, input);
        return ProductView.from(products.save(p));
    }
    @Transactional
    public ProductView update(Long id, ProductInput input) {
        Product p = products.findWithLockById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        if (input.sku() != null && !input.sku().isBlank()) products.findBySkuIgnoreCase(input.sku().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(id))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El SKU ya existe");
        });
        apply(p, input);
        return ProductView.from(products.save(p));
    }
    @Transactional public void eliminar(Long id) { find(id).setActive(false); }
    @Transactional
    public ProductView cambiarEstado(Long id, EstadoProducto input) {
        Product p = products.findWithLockById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        if (Boolean.TRUE.equals(input.active()) && p.getCategoria() != null && !p.getCategoria().isActiva())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Habilita primero la categoría del producto");
        p.setActive(input.active());
        return ProductView.from(p);
    }
    @Transactional
    public void consume(ConsumptionRequest request) {
        if (consumptions.existsById(request.eventId())) return;
        var grouped = new HashMap<Long, Integer>();
        for (StockItem item : request.items()) grouped.merge(item.productId(), item.quantity(), Math::addExact);
        for (var entry : grouped.entrySet().stream().sorted(Comparator.comparingLong(java.util.Map.Entry::getKey)).toList()) {
            Product p = products.findWithLockById(entry.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
            if (!p.isActive() || p.getStock() < entry.getValue())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente: " + p.getName());
            p.setStock(p.getStock() - entry.getValue());
        }
        StockConsumption marker = new StockConsumption();
        marker.setEventId(request.eventId());
        marker.setOrderId(request.orderId());
        consumptions.save(marker);
    }
    private Product find(Long id) {
        return products.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
    }
    private void apply(Product p, ProductInput input) {
        // Compatibilidad con integraciones anteriores; el ID visible lo asigna la base de datos.
        if (input.sku() != null && !input.sku().isBlank())
            p.setSku(input.sku().trim().toUpperCase(java.util.Locale.ROOT));
        else if (p.getSku() == null)
            p.setSku("REP-" + java.util.UUID.randomUUID());
        p.setName(input.name().trim());
        p.setPrice(input.price());
        p.setStock(input.stock());
        p.setActive(input.active());
        p.setCategoria(input.categoriaId() == null ? null : categorias.findById(input.categoriaId())
            .filter(com.talleres360.catalog.model.Categoria::isActiva)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría no disponible")));
    }
}
