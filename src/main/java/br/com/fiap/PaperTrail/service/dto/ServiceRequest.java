package br.com.fiap.PaperTrail.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ServiceRequest(
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 120) String cityName) {
    public ServiceRequest {
        title = title == null ? null : title.strip();
        description = description == null ? null : description.strip();
        cityName = cityName == null ? null : cityName.strip();
    }
}
