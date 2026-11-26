package com.asdp.weighbridge.controller;

import com.asdp.weighbridge.dto.WeighValidationRequest;
import com.asdp.weighbridge.dto.WeighValidationResponse;
import com.asdp.weighbridge.dto.WeighValidationResponse.StatusState;
import com.asdp.weighbridge.model.VehicleWeighing;
import com.asdp.weighbridge.repository.VehicleWeighingRepository;
import com.asdp.weighbridge.repository.VehicleWeighingSpecs;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
     * Endpoint utama validasi dan perekaman berat di Pos Jembatan Timbang.
     * Mengimplementasikan 4 Status Layar sesuai Technical Req Jembatan Timbang.pdf (Page 7 - 9).
     */
    @PostMapping("/validate")
    @Transactional
    public ResponseEntity<WeighValidationResponse> validateAndRecordWeight(@Valid @RequestBody WeighValidationRequest request) {
        String input = request.getBarcodeInput().trim().toUpperCase();
        String searchType = request.getSearchType();
        log.info("Menerima request validasi barcode jembatan timbang: {} (searchType: {})", input, searchType);

        // 1. Query order berdasarkan pilihan dropdown (Kode Booking / No Tiket / Order ID)
        String orderId = null;
        Map<String, Object> orderData = null;
        String invalidMsg = "KODE BOOKING TIDAK VALID";

        try {
            if (searchType != null && (searchType.equalsIgnoreCase("No Tiket") || searchType.equalsIgnoreCase("TIKET") || searchType.equalsIgnoreCase("TICKET"))) {
                invalidMsg = "NOMOR TIKET TIDAK VALID";
                List<Map<String, Object>> itemRows = jdbcTemplate.queryForList(
                    "SELECT oi.order_id FROM order_items oi WHERE oi.ticket_number = ? LIMIT 1", 
                    input
                );
                if (!itemRows.isEmpty()) {
                    orderId = (String) itemRows.get(0).get("order_id");
                    List<Map<String, Object>> orderRows = jdbcTemplate.queryForList(
                        "SELECT o.id AS order_id, o.booking_code, o.schedule_departure, o.status AS order_status FROM orders o WHERE o.id = ? LIMIT 1", 
                        orderId
                    );
                    if (!orderRows.isEmpty()) {
                        orderData = orderRows.get(0);
                    }
                }
            } else if (searchType != null && (searchType.equalsIgnoreCase("Order ID") || searchType.equalsIgnoreCase("ORDER"))) {
                invalidMsg = "ORDER ID TIDAK VALID";
                List<Map<String, Object>> orderRows = jdbcTemplate.queryForList(
                    "SELECT o.id AS order_id, o.booking_code, o.schedule_departure, o.status AS order_status FROM orders o WHERE o.id = ? LIMIT 1", 
                    input
                );
                if (!orderRows.isEmpty()) {
                    orderData = orderRows.get(0);
                    orderId = (String) orderData.get("order_id");
                }
            } else {
                // Default: "Kode Booking"
                invalidMsg = "KODE BOOKING TIDAK VALID";
                List<Map<String, Object>> orderRows = jdbcTemplate.queryForList(
                    "SELECT o.id AS order_id, o.booking_code, o.schedule_departure, o.status AS order_status FROM orders o WHERE o.booking_code = ? LIMIT 1", 
                    input
                );
                if (!orderRows.isEmpty()) {
                    orderData = orderRows.get(0);
                    orderId = (String) orderData.get("order_id");
                }
            }
        } catch (Exception e) {
            log.warn("Database order query error: {}", e.getMessage());
        }

        // KASUS 1: Kode Booking / No Tiket / Order ID Tidak Ditemukan / Tidak Valid (Layar MERAH - Page 8)
        if (orderData == null) {
            return ResponseEntity.ok(WeighValidationResponse.builder()
                    .status(StatusState.INVALID_BOOKING)
                    .message(invalidMsg)
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        // Query details per order_id
        String bookingCode = (String) orderData.get("booking_code");
        String ticketNumber = null;
        String licensePlate = null;
        String serviceName = "-";
        int ticketStatus = 0;
        String vehicleId = null;
        Object checkInTime = null;
        int isGoods = 0;

        try {
            List<Map<String, Object>> detailRows = jdbcTemplate.queryForList("""
                SELECT oi.ticket_number, oi.status AS ticket_status, oi.check_in_time, s.name AS service_name,
                       s.is_goods,
                       COALESCE(v.id, oi.vehicle_id) AS vehicle_id, COALESCE(v.license_plate, '-') AS license_plate, v.berat AS vehicle_berat, oi.service_type
                FROM order_items oi
                LEFT JOIN services s ON s.id = oi.service_id
                LEFT JOIN vehicles v ON (v.order_id = oi.order_id OR (oi.vehicle_id IS NOT NULL AND v.id = oi.vehicle_id))
                WHERE oi.order_id = ?
                ORDER BY CASE WHEN oi.service_type = 'vehicle' THEN 0 ELSE 1 END, oi.id ASC
                LIMIT 1
            """, orderId);

            if (!detailRows.isEmpty()) {
                Map<String, Object> det = detailRows.get(0);
                ticketNumber = (String) det.get("ticket_number");
                licensePlate = (String) det.get("license_plate");
                if (det.get("service_name") != null) serviceName = (String) det.get("service_name");
                Object sObj = det.get("ticket_status");
                if (sObj instanceof Boolean b) {
                    ticketStatus = b ? 1 : 0;
                } else if (sObj instanceof Number num) {
                    ticketStatus = num.intValue();
                }
                vehicleId = (String) det.get("vehicle_id");
                checkInTime = det.get("check_in_time");
                Object gObj = det.get("is_goods");
                if (gObj instanceof Boolean b) {
                    isGoods = b ? 1 : 0;
                } else if (gObj instanceof Number num) {
                    isGoods = num.intValue();
                }
            }
        } catch (Exception e) {
            log.warn("Detail query error: {}", e.getMessage());
        }

        // Penolakan tiket pejalan kaki murni (bukan kendaraan)
        if (vehicleId == null) {
            return ResponseEntity.ok(WeighValidationResponse.builder()
                    .status(StatusState.INVALID_BOOKING)
                    .bookingCode(bookingCode)
                    .ticketNumber(ticketNumber)
                    .message("Bukan Tiket Kendaraan")
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        // Penolakan kendaraan bukan barang (hanya is_goods == 1 yang diizinkan ditimbang)
        if (isGoods != 1) {
            recordAuditLog(orderId, bookingCode, ticketNumber, vehicleId, licensePlate, request, "REJECTED");
            return ResponseEntity.ok(WeighValidationResponse.builder()
                    .status(StatusState.NON_GOODS_VEHICLE)
                    .bookingCode(bookingCode)
                    .ticketNumber(ticketNumber)
                    .licensePlate(licensePlate)
                    .vehicleClass(serviceName)
                    .message("BUKAN KENDARAAN BARANG")
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        // KASUS 2: Tiket Sudah Check In / Boarded (Layar MERAH - Page 8)
        // Concurrency lock: order_items dengan service_type = 'vehicle' dikunci FOR UPDATE
        boolean isAlreadyCheckedIn = isOrderAlreadyCheckedIn(orderId, ticketStatus, checkInTime);

        if (isAlreadyCheckedIn) {
            recordAuditLog(orderId, bookingCode, ticketNumber, vehicleId, licensePlate, request, "REJECTED");
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
        Number orderStatusNum = (Number) orderData.get("order_status");
        int orderStatus = orderStatusNum != null ? orderStatusNum.intValue() : 0;
        if (orderStatus == 5) {
            recordAuditLog(orderId, bookingCode, ticketNumber, vehicleId, licensePlate, request, "REJECTED");
            return ResponseEntity.ok(WeighValidationResponse.builder()
                    .status(StatusState.EXPIRED)
                    .bookingCode(bookingCode)
                    .ticketNumber(ticketNumber)
                    .licensePlate(licensePlate)
                    .message("TIKET EXPIRED")
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        Object scheduleDepObj = orderData.get("schedule_departure");
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

                // Ambil parameter late_check_in dinamis dari tabel settings (default fallback: 2 jam)
                long lateCheckInHours = 2L;
                try {
                    List<Map<String, Object>> settingRows = jdbcTemplate.queryForList(
                        "SELECT set_value FROM settings WHERE set_name = 'late_check_in' LIMIT 1"
                    );
                    if (!settingRows.isEmpty() && settingRows.get(0).get("set_value") != null) {
                        lateCheckInHours = Long.parseLong(settingRows.get(0).get("set_value").toString());
                    }
                } catch (Exception ignored) {}

                // Jika jadwal keberangkatan + toleransi late_check_in telah lewat
                if (depTime.plusHours(lateCheckInHours).isBefore(LocalDateTime.now())) {
                    recordAuditLog(orderId, bookingCode, ticketNumber, vehicleId, licensePlate, request, "REJECTED");
                    return ResponseEntity.ok(WeighValidationResponse.builder()
                            .status(StatusState.EXPIRED)
                            .bookingCode(bookingCode)
                            .ticketNumber(ticketNumber)
                            .licensePlate(licensePlate)
                            .message("TIKET EXPIRED")
                            .timestamp(LocalDateTime.now())
                            .build());
                }
            } catch (Exception e) {
                log.warn("Gagal parse schedule_departure: {}", e.getMessage());
            }
        }

        // KASUS 4: Tiket Sah & Berhasil Input Berat (Layar HIJAU - Page 7)
        boolean notWeighed = request.getActualWeight() == null || request.getActualWeight() <= 0;
        float actualWeightVal = notWeighed ? 0.0f : request.getActualWeight();
        String weighStatus = notWeighed ? "NOT_WEIGHED" : "SUCCESS";

        // A. BR-05: record aktif lama untuk kendaraan ini di-SUPERSEDED sebelum record baru ditulis
        supersedePreviousWeighings(orderId, vehicleId);

        // B. Insert audit log (append-only) ke vehicle_weighing
        recordAuditLog(orderId, bookingCode, ticketNumber, vehicleId, licensePlate, request, weighStatus);

        // C. Update kolom berat pada tabel vehicles dengan anti-misassociation check
        updateVehicleWeight(vehicleId, orderId, actualWeightVal);

        return ResponseEntity.ok(WeighValidationResponse.builder()
                .status(StatusState.SUCCESS)
                .message("BERHASIL INPUT BERAT KENDARAAN")
                .bookingCode(bookingCode)
                .ticketNumber(ticketNumber)
                .licensePlate(licensePlate)
                .vehicleClass(serviceName)
                .recordedWeight(actualWeightVal)
                .isStable(request.getIsStable() != null ? request.getIsStable() : true)
                .timestamp(LocalDateTime.now())
                .build());
    }

    /**
     * True bila salah satu item kendaraan pada order sudah check-in (status 1) / boarded (status 4)
     * atau sudah punya check_in_time. Baris dikunci agar konsisten dengan proses check-in loket.
     */
    private boolean isOrderAlreadyCheckedIn(String orderId, int fallbackStatus, Object fallbackCheckInTime) {
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT oi.status, oi.check_in_time FROM order_items oi " +
            "WHERE oi.order_id = ? AND oi.service_type = 'vehicle' FOR UPDATE",
            orderId
        );
        if (items.isEmpty()) {
            return fallbackStatus == 1 || fallbackStatus == 4 || fallbackCheckInTime != null;
        }
        for (Map<String, Object> item : items) {
            int status = 0;
            Object sObj = item.get("status");
            if (sObj instanceof Boolean b) {
                status = b ? 1 : 0;
            } else if (sObj instanceof Number num) {
                status = num.intValue();
            }
            if (status == 1 || status == 4 || item.get("check_in_time") != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * BR-05 (Re-Weigh Guard): satu kendaraan/tiket hanya punya satu record aktif.
     * Record SUCCESS / NOT_WEIGHED sebelumnya ditandai SUPERSEDED; riwayat REJECTED tidak disentuh.
     */
    private void supersedePreviousWeighings(String orderId, String vehicleId) {
        jdbcTemplate.update(
            "UPDATE vehicle_weighing SET weigh_status = 'SUPERSEDED' " +
            "WHERE order_id = ? AND vehicle_id = ? AND weigh_status IN ('SUCCESS', 'NOT_WEIGHED')",
            orderId, vehicleId);
    }

    /**
     * Anti-misassociation: berat hanya ditimpa pada kendaraan milik order yang sama.
     * Bila tidak tepat 1 baris ter-update, lempar exception sehingga transaksi di-rollback.
     */
    private void updateVehicleWeight(String vehicleId, String orderId, float weight) {
        int updated = jdbcTemplate.update(
            "UPDATE vehicles SET berat = ? WHERE id = ? AND order_id = ?", weight, vehicleId, orderId);
        if (updated != 1) {
            throw new IllegalStateException("Gagal memperbarui vehicles.berat: kendaraan " + vehicleId
                    + " tidak cocok dengan order " + orderId + " (baris ter-update: " + updated + ")");
        }
    }

    private void recordAuditLog(String orderId, String bookingCode, String ticketNumber, String vehicleId,
                                String licensePlate, WeighValidationRequest request, String status) {
        VehicleWeighing logEntity = VehicleWeighing.builder()
                .orderId(orderId)
                .bookingCode(bookingCode)
                .ticketNumber(ticketNumber)
                .vehicleId(vehicleId)
                .licensePlate(licensePlate != null ? licensePlate : "-")
                .actualWeight(request.getActualWeight() != null ? request.getActualWeight() : 0.0f)
                .isStable(request.getIsStable() != null ? request.getIsStable() : true)
                .weighStatus(status)
                .operatorUsername(request.getOperatorUsername() != null && !request.getOperatorUsername().isBlank() 
                        ? request.getOperatorUsername() : null)
                .createdAt(LocalDateTime.now())
                .build();
        vehicleWeighingRepository.save(logEntity);
    }

    /**
     * Cek apakah kode (Kode Booking / No Tiket / Order ID) sudah pernah bertransaksi timbang HARI INI.
     * Dihitung sebagai transaksi: record vehicle_weighing berstatus SUCCESS, NOT_WEIGHED, atau SUPERSEDED
     * (record REJECTED tidak dihitung). No Tiket juga dicocokkan lewat order_items, sehingga nomor tiket
     * lain dalam order yang sama tetap terdeteksi.
     * Response: true jika sudah pernah, false jika belum.
     */
    @GetMapping("/check-today")
    public ResponseEntity<Boolean> checkTransactionToday(@RequestParam("code") String code) {
        String input = code == null ? "" : code.trim().toUpperCase();
        if (input.isEmpty()) {
            return ResponseEntity.ok(false);
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfNextDay = startOfDay.plusDays(1);

        boolean exists = vehicleWeighingRepository.exists(
                VehicleWeighingSpecs.createdBetween(startOfDay, startOfNextDay)
                        .and(VehicleWeighingSpecs.statusIn(List.of("SUCCESS", "NOT_WEIGHED", "SUPERSEDED")))
                        .and(VehicleWeighingSpecs.matchesCode(input)));

        return ResponseEntity.ok(exists);
    }

    /**
     * Endpoint untuk mengambil riwayat penimbangan terbaru dari database.
     */
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentWeighings() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, order_id, booking_code, ticket_number, license_plate, " +
                "actual_weight, weigh_status, is_stable, created_at, operator_username " +
                "FROM vehicle_weighing ORDER BY id DESC LIMIT 10"
            );
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.error("Gagal mengambil riwayat penimbangan", e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Gagal mengambil riwayat penimbangan"));
        }
    }
}
