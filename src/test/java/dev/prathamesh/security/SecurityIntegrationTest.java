package dev.prathamesh.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.prathamesh.controller.AuthController;
import dev.prathamesh.controller.LedgerController;
import dev.prathamesh.model.UserModel;
import dev.prathamesh.repository.UserRepository;
import dev.prathamesh.service.AuthService;
import dev.prathamesh.service.LedgerService;
import dev.prathamesh.types.AuthResponse;
import dev.prathamesh.types.LedgerVerifyResponse;
import dev.prathamesh.types.RegisterRequest;
import dev.prathamesh.types.UserRole;

@WebMvcTest(controllers = {AuthController.class, LedgerController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, JwtAuthenticationEntryPoint.class, CustomUserDetailsService.class})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private LedgerService ledgerService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void shouldDenyAccessToProtectedEndpointsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/verify"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAccessToAuthEndpointsWithoutToken() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(AuthResponse.of("mock.token", 1L, "Alice", "alice@test.io", UserRole.USER));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "name": "Alice",
                            "email": "alice@test.io",
                            "password": "password123"
                        }
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldAllowAccessToProtectedEndpointWithValidJwt() throws Exception {
        UserModel user = new UserModel("Alice", "alice@test.io", "encoded", UserRole.USER);
        when(userRepository.findByEmail("alice@test.io")).thenReturn(Optional.of(user));

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateToken(principal);

        when(ledgerService.verify()).thenReturn(new LedgerVerifyResponse(BigDecimal.ZERO, true, List.of()));

        mockMvc.perform(get("/api/v1/ledger/verify")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectAccessWithInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/verify")
                        .header("Authorization", "Bearer invalid.garbage.token"))
                .andExpect(status().isUnauthorized());
    }
}
