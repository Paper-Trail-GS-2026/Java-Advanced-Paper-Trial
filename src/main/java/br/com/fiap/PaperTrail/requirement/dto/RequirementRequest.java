package br.com.fiap.PaperTrail.requirement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RequirementRequest(
        @NotBlank(message = "O título é obrigatório")
        @Size(max = 200, message = "O título deve ter no máximo 200 caracteres")
        String title,

        @NotNull(message = "Informe se o requisito é obrigatório")
        Boolean required,

        @NotBlank(message = "A URL da fonte é obrigatória")
        @Size(max = 2048, message = "A URL da fonte deve ter no máximo 2048 caracteres")
        @Pattern(regexp = "^https?://\\S+$", message = "A URL da fonte deve começar com http:// ou https://")
        String sourceUrl
) {
}
