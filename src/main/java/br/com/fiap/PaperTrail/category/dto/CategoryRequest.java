package br.com.fiap.PaperTrail.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(@NotBlank @Size(max = 100) String name) {
    public CategoryRequest {
        name = name == null ? null : name.strip();
    }
}
