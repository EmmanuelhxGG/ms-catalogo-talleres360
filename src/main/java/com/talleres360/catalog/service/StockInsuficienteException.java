package com.talleres360.catalog.service;

import lombok.Getter;

@Getter
public class StockInsuficienteException extends RuntimeException {
  private final Long productoId;
  private final String nombreProducto;

  public StockInsuficienteException(Long productoId, String nombreProducto) {
    super("No hay disponibilidad suficiente para el producto solicitado");
    this.productoId = productoId;
    this.nombreProducto = nombreProducto;
  }
}
