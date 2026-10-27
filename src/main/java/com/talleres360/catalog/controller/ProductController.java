package com.talleres360.catalog.controller;

import com.talleres360.catalog.service.CatalogService;
import com.talleres360.catalog.service.CatalogService.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController @RequiredArgsConstructor
public class ProductController {
    private final CatalogService service;
    private final com.talleres360.catalog.service.CategoriaService categorias;
    @Value("${app.internal-key}") private String internalKey;

    private void check(String candidate) {
        if (internalKey.isBlank() || candidate == null ||
                !MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial interna inválida");
    }
    private void admin(String key, String role) {
        check(key);
        if (!"Admin".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Operación reservada al administrador");
    }
    @GetMapping("/api/catalogo")
    public List<CatalogoView> catalogo(@RequestHeader(value = "X-Internal-Key", required = false) String key) {
        check(key); return service.catalogo();
    }
    @GetMapping("/internal/products/{id}")
    public ProductView interno(@RequestHeader(value = "X-Internal-Key", required = false) String key, @PathVariable Long id) {
        check(key); return service.get(id);
    }
    @GetMapping("/api/products")
    public List<ProductView> list(@RequestHeader(value = "X-Internal-Key", required = false) String key,
        @RequestHeader(value = "X-Actor-Role", required = false) String role) { admin(key, role); return service.list(); }
    @GetMapping("/api/products/{id}")
    public ProductView get(@RequestHeader(value = "X-Internal-Key", required = false) String key,
        @RequestHeader(value = "X-Actor-Role", required = false) String role, @PathVariable Long id) { admin(key, role); return service.get(id); }
    @PostMapping("/api/products/carga-inicial")
    public Object inicial(@RequestHeader(value = "X-Internal-Key", required = false) String key,
        @RequestHeader(value = "X-Actor-Role", required = false) String role) {
        admin(key, role); return java.util.Map.of("agregados", service.cargarRepuestosEjemplo());
    }
    @PostMapping("/api/products") @ResponseStatus(HttpStatus.CREATED)
    public ProductView create(@RequestHeader(value = "X-Internal-Key", required = false) String key,
        @RequestHeader(value = "X-Actor-Role", required = false) String role, @Valid @RequestBody ProductInput input) {
        admin(key, role); return service.create(input);
    }
    @PutMapping("/api/products/{id}")
    public ProductView update(@RequestHeader(value = "X-Internal-Key", required = false) String key,
        @RequestHeader(value = "X-Actor-Role", required = false) String role, @PathVariable Long id, @Valid @RequestBody ProductInput input) {
        admin(key, role); return service.update(id, input);
    }
    @DeleteMapping("/api/products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@RequestHeader(value="X-Internal-Key", required=false) String key,
        @RequestHeader(value = "X-Actor-Role", required = false) String role, @PathVariable Long id) {
        admin(key, role); service.eliminar(id);
    }
    @GetMapping("/api/categorias") public Object categorias(@RequestHeader(value="X-Internal-Key", required=false) String key) {
        check(key); return categorias.listar();
    }
    @PostMapping("/api/categorias") @ResponseStatus(HttpStatus.CREATED)
    public Object crearCategoria(@RequestHeader(value="X-Internal-Key", required=false) String key,
        @RequestHeader(value = "X-Actor-Role", required = false) String role,
        @Valid @RequestBody com.talleres360.catalog.service.CategoriaService.Entrada e) { admin(key, role); return categorias.guardar(null,e); }
    @PutMapping("/api/categorias/{id}") public Object editarCategoria(@RequestHeader(value="X-Internal-Key", required=false) String key,
        @RequestHeader(value = "X-Actor-Role", required = false) String role,
        @PathVariable Long id, @Valid @RequestBody com.talleres360.catalog.service.CategoriaService.Entrada e) { admin(key, role); return categorias.guardar(id,e); }
    @DeleteMapping("/api/categorias/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCategoria(@RequestHeader(value="X-Internal-Key", required=false) String key,
        @RequestHeader(value = "X-Actor-Role", required = false) String role, @PathVariable Long id) { admin(key, role); categorias.eliminar(id); }
    @PostMapping("/internal/stock-consumptions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consume(@RequestHeader(value = "X-Internal-Key", required = false) String key, @Valid @RequestBody ConsumptionRequest input) {
        check(key); service.consume(input);
    }
    @GetMapping("/internal/stock-reservations/{orderId}")
    public ReservaView reserva(@RequestHeader(value = "X-Internal-Key", required = false) String key,
                               @PathVariable @jakarta.validation.constraints.Positive Long orderId) {
        check(key); return service.reserva(orderId);
    }
    @PutMapping("/internal/stock-reservations") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reservar(@RequestHeader(value = "X-Internal-Key", required = false) String key,
                         @Valid @RequestBody SolicitudReserva input) {
        check(key); service.sincronizarReserva(input);
    }
}
