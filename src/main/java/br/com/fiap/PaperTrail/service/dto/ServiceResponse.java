package br.com.fiap.PaperTrail.service.dto;

import br.com.fiap.PaperTrail.service.entity.PublicService;

public record ServiceResponse(Long id, Long categoryId, String title, String description, String cityName, Long version) {
    public static ServiceResponse fromEntity(PublicService service) {
        return new ServiceResponse(service.getId(), service.getCategory().getId(), service.getTitle(),
                service.getDescription(), service.getCityName(), service.getVersion());
    }
}
