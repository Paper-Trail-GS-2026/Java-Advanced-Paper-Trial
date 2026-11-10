package br.com.fiap.PaperTrail.guidance.dto;

import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;

import java.time.OffsetDateTime;

public record GuidanceChunkResponse(
        Long id,
        Long serviceId,
        String content,
        String sourceUrl,
        String sourceTitle,
        OffsetDateTime reviewedAt,
        Long version
) {
    public static GuidanceChunkResponse fromEntity(GuidanceChunk chunk) {
        return new GuidanceChunkResponse(
                chunk.getId(),
                chunk.getServiceId(),
                chunk.getContent(),
                chunk.getSourceUrl(),
                chunk.getSourceTitle(),
                chunk.getReviewedAt(),
                chunk.getVersion()
        );
    }
}
