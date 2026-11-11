package br.com.fiap.PaperTrail.service.dto;

import java.util.List;

public record ServiceSnapshot(Long serviceId, Long version, String title, List<RequirementInfo> requirements) {
    public record RequirementInfo(Long id, String title, boolean required, String sourceUrl) {}
}
