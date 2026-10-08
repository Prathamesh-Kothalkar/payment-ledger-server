package dev.prathamesh.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import dev.prathamesh.model.PaymentModel;

import dev.prathamesh.types.PaymentStatus;

public interface PaymentRepository extends JpaRepository<PaymentModel, Long> {

    PaymentModel findByIdempotencyKey(String idempotencyKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE PaymentModel p
        SET p.status = :to, p.updatedAt = :now
        WHERE p.id = :id AND p.status = :from
    """) 
    int transition(
            @Param("id") Long id,
            @Param("from") PaymentStatus from,
            @Param("to") PaymentStatus to,
            @Param("now") Instant now);
    
  
}