package dev.prathamesh.service;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import dev.prathamesh.exception.ResourceNotFoundException;
import dev.prathamesh.model.AccountModel;
import dev.prathamesh.model.PaymentModel;
import dev.prathamesh.model.UserModel;
import dev.prathamesh.repository.AccountRepository;
import dev.prathamesh.repository.PaymentRepository;
import dev.prathamesh.repository.UserRepository;
import dev.prathamesh.types.AccountType;
import dev.prathamesh.types.PaymentStatus;
import jakarta.transaction.Transactional;

@Service
public class UserService {

    private static final BigDecimal SIGNUP_BONUS = new BigDecimal("2000");

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                AccountRepository accountRepository,
                PaymentRepository paymentRepository,
                LedgerService ledgerService,
                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
        this.ledgerService = ledgerService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserModel createUser(UserModel user) {
        if (user.getEmail() != null && userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email is already registered: " + user.getEmail());
        }

        if (user.getPassword() != null && !user.getPassword().isBlank()
                && !user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        UserModel u = userRepository.save(user);
        AccountModel account = accountRepository.save(new AccountModel(u.getId(), AccountType.USER));

        PaymentModel bonus = new PaymentModel(
                ledgerService.systemAccountId(), account.getId(),
                SIGNUP_BONUS, "signup-" + u.getId(), "signup");
        bonus.setStatus(PaymentStatus.SUCCESS);
        bonus = paymentRepository.save(bonus);

        ledgerService.post(bonus.getId(), ledgerService.systemAccountId(), account.getId(), SIGNUP_BONUS);
        return u;
    }

    public UserModel getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    public Optional<UserModel> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}