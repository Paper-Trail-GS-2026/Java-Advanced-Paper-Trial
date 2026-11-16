package br.com.fiap.PaperTrail.service.controller;

import br.com.fiap.PaperTrail.service.dto.ServiceRequest;
import br.com.fiap.PaperTrail.service.dto.ServiceResponse;
import br.com.fiap.PaperTrail.service.dto.ServiceSnapshot;
import br.com.fiap.PaperTrail.service.service.PublicServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/services")
@PreAuthorize("hasAnyRole('CITIZEN','MANAGER')")
@RequiredArgsConstructor
@Tag(name = "Services")
public class PublicServiceController {
    private final PublicServiceService services;

    @GetMapping
    @Operation(summary = "List services")
    public PagedModel<ServiceResponse> findAll(@PageableDefault(sort = "id") Pageable pageable) {
        return new PagedModel<>(services.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a service")
    public ServiceResponse findById(@PathVariable @Positive Long id) {
        return services.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a service")
    public ResponseEntity<ServiceResponse> create(@RequestBody @Valid ServiceRequest request) {
        var response = services.create(request);
        return ResponseEntity.created(URI.create("/services/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a service")
    public ServiceResponse update(@PathVariable @Positive Long id, @RequestBody @Valid ServiceRequest request) {
        return services.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Delete a service")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        services.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/requirements-snapshot")
    @Operation(summary = "Get the service version and its requirements")
    public ServiceSnapshot snapshot(@PathVariable @Positive Long id) {
        return services.snapshot(id);
    }
}
