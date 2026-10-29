package com.talleres360.catalog.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

@Entity @Table(name = "productos", uniqueConstraints = @UniqueConstraint(columnNames = "codigo_interno"))
@Getter @Setter @NoArgsConstructor
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @Column(name = "codigo_interno", nullable = false, length = 60) private String sku;
    @Column(name = "nombre", nullable = false, length = 160) private String name;
    @Column(name = "precio", nullable = false, precision = 12, scale = 2) private BigDecimal price;
    @Column(name = "existencias", nullable = false) private int stock;
    @Column(name = "activo", nullable = false) private boolean active = true;
    @Version @Column(name = "version") private long version;
    @ManyToOne @JoinColumn(name = "categoria_id") private Categoria categoria;
}
