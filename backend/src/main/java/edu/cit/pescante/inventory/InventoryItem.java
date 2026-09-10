package edu.cit.pescante.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class InventoryItem {

    @Id
    @Column(name = "product_id", nullable = false, length = 50)
    private String productId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    public InventoryItem() {
    }

    public InventoryItem(String productId, String name, Integer stock) {
        this.productId = productId;
        this.name = name;
        this.stock = stock;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    @Override
    public String toString() {
        return "InventoryItem{" +
                "productId='" + productId + '\'' +
                ", name='" + name + '\'' +
                ", stock=" + stock +
                '}';
    }
}
