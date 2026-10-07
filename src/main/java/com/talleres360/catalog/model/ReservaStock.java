package com.talleres360.catalog.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.HashMap;
import java.util.Map;

/** Última asignación aplicada a una orden. Conservar incluso al cancelar evita reintentos antiguos. */
@Entity @Table(name = "reservas_stock")
@Getter @Setter @NoArgsConstructor
public class ReservaStock {
    @Id private Long ordenId;
    private long revision;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reserva_stock_items", joinColumns = @JoinColumn(name = "orden_id"))
    @MapKeyColumn(name = "producto_id") @Column(name = "cantidad", nullable = false)
    private Map<Long, Integer> cantidades = new HashMap<>();
}
