package com.talleres360.catalog.controller;

import com.talleres360.catalog.service.StockInsuficienteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErroresStock {
  @ExceptionHandler(StockInsuficienteException.class)
  public ProblemDetail sinStock(StockInsuficienteException error) {
    var problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    problema.setProperty("codigo", "RESERVA_SIN_DISPONIBILIDAD");
    problema.setProperty("productoId", error.getProductoId());
    problema.setProperty("nombreProducto", error.getNombreProducto());
    return problema;
  }
}
