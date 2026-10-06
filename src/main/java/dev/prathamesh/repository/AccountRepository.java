package dev.prathamesh.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.prathamesh.model.AccountModel;

import jakarta.persistence.LockModeType;

public interface AccountRepository
        extends JpaRepository<AccountModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a
        FROM AccountModel a
        WHERE a.id = :id
    """)
    Optional<AccountModel> findByIdForUpdate(
            @Param("id") Long id
    );
}