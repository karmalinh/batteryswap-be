package BatterySwapStation.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Battery")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Battery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BatteryId")
    private Integer batteryId;

    @Column(name = "SerialNumber", nullable = false, unique = true, length = 100)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "BatteryStatus", nullable = false, length = 50)
    private BatteryStatus batteryStatus = BatteryStatus.AVAILABLE;

    @Column(name = "IsActive", nullable = false)
    private boolean isActive = true;

    // 1-1: Pin có thể nằm trong 1 slot
    @OneToOne(mappedBy = "battery", fetch = FetchType.LAZY)
    @JsonBackReference
    private DockSlot dockSlot;

    public enum BatteryStatus {
        AVAILABLE,
        IN_USE,
        CHARGING,
        DAMAGED
    }
}
