package dev.prathamesh.types;

public record UserProfileResponse(
    Long id,
    String name,
    String email,
    UserRole role
) {}
