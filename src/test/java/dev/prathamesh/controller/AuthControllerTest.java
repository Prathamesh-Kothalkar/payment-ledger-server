package dev.prathamesh.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.prathamesh.model.UserModel;
import dev.prathamesh.security.UserPrincipal;
import dev.prathamesh.service.AuthService;
import dev.prathamesh.types.AuthResponse;
import dev.prathamesh.types.LoginRequest;
import dev.prathamesh.types.RegisterRequest;
import dev.prathamesh.types.UserRole;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController authController = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRegisterAndReturnCreated() throws Exception {
        AuthResponse response = AuthResponse.of("jwt.token.here", 1L, "Alice", "alice@test.io", UserRole.USER);
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        String json = """
                {
                    "name": "Alice",
                    "email": "alice@test.io",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@test.io"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void shouldLoginAndReturnOk() throws Exception {
        AuthResponse response = AuthResponse.of("login.token.jwt", 2L, "Bob", "bob@test.io", UserRole.USER);
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        String json = """
                {
                    "email": "bob@test.io",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("login.token.jwt"))
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.name").value("Bob"));
    }

    @Test
    void shouldReturnUnauthorizedWhenMeCalledWithoutPrincipal() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnProfileWhenMeCalledWithPrincipal() throws Exception {
        UserModel user = new UserModel("Alice", "alice@test.io", "pass", UserRole.USER);
        Field idField = UserModel.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, 1L);

        UserPrincipal principal = new UserPrincipal(user);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@test.io"))
                .andExpect(jsonPath("$.role").value("USER"));
    }
}
