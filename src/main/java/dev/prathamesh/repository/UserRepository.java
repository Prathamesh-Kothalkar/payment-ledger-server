package dev.prathamesh.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.prathamesh.model.UserModel;

public interface UserRepository extends JpaRepository<UserModel, Long> {
    Optional<UserModel> findByEmail(String email);
    boolean existsByEmail(String email);
}