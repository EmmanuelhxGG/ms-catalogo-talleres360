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

    public record ProductInput(
            @NotBlank @Size(max = 60) String sku,
            @NotBlank @Size(max = 160) String name,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            @Min(0) int stock,
            boolean active) {}
    public record ProductView(Long id, String sku, String name, BigDecimal price, int stock, boolean active, boolean available) {
        public static ProductView from(Product p) {
            return new ProductView(p.getId(), p.getSku(), p.getName(), p.getPrice(), p.getStock(), p.isActive(), p.isActive() && p.getStock() > 0);
        }
    }
    public record StockItem(@NotNull @Positive Long productId, @NotNull @Positive @Max(1000000) Integer quantity) {}
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
            if (diferencia > 0 && (!p.isActive() || p.getStock() < diferencia))
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
        if (products.existsBySkuIgnoreCase(input.sku().trim()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El SKU ya existe");
        Product p = new Product();
        apply(p, input);
        return ProductView.from(products.save(p));
    }
    @Transactional
    public ProductView update(Long id, ProductInput input) {
        Product p = products.findWithLockById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        products.findBySkuIgnoreCase(input.sku().trim()).ifPresent(existing -> {
            if (!existing.getId().equals(id))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El SKU ya existe");
        });
        apply(p, input);
        return ProductView.from(products.save(p));
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
        p.setSku(input.sku().trim().toUpperCase());
        p.setName(input.name().trim());
        p.setPrice(input.price());
        p.setStock(input.stock());
        p.setActive(input.active());
    }
}
