package com.talleres360.catalog.service;

import com.talleres360.catalog.model.Categoria;
import com.talleres360.catalog.repository.CategoriaRepository;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class CategoriaService {
  private final CategoriaRepository repository;
  public record Entrada(@NotBlank @Size(max=80) String nombre, @Size(max=300) String descripcion, boolean activa) {}
  @Transactional(readOnly=true) public List<Categoria> listar() { return repository.findAllByOrderByNombreAsc(); }
  @Transactional public Categoria guardar(Long id, Entrada e) {
    var c = id == null ? new Categoria() : buscar(id);
    repository.findByNombreIgnoreCase(e.nombre().trim()).ifPresent(otra -> {
      if (!otra.getId().equals(c.getId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "La categoría ya existe");
    });
    c.setNombre(e.nombre().trim()); c.setDescripcion(e.descripcion()); c.setActiva(e.activa());
    return repository.save(c);
  }
  @Transactional public void eliminar(Long id) { buscar(id).setActiva(false); }
  private Categoria buscar(Long id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
}
