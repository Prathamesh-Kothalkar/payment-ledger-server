package dev.prathamesh.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

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

    /**
     * Creates a payment attempt and processes the transfer.
     *
     * Flow:
     *
     * 1. Validate idempotency key
     * 2. Check whether this request was already processed
     * 3. Create PENDING payment
     * 4. Commit PENDING payment
     * 5. Execute money transfer in a separate transaction
     * 6. SUCCESS -> commit balance changes
     * 7. Business failure -> rollback balance changes
     * 8. Mark payment as FAILED in a new transaction
     */
    public PaymentModel transfer(PaymentRequest paymentRequest) {

        validateRequest(paymentRequest);

       
        PaymentModel existingPayment = paymentRepository.findByIdempotencyKey(paymentRequest.idempotencyKey());

        if (existingPayment != null) {
            return existingPayment;
        }

        
        PaymentModel payment = transactionTemplate.execute(status -> {
            PaymentModel existing =
                    paymentRepository.findByIdempotencyKey(
                            paymentRequest.idempotencyKey()
                    );

            if (existing != null) {
                return existing;
            }

            PaymentModel newPayment = new PaymentModel(
                    paymentRequest.senderAccount(),
                    paymentRequest.receiverAccount(),
                    paymentRequest.amount(),
                    paymentRequest.idempotencyKey()
            );

            newPayment.setStatus(PaymentStatus.PENDING);

            return paymentRepository.save(newPayment);
        });

        
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return payment;
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {

                AccountModel sender =
                        accountRepository.findByIdForUpdate(
                                paymentRequest.senderAccount()
                        ).orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Sender account not found"
                                )
                        );

                AccountModel receiver =
                        accountRepository.findByIdForUpdate(
                                paymentRequest.receiverAccount()
                        ).orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Receiver account not found"
                                )
                        );

                BigDecimal amount = paymentRequest.amount();

                if (amount == null ||
                        amount.compareTo(BigDecimal.ZERO) <= 0) {

                    throw new IllegalArgumentException(
                            "Amount must be greater than zero"
                    );
                }

                
                if (sender.getId().equals(receiver.getId())) {

                    throw new IllegalArgumentException(
                            "Sender and receiver cannot be the same account"
                    );
                }

                
                if (sender.getBalance().compareTo(amount) < 0) {

                    throw new IllegalArgumentException(
                            "Insufficient balance"
                    );
                }

                
                sender.setBalance(
                        sender.getBalance().subtract(amount)
                );

                receiver.setBalance(
                        receiver.getBalance().add(amount)
                );
               
                accountRepository.save(sender);
                accountRepository.save(receiver);
            });

            
            transactionTemplate.executeWithoutResult(status -> {

                PaymentModel currentPayment =
                        paymentRepository.findById(payment.getId())
                                .orElseThrow(() ->
                                        new IllegalStateException(
                                                "Payment not found"
                                        )
                                );

                currentPayment.setStatus(PaymentStatus.SUCCESS);

                paymentRepository.save(currentPayment);
            });

            return paymentRepository.findById(payment.getId())
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Payment not found"
                            )
                    );

        } catch (IllegalArgumentException e) {

            /*
             * Business failure:
             *
             * Example:
             * - insufficient balance
             * - same account
             * - invalid amount
             * - account doesn't exist
             *
             * The transfer transaction has already rolled back.
             *
             * Now persist FAILED in a separate transaction.
             */
            transactionTemplate.executeWithoutResult(status -> {

                PaymentModel currentPayment =
                        paymentRepository.findById(payment.getId())
                                .orElseThrow(() ->
                                        new IllegalStateException(
                                                "Payment not found"
                                        )
                                );

                currentPayment.setStatus(PaymentStatus.FAILED);

                paymentRepository.save(currentPayment);
            });

            throw e;
        }
    }

    
    private void validateRequest(PaymentRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Payment request cannot be null"
            );
        }

        if (request.idempotencyKey() == null ||
                request.idempotencyKey().isBlank()) {

            throw new IllegalArgumentException(
                    "Idempotency key is required"
            );
        }

        if (request.senderAccount() == null) {
            throw new IllegalArgumentException(
                    "Sender account is required"
            );
        }

        if (request.receiverAccount() == null) {
            throw new IllegalArgumentException(
                    "Receiver account is required"
            );
        }

        if (request.amount() == null ||
                request.amount().compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalArgumentException(
                    "Amount must be greater than zero"
            );
        }
    }
}