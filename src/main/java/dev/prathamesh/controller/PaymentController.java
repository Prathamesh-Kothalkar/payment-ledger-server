package dev.prathamesh.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    
    @GetMapping("/history/{id}")
    public ResponseEntity<List<PaymentResponse>> history(@PathVariable Long id){
    	List<PaymentModel> payments=paymentService.getHistory(id);
    	List<PaymentResponse>res=new ArrayList<>();
    	for(PaymentModel payment:payments) {
    		res.add(PaymentResponse.from(payment));
    	}
    	
    	return ResponseEntity.ok(res);
    }
}