package br.com.fiap.PaperTrail.guidance.dto;

import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;

public record GuidanceChunkRequest(
        @NotBlank(message = "O conteúdo é obrigatório")
        @Size(max = 4000, message = "O conteúdo deve ter no máximo 4000 caracteres")
        String content,

        @NotBlank(message = "A URL da fonte é obrigatória")
        @Size(max = 2048, message = "A URL da fonte deve ter no máximo 2048 caracteres")
        @Pattern(regexp = "^https?://\\S+$", message = "A URL da fonte deve começar com http:// ou https://")
        String sourceUrl,

        @NotBlank(message = "O título da fonte é obrigatório")
        @Size(max = 200, message = "O título da fonte deve ter no máximo 200 caracteres")
        String sourceTitle,

        @NotNull(message = "A data de revisão é obrigatória")
        @PastOrPresent(message = "A data de revisão não pode estar no futuro")
        OffsetDateTime reviewedAt,

        @NotNull(message = "A versão é obrigatória")
        @Positive(message = "A versão deve ser positiva")
        Long version
) {
    public GuidanceChunk toEntity(Long serviceId) {
        return GuidanceChunk.builder()
                .serviceId(serviceId)
                .content(content.strip())
                .sourceUrl(sourceUrl.strip())
                .sourceTitle(sourceTitle.strip())
                .reviewedAt(reviewedAt)
                .version(version)
                .build();
    }
}
