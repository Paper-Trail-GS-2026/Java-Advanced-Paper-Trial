package br.com.fiap.PaperTrail.guidance.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record GuidanceResponse(
        String answer,
        List<Source> sources,
        boolean insufficientEvidence
) {
    public record Source(String sourceUrl, String sourceTitle, OffsetDateTime reviewedAt) {
    }
}
