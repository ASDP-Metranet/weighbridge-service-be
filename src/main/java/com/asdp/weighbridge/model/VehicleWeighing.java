package com.asdp.weighbridge.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_weighing")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleWeighing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "vehicle_id", length = 36)
    private String vehicleId;

    @Column(name = "booking_code", nullable = false, length = 50)
    private String bookingCode;

    @Column(name = "ticket_number", length = 50)
    private String ticketNumber;

    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;

    @Column(name = "actual_weight", nullable = false)
    private Float actualWeight;

    @Column(name = "is_stable", nullable = false)
    private Boolean isStable;

    @Column(name = "weigh_status", nullable = false, length = 30)
    private String weighStatus;

    @Column(name = "operator_username", length = 100)
    private String operatorUsername;

    @Column(name = "scale_device_id", length = 50)
    private String scaleDeviceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
