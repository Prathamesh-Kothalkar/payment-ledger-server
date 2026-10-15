package dev.prathamesh.security;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.prathamesh.model.UserModel;
import dev.prathamesh.types.UserRole;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();
        // Set secret key and expiration via reflection for testing
        Field secretField = JwtService.class.getDeclaredField("secretKey");
        secretField.setAccessible(true);
        secretField.set(jwtService, "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");

        Field expField = JwtService.class.getDeclaredField("jwtExpiration");
        expField.setAccessible(true);
        expField.set(jwtService, 3600000L); // 1 hour
    }

    @Test
    void shouldGenerateAndValidateToken() {
        UserModel user = new UserModel("John Doe", "john@example.com", "password123", UserRole.USER);
        Field idField;
        try {
            idField = UserModel.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, 42L);
        } catch (Exception e) {
            fail(e);
        }

        UserPrincipal principal = new UserPrincipal(user);
        String token = jwtService.generateToken(principal);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals("john@example.com", jwtService.extractUsername(token));
        assertTrue(jwtService.isTokenValid(token, principal));

        var claims = jwtService.extractAllClaims(token);
        assertEquals(42, ((Number) claims.get("userId")).intValue());
        assertEquals("USER", claims.get("role"));
        assertEquals("John Doe", claims.get("name"));
    }

    @Test
    void shouldDetectInvalidUser() {
        UserModel user1 = new UserModel("User1", "user1@example.com", "pass", UserRole.USER);
        UserModel user2 = new UserModel("User2", "user2@example.com", "pass", UserRole.USER);

        UserPrincipal principal1 = new UserPrincipal(user1);
        UserPrincipal principal2 = new UserPrincipal(user2);

        String token = jwtService.generateToken(principal1);

        assertFalse(jwtService.isTokenValid(token, principal2));
    }
}
