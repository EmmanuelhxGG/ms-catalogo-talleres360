package com.talleres360.catalog.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "categorias") @Getter @Setter @NoArgsConstructor
public class Categoria {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
  @Column(name = "nombre", nullable = false, unique = true, length = 80) private String nombre;
  @Column(name = "descripcion", length = 300) private String descripcion;
  @Column(name = "activa", nullable = false) private boolean activa = true;
}
