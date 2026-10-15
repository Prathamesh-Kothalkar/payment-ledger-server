package dev.prathamesh.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.prathamesh.model.UserModel;
import dev.prathamesh.repository.UserRepository;
import dev.prathamesh.security.JwtService;
import dev.prathamesh.security.UserPrincipal;
import dev.prathamesh.types.AuthResponse;
import dev.prathamesh.types.LoginRequest;
import dev.prathamesh.types.RegisterRequest;
import dev.prathamesh.types.UserRole;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            UserService userService,
            JwtService jwtService,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email is already registered: " + request.email());
        }

        UserRole role = request.role() != null ? request.role() : UserRole.USER;
        UserModel user = new UserModel(request.name(), request.email(), request.password(), role);

        UserModel created = userService.createUser(user);

        String token = jwtService.generateToken(new UserPrincipal(created));
        return AuthResponse.of(token, created.getId(), created.getName(), created.getEmail(), created.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        if (request.email() == null || request.password() == null) {
            throw new IllegalArgumentException("Email and password must be provided");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        UserModel user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + request.email()));

        String token = jwtService.generateToken(new UserPrincipal(user));
        return AuthResponse.of(token, user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
