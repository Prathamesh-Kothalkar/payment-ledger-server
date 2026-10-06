package dev.prathamesh.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import dev.prathamesh.exception.IdempotencyConflictException;
import dev.prathamesh.model.AccountModel;
import dev.prathamesh.model.PaymentModel;
import dev.prathamesh.repository.AccountRepository;
import dev.prathamesh.repository.PaymentRepository;
import dev.prathamesh.types.PaymentRequest;
import dev.prathamesh.types.PaymentStatus;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountRepository accountRepository;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(
            PaymentRepository paymentRepository,
            AccountRepository accountRepository,
            TransactionTemplate transactionTemplate) {
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public PaymentModel transfer(PaymentRequest request) {

        validateRequest(request);
        String hash = fingerprint(request);

        // 1. Find the payment for this key, or create it (safe under races)
        PaymentModel payment = findOrCreate(request, hash);

        // 2. Same key must mean the same request (fix 3)
        if (!hash.equals(payment.getRequestHash())) {
            throw new IdempotencyConflictException(
                    "Idempotency key was already used with a different request");
        }

        // 3. Already finished, or another request is working on it
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return payment;
        }

        // 4. Claim ownership: only one request can win PENDING -> PROCESSING (fix 1)
        Integer claimed = transactionTemplate.execute(status ->
                paymentRepository.transition(
                        payment.getId(),
                        PaymentStatus.PENDING,
                        PaymentStatus.PROCESSING,
                        Instant.now()));

        if (claimed == null || claimed == 0) {
            // Lost the claim: someone else is processing it. Return current state.
            return reload(payment.getId());
        }

        // 5. We own it. Move the money and mark SUCCESS in ONE transaction.
        try {
            transactionTemplate.executeWithoutResult(status ->
                    executeTransfer(payment.getId(), request));

            return reload(payment.getId());

        } catch (IllegalArgumentException e) {
            markFailed(payment.getId());
            throw e;
        }
    }

    private PaymentModel findOrCreate(PaymentRequest request, String hash) {

        PaymentModel existing =
                paymentRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing != null) {
            return existing;
        }

        try {
            return transactionTemplate.execute(status ->
                    paymentRepository.saveAndFlush(new PaymentModel(
                            request.senderAccount(),
                            request.receiverAccount(),
                            request.amount(),
                            request.idempotencyKey(),
                            hash)));
        } catch (DataIntegrityViolationException e) {
            // Another request inserted the same key first. Use theirs.
            PaymentModel winner =
                    paymentRepository.findByIdempotencyKey(request.idempotencyKey());
            if (winner == null) {
                throw e;
            }
            return winner;
        }
    }

    private void executeTransfer(Long paymentId, PaymentRequest request) {

        Long senderId = request.senderAccount();
        Long receiverId = request.receiverAccount();
        BigDecimal amount = request.amount();

        // Fix 2: always lock the lower id first, so A->B and B->A can't deadlock
        Long firstId = Math.min(senderId, receiverId);
        Long secondId = Math.max(senderId, receiverId);

        AccountModel first = lockAccount(firstId);
        AccountModel second = lockAccount(secondId);

        AccountModel sender = first.getId().equals(senderId) ? first : second;
        AccountModel receiver = (sender == first) ? second : first;

        if (sender.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accountRepository.save(sender);
        accountRepository.save(receiver);

        PaymentModel payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment not found"));
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
    }

    private AccountModel lockAccount(Long id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account " + id + " not found"));
    }

    private void markFailed(Long paymentId) {
        transactionTemplate.executeWithoutResult(status -> {
            PaymentModel payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new IllegalStateException("Payment not found"));
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        });
    }

    private PaymentModel reload(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment not found"));
    }

    private String fingerprint(PaymentRequest r) {
        String raw = r.senderAccount() + "|" + r.receiverAccount() + "|"
                + r.amount().stripTrailingZeros().toPlainString();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void validateRequest(PaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment request cannot be null");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
    }
}