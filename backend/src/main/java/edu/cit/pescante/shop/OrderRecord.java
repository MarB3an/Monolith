package edu.cit.pescante.shop;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "product_id", length = 50)
    private String productId;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "status", nullable = false, length = 20)
    private String status; // "CONFIRMED", "REJECTED", "CANCELLED"

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItemRecord> items = new ArrayList<>();

    public OrderRecord() {
    }

    public OrderRecord(String status, String reason) {
        this.status = status;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
        this.productId = "MULTI";
        this.quantity = 0;
    }

    public void addItem(OrderItemRecord item) {
        items.add(item);
        item.setOrder(this);
        if (this.productId == null || "MULTI".equals(this.productId)) {
            this.productId = item.getProductId();
        }
        this.quantity = (this.quantity != null ? this.quantity : 0) + item.getQuantity();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<OrderItemRecord> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRecord> items) {
        this.items = items;
        if (items != null) {
            for (OrderItemRecord item : items) {
                item.setOrder(this);
            }
        }
    }

    public String getProductId() {
        if (productId != null && !productId.isEmpty()) return productId;
        if (items == null || items.isEmpty()) return null;
        return items.get(0).getProductId();
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        if (quantity != null && quantity > 0) return quantity;
        if (items == null || items.isEmpty()) return 0;
        return items.stream().mapToInt(OrderItemRecord::getQuantity).sum();
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return "OrderRecord{" +
                "orderId=" + orderId +
                ", status='" + status + '\'' +
                ", reason='" + reason + '\'' +
                ", createdAt=" + createdAt +
                ", items=" + items +
                '}';
    }
}
