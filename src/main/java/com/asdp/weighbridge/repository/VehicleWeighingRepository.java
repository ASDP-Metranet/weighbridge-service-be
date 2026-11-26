package com.asdp.weighbridge.repository;

import com.asdp.weighbridge.model.VehicleWeighing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleWeighingRepository extends JpaRepository<VehicleWeighing, Long> {

    List<VehicleWeighing> findByBookingCodeOrderByCreatedAtDesc(String bookingCode);

    Optional<VehicleWeighing> findFirstByOrderIdOrderByCreatedAtDesc(String orderId);

    Optional<VehicleWeighing> findFirstByTicketNumberOrderByCreatedAtDesc(String ticketNumber);

    /**
     * Hitung record penimbangan dalam rentang waktu [start, end) yang cocok dengan kode
     * (Kode Booking / No Tiket / Order ID), termasuk nomor tiket lain dalam order yang sama.
     */
    @Query("""
        SELECT COUNT(vw) FROM VehicleWeighing vw
        WHERE vw.createdAt >= :start AND vw.createdAt < :end
          AND vw.weighStatus IN :statuses
          AND (vw.bookingCode = :code
               OR vw.ticketNumber = :code
               OR vw.orderId = :code
               OR vw.orderId IN (SELECT oi.orderId FROM OrderItem oi WHERE oi.ticketNumber = :code))
        """)
    long countTransactionsByCode(@Param("code") String code,
                                 @Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end,
                                 @Param("statuses") Collection<String> statuses);
}
