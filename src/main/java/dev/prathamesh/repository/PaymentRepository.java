package dev.prathamesh.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import dev.prathamesh.model.PaymentModel;

public interface PaymentRepository extends JpaRepository<PaymentModel,Long>{
	PaymentModel findByIdempotencyKey(String idempotencyKey);
}