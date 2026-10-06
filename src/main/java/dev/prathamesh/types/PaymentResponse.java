package dev.prathamesh.types;

import java.math.BigDecimal;
import java.time.Instant;

import dev.prathamesh.model.PaymentModel;

public record PaymentResponse(
        Long paymentId,
        Long senderAccount,
        Long receiverAccount,
        BigDecimal amount,
        PaymentStatus status,
        String idempotencyKey,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(PaymentModel p) {
        return new PaymentResponse(
                p.getId(),
                p.getSourceAccountId(),
                p.getDestinationAccountId(),
                p.getAmount(),
                p.getStatus(),
                p.getIdempotencyKey(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}