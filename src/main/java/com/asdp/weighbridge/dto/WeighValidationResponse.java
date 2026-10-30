package com.asdp.weighbridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeighValidationResponse {

    public enum StatusState {
        SUCCESS,              // Layar HIJAU: Berhasil input berat
        INVALID_BOOKING,      // Layar MERAH: Kode Booking Tidak Valid
        ALREADY_CHECKED_IN,   // Layar KUNING/MERAH: Tiket Sudah Check-In
        EXPIRED               // Layar MERAH: Tiket Expired
    }

    private StatusState status;
    private String message;
    private String bookingCode;
    private String ticketNumber;
    private String licensePlate;
    private String vehicleClass;
    private Float recordedWeight;
    private Boolean isStable;
    private LocalDateTime timestamp;
}
