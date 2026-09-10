package edu.cit.pescante.shop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<OrderRecord, Long> {
    List<OrderRecord> findAllByOrderByCreatedAtDesc();
}
