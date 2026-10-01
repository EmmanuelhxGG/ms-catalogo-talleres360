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
    @Value("${app.internal-key}") private String internalKey;

    private void check(String candidate) {
        if (internalKey.isBlank() || candidate == null ||
                !MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8), candidate.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial interna inválida");
    }
    @GetMapping("/api/products")
    public List<ProductView> list(@RequestHeader(value = "X-Internal-Key", required = false) String key) { check(key); return service.list(); }
    @GetMapping("/api/products/{id}")
    public ProductView get(@RequestHeader(value = "X-Internal-Key", required = false) String key, @PathVariable Long id) { check(key); return service.get(id); }
    @PostMapping("/api/products") @ResponseStatus(HttpStatus.CREATED)
    public ProductView create(@RequestHeader(value = "X-Internal-Key", required = false) String key, @Valid @RequestBody ProductInput input) {
        check(key); return service.create(input);
    }
    @PutMapping("/api/products/{id}")
    public ProductView update(@RequestHeader(value = "X-Internal-Key", required = false) String key, @PathVariable Long id, @Valid @RequestBody ProductInput input) {
        check(key); return service.update(id, input);
    }
    @PostMapping("/internal/stock-consumptions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consume(@RequestHeader(value = "X-Internal-Key", required = false) String key, @Valid @RequestBody ConsumptionRequest input) {
        check(key); service.consume(input);
    }
}
