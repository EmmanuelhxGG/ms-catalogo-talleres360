package com.talleres360.catalog.repository;

import com.talleres360.catalog.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByOrderByNameAsc();
    boolean existsBySkuIgnoreCase(String sku);
    Optional<Product> findBySkuIgnoreCase(String sku);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<Product> findWithLockById(Long id);
}
