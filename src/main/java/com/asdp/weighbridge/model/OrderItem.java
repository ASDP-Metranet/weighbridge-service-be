package com.asdp.weighbridge.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Mapping read-only minimal untuk tabel order_items (milik sistem tiketing).
 * Hanya kolom yang dibutuhkan untuk query yang dipetakan; service ini tidak pernah menulis ke tabel ini.
 */
@Entity
@Immutable
@Table(name = "order_items")
@Getter
@NoArgsConstructor
public class OrderItem {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "ticket_number")
    private String ticketNumber;
}
