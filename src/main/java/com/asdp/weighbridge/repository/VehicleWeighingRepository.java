package com.asdp.weighbridge.repository;

import com.asdp.weighbridge.model.VehicleWeighing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleWeighingRepository extends JpaRepository<VehicleWeighing, Long> {

    List<VehicleWeighing> findByBookingCodeOrderByCreatedAtDesc(String bookingCode);

    Optional<VehicleWeighing> findFirstByOrderIdOrderByCreatedAtDesc(String orderId);

    Optional<VehicleWeighing> findFirstByTicketNumberOrderByCreatedAtDesc(String ticketNumber);
}
