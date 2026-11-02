package com.asdp.weighbridge.controller;

import com.asdp.weighbridge.dto.WeighValidationRequest;
import com.asdp.weighbridge.dto.WeighValidationResponse;
import com.asdp.weighbridge.dto.WeighValidationResponse.StatusState;
import com.asdp.weighbridge.model.VehicleWeighing;
import com.asdp.weighbridge.repository.VehicleWeighingRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/weighbridge")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class WeighbridgeValidationController {

    private final VehicleWeighingRepository vehicleWeighingRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Endpoint utama validasi dan perekaman berat di REPO BARU Pos Jembatan Timbang.
     * Mengimplementasikan 4 Status Layar sesuai Technical Req Jembatan Timbang.pdf (Page 7 - 9).
     */
    @PostMapping("/validate")
    @Transactional
    public ResponseEntity<WeighValidationResponse> validateAndRecordWeight(@Valid @RequestBody WeighValidationRequest request) {
        String input = request.getBarcodeInput().trim().toUpperCase();
        String searchType = request.getSearchType();
        log.info("Menerima request validasi barcode jembatan timbang: {} (searchType: {})", input, searchType);

        // 1. Query data order & order_items berdasarkan pilihan dropdown (Kode Booking / No Tiket / Order ID)
        String baseQuery = """
            SELECT o.id AS order_id, o.booking_code, o.schedule_departure, o.status AS order_status,
                   oi.id AS order_item_id, oi.ticket_number, oi.status AS ticket_status, 
                   oi.check_in_time, oi.service_name, v.id AS vehicle_id, v.license_plate, v.berat AS vehicle_berat
            FROM orders o
            JOIN order_items oi ON oi.order_id = o.id
            LEFT JOIN vehicles v ON (v.order_id = o.id OR v.id = oi.vehicle_id)
        """;

        String query;
        Object[] params;
        String invalidMsg = "KODE BOOKING TIDAK VALID";

        if (searchType != null && (searchType.equalsIgnoreCase("No Tiket") || searchType.equalsIgnoreCase("TIKET") || searchType.equalsIgnoreCase("TICKET"))) {
            query = baseQuery + " WHERE oi.ticket_number = ? LIMIT 1";
            params = new Object[]{input};
            invalidMsg = "NOMOR TIKET TIDAK VALID";
        } else if (searchType != null && (searchType.equalsIgnoreCase("Order ID") || searchType.equalsIgnoreCase("ORDER"))) {
            query = baseQuery + " WHERE o.id = ? LIMIT 1";
            params = new Object[]{input};
            invalidMsg = "ORDER ID TIDAK VALID";
        } else {
            // Default "Kode Booking"
            query = baseQuery + " WHERE o.booking_code = ? LIMIT 1";
            params = new Object[]{input};
            invalidMsg = "KODE BOOKING TIDAK VALID";
        }

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(query, params);
            log.info("Hasil query database untuk {} (tipe: {}): {} baris ditemukan", input, searchType, rows.size());
        } catch (Exception e) {
            log.error("Database connection fallback/mock error: {}", e.getMessage(), e);
            rows = List.of();
        }

        // KASUS 1: Kode Booking / No Tiket / Order ID Tidak Ditemukan / Tidak Valid (Layar MERAH - Page 8)
        if (rows.isEmpty()) {
            return ResponseEntity.ok(WeighValidationResponse.builder()
                    .status(StatusState.INVALID_BOOKING)
                    .message(invalidMsg)
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        Map<String, Object> data = rows.get(0);
        String orderId = (String) data.get("order_id");
        String vehicleId = (String) data.get("vehicle_id");
        String bookingCode = (String) data.get("booking_code");
        String ticketNumber = (String) data.get("ticket_number");
        String licensePlate = (String) data.get("license_plate");
        String serviceName = (String) data.get("service_name");
        Number statusNum = (Number) data.get("ticket_status");
        int ticketStatus = statusNum != null ? statusNum.intValue() : 0;
        Number orderStatusNum = (Number) data.get("order_status");
        int orderStatus = orderStatusNum != null ? orderStatusNum.intValue() : 0;
        Object checkInTime = data.get("check_in_time");
        Double existingBerat = null;
        if (data.get("vehicle_berat") instanceof Number num) {
            existingBerat = num.doubleValue();
        }

        // KASUS 2: Tiket Sudah Check In / Boarded / Sudah Pernah Ditimbang (Layar MERAH - Page 8)
        boolean isAlreadyCheckedIn = (ticketStatus == 1 || ticketStatus == 4 || checkInTime != null);
        boolean isAlreadyWeighed = (existingBerat != null && existingBerat > 0);
        if (!isAlreadyWeighed) {
            try {
                Integer weighCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicle_weighing WHERE (order_id = ? OR booking_code = ?) AND weigh_status = 'SUCCESS'",
                    Integer.class,
                    orderId, bookingCode
                );
                if (weighCount != null && weighCount > 0) {
                    isAlreadyWeighed = true;
                }
            } catch (Exception ignored) {}
        }

        if (isAlreadyCheckedIn || isAlreadyWeighed) {
            return ResponseEntity.ok(WeighValidationResponse.builder()
                    .status(StatusState.ALREADY_CHECKED_IN)
                    .bookingCode(bookingCode)
                    .ticketNumber(ticketNumber)
                    .licensePlate(licensePlate)
                    .message("TIKET SUDAH CHECK IN")
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        // KASUS 3: Tiket Expired (Layar MERAH - Page 9)
        if (orderStatus == 5) {
            return ResponseEntity.ok(WeighValidationResponse.builder()
                    .status(StatusState.EXPIRED)
                    .bookingCode(bookingCode)
                    .ticketNumber(ticketNumber)
                    .licensePlate(licensePlate)
                    .message("TIKET EXPIRED")
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        Object scheduleDepObj = data.get("schedule_departure");
        if (scheduleDepObj != null) {
            try {
                LocalDateTime depTime = null;
                if (scheduleDepObj instanceof java.sql.Timestamp ts) {
                    depTime = ts.toLocalDateTime();
                } else if (scheduleDepObj instanceof LocalDateTime ldt) {
                    depTime = ldt;
                } else {
                    depTime = LocalDateTime.parse(scheduleDepObj.toString().replace(" ", "T"));
                }
                if (depTime.plusHours(2).isBefore(LocalDateTime.now())) {
                    return ResponseEntity.ok(WeighValidationResponse.builder()
                            .status(StatusState.EXPIRED)
                            .bookingCode(bookingCode)
                            .ticketNumber(ticketNumber)
                            .licensePlate(licensePlate)
                            .message("TIKET EXPIRED")
                            .timestamp(LocalDateTime.now())
                            .build());
                }
            } catch (Exception ignored) {}
        }

        // KASUS 4: Tiket Sah & Berhasil Input Berat (Layar HIJAU - Page 7)
        // A. Insert ke tabel vehicle_weighing (Audit Log Append-Only)
        VehicleWeighing logEntity = VehicleWeighing.builder()
                .orderId(orderId)
                .vehicleId(vehicleId)
                .bookingCode(bookingCode)
                .ticketNumber(ticketNumber)
                .licensePlate(licensePlate)
                .actualWeight(request.getActualWeight())
                .isStable(request.getIsStable() != null ? request.getIsStable() : true)
                .weighStatus("SUCCESS")
                .operatorUsername(request.getOperatorUsername() != null ? request.getOperatorUsername() : "operator_timbang")
                .scaleDeviceId(request.getDeviceId())
                .createdAt(LocalDateTime.now())
                .build();
        vehicleWeighingRepository.save(logEntity);

        // B. Update kolom berat pada tabel vehicles
        if (vehicleId != null) {
            try {
                jdbcTemplate.update("UPDATE vehicles SET berat = ? WHERE id = ?", request.getActualWeight(), vehicleId);
            } catch (Exception e) {
                log.warn("Tidak dapat update vehicles.berat: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(WeighValidationResponse.builder()
                .status(StatusState.SUCCESS)
                .message("BERHASIL INPUT BERAT KENDARAAN")
                .bookingCode(bookingCode)
                .ticketNumber(ticketNumber)
                .licensePlate(licensePlate)
                .vehicleClass(serviceName)
                .recordedWeight(request.getActualWeight())
                .isStable(request.getIsStable())
                .timestamp(LocalDateTime.now())
                .build());
    }
}
