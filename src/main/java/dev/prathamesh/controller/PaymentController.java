package dev.prathamesh.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.prathamesh.model.PaymentModel;
import dev.prathamesh.service.PaymentService;
import dev.prathamesh.types.PaymentRequest;
import dev.prathamesh.types.PaymentResponse;
import dev.prathamesh.types.PaymentStatus;

@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(@RequestBody PaymentRequest paymentRequest) {

        PaymentModel payment = paymentService.transfer(paymentRequest);
        PaymentResponse body = PaymentResponse.from(payment);

        // Another request with the same key is still processing it:
        // 202 tells the client "accepted, not finished, retry with the same key"
        if (payment.getStatus() == PaymentStatus.PROCESSING
                || payment.getStatus() == PaymentStatus.PENDING) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
        }

        return ResponseEntity.ok(body);
    }
}