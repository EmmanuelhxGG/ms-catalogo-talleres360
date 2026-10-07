package com.talleres360.catalog.repository;

import com.talleres360.catalog.model.ReservaStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface ReservaStockRepository extends JpaRepository<ReservaStock, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReservaStock r where r.ordenId = :id")
    Optional<ReservaStock> bloquear(Long id);
}
