package com.talleres360.catalog.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "stock_consumptions")
@Getter @Setter @NoArgsConstructor
public class StockConsumption {
    @Id private String eventId;
    @Column(nullable = false) private Long orderId;
}
