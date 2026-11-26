package com.asdp.weighbridge.repository;

import com.asdp.weighbridge.model.OrderItem;
import com.asdp.weighbridge.model.VehicleWeighing;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Specification (JPA Criteria API) untuk query vehicle_weighing tanpa string query.
 */
public final class VehicleWeighingSpecs {

    private VehicleWeighingSpecs() {}

    /** createdAt dalam rentang [start, end). */
    public static Specification<VehicleWeighing> createdBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> cb.and(
                cb.greaterThanOrEqualTo(root.get("createdAt"), start),
                cb.lessThan(root.get("createdAt"), end));
    }

    /** weighStatus termasuk salah satu status yang diberikan. */
    public static Specification<VehicleWeighing> statusIn(Collection<String> statuses) {
        return (root, query, cb) -> root.get("weighStatus").in(statuses);
    }

    /**
     * Kode cocok dengan bookingCode / ticketNumber / orderId, atau dengan nomor tiket lain
     * dalam order yang sama (lewat order_items).
     */
    public static Specification<VehicleWeighing> matchesCode(String code) {
        return (root, query, cb) -> {
            Subquery<String> orderIdsByTicket = query.subquery(String.class);
            var oi = orderIdsByTicket.from(OrderItem.class);
            orderIdsByTicket.select(oi.get("orderId"))
                    .where(cb.equal(oi.get("ticketNumber"), code));

            return cb.or(
                    cb.equal(root.get("bookingCode"), code),
                    cb.equal(root.get("ticketNumber"), code),
                    cb.equal(root.get("orderId"), code),
                    root.get("orderId").in(orderIdsByTicket));
        };
    }
}
