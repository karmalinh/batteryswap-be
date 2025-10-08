package BatterySwapStation.dto;

import lombok.Data;

@Data
public class VehicleInfoResponse {
    private int vehicleId;
    private String vin;
    private String ownerName;
    private String vehicleType;
    private String batteryType;
    private int batteryCount;
    private java.time.LocalDate purchaseDate;
    private int manufactureYear;
    private String color;
    private boolean active;
    private String licensePlate;
    private String userId; // Đổi từ Integer thành String

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}