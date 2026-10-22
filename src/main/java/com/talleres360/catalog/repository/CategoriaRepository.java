package com.talleres360.catalog.repository;
import com.talleres360.catalog.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
  Optional<Categoria> findByNombreIgnoreCase(String nombre);
  List<Categoria> findAllByOrderByNombreAsc();
}
