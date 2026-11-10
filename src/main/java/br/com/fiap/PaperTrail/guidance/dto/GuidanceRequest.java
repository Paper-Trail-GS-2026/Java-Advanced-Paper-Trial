package br.com.fiap.PaperTrail.guidance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuidanceRequest(
        @NotBlank(message = "A pergunta é obrigatória")
        @Size(max = 500, message = "A pergunta deve ter no máximo 500 caracteres")
        String question
) {
}
