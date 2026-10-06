package dev.prathamesh.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.prathamesh.model.PaymentModel;
import dev.prathamesh.service.PaymentService;
import dev.prathamesh.types.PaymentRequest;

@RestController()
@RequestMapping("/api/v1/payment")
public class PaymentController{
	
	PaymentService paymentService ;
	
	public PaymentController(PaymentService paymentService) {
		this.paymentService=paymentService;
	}
	
	@PostMapping()
	public PaymentModel create(@RequestBody PaymentRequest paymentRequest) throws InterruptedException {
		return paymentService.transfer(paymentRequest);
	}
}