package dev.prathamesh.types;

public record RegisterRequest(
    String name,
    String email,
    String password,
    UserRole role
) {}
