package br.com.fiap.PaperTrail.category.dto;

import br.com.fiap.PaperTrail.category.entity.Category;

public record CategoryResponse(Long id, String name) {
    public static CategoryResponse fromEntity(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
