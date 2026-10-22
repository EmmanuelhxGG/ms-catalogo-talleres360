package com.talleres360.catalog.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "categorias") @Getter @Setter @NoArgsConstructor
public class Categoria {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(nullable = false, unique = true, length = 80) private String nombre;
  @Column(length = 300) private String descripcion;
  @Column(nullable = false) private boolean activa = true;
}
