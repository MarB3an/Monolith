package edu.cit.pescante.supplier;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Package-private JPA entity tracking a single LegacySupply purchase order.
 * Never imported by Order or Inventory modules.
 */
@Entity
@Table(name = "supplier_orders")
class SupplierOrderRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, length = 50)
    private String productId;

    /** "RO-{id}" — set after first save when ID is known. */
    @Column(name = "buyer_ref", unique = true, length = 50)
    private String buyerRef;

    /** "REQ-RO-{id}" — idempotency key for LegacySupply X-Request-Id. */
    @Column(name = "request_id", unique = true, length = 100)
    private String requestId;

    /** LegacySupply's PoNumber assigned on acknowledgement. */
    @Column(name = "po_number", length = 50)
    private String poNumber;

    /** Number of cases ordered. */
    @Column(name = "cases", nullable = false)
    private Integer cases;

    /** Total individual units (cases * packSize). */
    @Column(name = "units", nullable = false)
    private Integer units;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SupplierOrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    SupplierOrderRecord() {}

    @PrePersist
    void onPersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    Long getId() { return id; }
    String getProductId() { return productId; }
    void setProductId(String productId) { this.productId = productId; }
    String getBuyerRef() { return buyerRef; }
    void setBuyerRef(String buyerRef) { this.buyerRef = buyerRef; }
    String getRequestId() { return requestId; }
    void setRequestId(String requestId) { this.requestId = requestId; }
    String getPoNumber() { return poNumber; }
    void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    Integer getCases() { return cases; }
    void setCases(Integer cases) { this.cases = cases; }
    Integer getUnits() { return units; }
    void setUnits(Integer units) { this.units = units; }
    SupplierOrderStatus getStatus() { return status; }
    void setStatus(SupplierOrderStatus status) { this.status = status; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
