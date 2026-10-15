package dev.prathamesh.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import dev.prathamesh.model.UserModel;
import dev.prathamesh.repository.UserRepository;
import dev.prathamesh.security.JwtService;
import dev.prathamesh.security.UserPrincipal;
import dev.prathamesh.types.AuthResponse;
import dev.prathamesh.types.LoginRequest;
import dev.prathamesh.types.RegisterRequest;
import dev.prathamesh.types.UserRole;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, userService, jwtService, authenticationManager);
    }

    @Test
    void shouldRegisterNewUserSuccessfully() throws Exception {
        RegisterRequest req = new RegisterRequest("Alice", "alice@test.io", "password123", UserRole.USER);

        when(userRepository.existsByEmail("alice@test.io")).thenReturn(false);

        UserModel savedUser = new UserModel("Alice", "alice@test.io", "password123", UserRole.USER);
        Field idField = UserModel.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(savedUser, 10L);

        when(userService.createUser(any(UserModel.class))).thenReturn(savedUser);
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("mocked-jwt-token");

        AuthResponse resp = authService.register(req);

        assertNotNull(resp);
        assertEquals("mocked-jwt-token", resp.token());
        assertEquals("Bearer", resp.type());
        assertEquals(10L, resp.userId());
        assertEquals("Alice", resp.name());
        assertEquals("alice@test.io", resp.email());
        assertEquals(UserRole.USER, resp.role());

        verify(userService).createUser(any(UserModel.class));
    }

    @Test
    void shouldThrowExceptionWhenRegisteringWithExistingEmail() {
        RegisterRequest req = new RegisterRequest("Alice", "existing@test.io", "pass", UserRole.USER);
        when(userRepository.existsByEmail("existing@test.io")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> authService.register(req));
        assertTrue(ex.getMessage().contains("already registered"));
    }

    @Test
    void shouldThrowExceptionWhenRegisteringWithMissingFields() {
        assertThrows(IllegalArgumentException.class, () -> authService.register(new RegisterRequest(null, "a@b.com", "pass", null)));
        assertThrows(IllegalArgumentException.class, () -> authService.register(new RegisterRequest("Alice", "", "pass", null)));
        assertThrows(IllegalArgumentException.class, () -> authService.register(new RegisterRequest("Alice", "a@b.com", null, null)));
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        LoginRequest req = new LoginRequest("alice@test.io", "password123");

        UserModel user = new UserModel("Alice", "alice@test.io", "$2a$encoded", UserRole.USER);
        Field idField = UserModel.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, 10L);

        when(userRepository.findByEmail("alice@test.io")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(UserPrincipal.class))).thenReturn("login-jwt-token");

        AuthResponse resp = authService.login(req);

        assertNotNull(resp);
        assertEquals("login-jwt-token", resp.token());
        assertEquals(10L, resp.userId());
        assertEquals("alice@test.io", resp.email());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void shouldPropagateBadCredentialsExceptionOnLogin() {
        LoginRequest req = new LoginRequest("alice@test.io", "wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(req));
    }
}
