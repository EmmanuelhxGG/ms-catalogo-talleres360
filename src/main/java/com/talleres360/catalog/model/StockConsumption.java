package com.talleres360.catalog.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "consumos_existencias")
@Getter @Setter @NoArgsConstructor
public class StockConsumption {
    @Id @Column(name = "evento_id") private String eventId;
    @Column(name = "solicitud_id", nullable = false) private Long orderId;
}
