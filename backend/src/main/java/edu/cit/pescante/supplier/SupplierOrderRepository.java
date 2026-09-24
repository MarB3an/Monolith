package edu.cit.pescante.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Package-private Spring Data repository for {@link SupplierOrderRecord}.
 * Must not be visible outside the supplier package.
 */
interface SupplierOrderRepository extends JpaRepository<SupplierOrderRecord, Long> {

    List<SupplierOrderRecord> findByStatus(SupplierOrderStatus status);

    List<SupplierOrderRecord> findByStatusIn(List<SupplierOrderStatus> statuses);
}
