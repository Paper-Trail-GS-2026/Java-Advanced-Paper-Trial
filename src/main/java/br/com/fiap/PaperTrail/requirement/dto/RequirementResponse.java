package br.com.fiap.PaperTrail.requirement.dto;

import br.com.fiap.PaperTrail.requirement.entity.Requirement;

public record RequirementResponse(
        Long id,
        Long serviceId,
        String title,
        boolean required,
        String sourceUrl
) {
    public static RequirementResponse fromEntity(Requirement requirement) {
        return new RequirementResponse(
                requirement.getId(),
                requirement.getService().getId(),
                requirement.getTitle(),
                requirement.isRequired(),
                requirement.getSourceUrl()
        );
    }
}
