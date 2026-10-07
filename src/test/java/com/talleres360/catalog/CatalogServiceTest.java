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
        mvc.perform(get("/internal/stock-reservations/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void asignarEditarYCancelarEsIdempotenteInclusoConEventosDesordenados() {
        var p = service.create(new ProductInput("RESERVA-001", "Aceite", BigDecimal.TEN, 10, true));
        var inicial = new SolicitudReserva(101L, 1, List.of(new StockItem(p.id(), 2)));
        service.sincronizarReserva(inicial);
        service.sincronizarReserva(inicial);
        assertEquals(8, service.get(p.id()).stock());
        service.sincronizarReserva(new SolicitudReserva(101L, 2, List.of(new StockItem(p.id(), 5))));
        assertEquals(5, service.get(p.id()).stock());
        service.sincronizarReserva(new SolicitudReserva(101L, 3, List.of(new StockItem(p.id(), 1))));
        assertEquals(9, service.get(p.id()).stock());
        service.sincronizarReserva(new SolicitudReserva(101L, 4, List.of()));
        service.sincronizarReserva(inicial);
        assertEquals(10, service.get(p.id()).stock());
        assertEquals(4, service.reserva(101L).revision());
        assertTrue(service.reserva(101L).quantities().isEmpty());
    }

    @Test
    void unaAsignacionFallidaRevierteTodosLosProductosYSePuedeSuperarConCancelacion() {
        var a = service.create(new ProductInput("RESERVA-A", "Filtro A", BigDecimal.TEN, 5, true));
        var b = service.create(new ProductInput("RESERVA-B", "Filtro B", BigDecimal.TEN, 1, true));
        var fallo = new SolicitudReserva(102L, 1, List.of(new StockItem(a.id(), 2), new StockItem(b.id(), 2)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.sincronizarReserva(fallo));
        assertEquals(5, service.get(a.id()).stock());
        assertEquals(1, service.get(b.id()).stock());
        assertEquals(0, service.reserva(102L).revision());
        service.sincronizarReserva(new SolicitudReserva(102L, 2, List.of()));
        service.sincronizarReserva(fallo);
        assertEquals(5, service.get(a.id()).stock());
    }

    @Test
    void agrupaDuplicadosYNoPermiteQueDosOrdenesUsenElMismoStock() {
        var p = service.create(new ProductInput("RESERVA-DUP", "Bujía", BigDecimal.TEN, 3, true));
        service.sincronizarReserva(new SolicitudReserva(103L, 1,
                List.of(new StockItem(p.id(), 1), new StockItem(p.id(), 2))));
        assertEquals(0, service.get(p.id()).stock());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.sincronizarReserva(new SolicitudReserva(104L, 1, List.of(new StockItem(p.id(), 1)))));
        // Mantener una cantidad ya asignada es válido incluso si se desactiva el producto.
        service.update(p.id(), new ProductInput(p.sku(), p.name(), p.price(), 0, false));
        service.sincronizarReserva(new SolicitudReserva(103L, 2, List.of(new StockItem(p.id(), 3))));
        service.sincronizarReserva(new SolicitudReserva(103L, 3, List.of()));
        assertEquals(3, service.get(p.id()).stock());
    }

    @Test
    void dosAceptacionesConcurrentesNoDejanStockNegativo() throws Exception {
        var p = service.create(new ProductInput("RESERVA-CONCURRENT", "Repuesto", BigDecimal.TEN, 1, true));
        var inicio = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tareas = java.util.stream.LongStream.of(201L, 202L).mapToObj(id -> pool.submit(() -> {
                inicio.await();
                try {
                    service.sincronizarReserva(new SolicitudReserva(id, 1, List.of(new StockItem(p.id(), 1))));
                    return true;
                } catch (org.springframework.web.server.ResponseStatusException ex) {
                    assertEquals(org.springframework.http.HttpStatus.CONFLICT, ex.getStatusCode());
                    return false;
                }
            })).toList();
            inicio.countDown();
            int exitos = 0;
            for (var tarea : tareas) if (tarea.get(10, java.util.concurrent.TimeUnit.SECONDS)) exitos++;
            assertEquals(1, exitos);
            assertEquals(0, service.get(p.id()).stock());
        } finally { pool.shutdownNow(); }
    }
}
