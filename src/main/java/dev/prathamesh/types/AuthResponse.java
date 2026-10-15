package dev.prathamesh.types;

public record AuthResponse(
    String token,
    String type,
    Long userId,
    String name,
    String email,
    UserRole role
) {
    public static AuthResponse of(String token, Long userId, String name, String email, UserRole role) {
        return new AuthResponse(token, "Bearer", userId, name, email, role);
    }
}
