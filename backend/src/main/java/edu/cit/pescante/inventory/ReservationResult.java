package edu.cit.pescante.inventory;

public class ReservationResult {
    private final boolean success;
    private final String reason;
    private final InventoryItem item;

    public ReservationResult(boolean success, String reason, InventoryItem item) {
        this.success = success;
        this.reason = reason;
        this.item = item;
    }

    public static ReservationResult success(String reason, InventoryItem item) {
        return new ReservationResult(true, reason, item);
    }

    public static ReservationResult rejected(String reason, InventoryItem item) {
        return new ReservationResult(false, reason, item);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReason() {
        return reason;
    }

    public InventoryItem getItem() {
        return item;
    }
}
