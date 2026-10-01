package com.talleres360.catalog;

import com.talleres360.catalog.service.CatalogService;
import com.talleres360.catalog.service.CatalogService.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "INTERNAL_API_KEY=test-key")
@AutoConfigureMockMvc
class CatalogServiceTest {
    @Autowired CatalogService service;
    @Autowired MockMvc mvc;

    @Test
    void descuentaUnaSolaVezPorEventoYRechazaStockInsuficiente() {
        ProductView product = service.create(new ProductInput("FILTRO-001", "Filtro", BigDecimal.valueOf(12000), 3, true));
        service.consume(new ConsumptionRequest("evento-1", 7L, List.of(new StockItem(product.id(), 2))));
        service.consume(new ConsumptionRequest("evento-1", 7L, List.of(new StockItem(product.id(), 2))));
        assertEquals(1, service.get(product.id()).stock());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.consume(new ConsumptionRequest("evento-2", 8L, List.of(new StockItem(product.id(), 2)))));
        assertEquals(1, service.get(product.id()).stock());
    }

    @Test
    void laApiExigeClaveInterna() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products").header("X-Internal-Key", "incorrecta")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/products").header("X-Internal-Key", "test-key")).andExpect(status().isOk());
    }
}
