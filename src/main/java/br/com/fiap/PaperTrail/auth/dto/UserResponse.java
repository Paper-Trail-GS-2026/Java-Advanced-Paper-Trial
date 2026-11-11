package br.com.fiap.PaperTrail.auth.dto;

import br.com.fiap.PaperTrail.auth.entity.User;

import java.time.OffsetDateTime;

public record UserResponse(Long id, String name, String email, User.Role role, OffsetDateTime createdAt) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRoleName(), user.getCreatedAt());
    }
}
