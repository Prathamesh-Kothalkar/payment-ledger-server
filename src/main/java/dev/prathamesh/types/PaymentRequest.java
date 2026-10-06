package dev.prathamesh.types;

import java.math.BigDecimal;

public record PaymentRequest(Long senderAccount, Long receiverAccount, BigDecimal amount,String idempotencyKey){
	 public PaymentRequest {
	        if (senderAccount == null || receiverAccount == null) {
	            throw new IllegalArgumentException("Account IDs cannot be null");
	        }
	        if (senderAccount.equals(receiverAccount)) {
	            throw new IllegalArgumentException("Sender and receiver accounts must be different");
	        }
	        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
	            throw new IllegalArgumentException("Payment amount must be greater than zero");
	        }
	    }
}