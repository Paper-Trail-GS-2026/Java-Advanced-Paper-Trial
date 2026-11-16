package br.com.fiap.PaperTrail.requirement.controller;

import br.com.fiap.PaperTrail.requirement.dto.RequirementRequest;
import br.com.fiap.PaperTrail.requirement.dto.RequirementResponse;
import br.com.fiap.PaperTrail.requirement.service.RequirementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping("/services/{serviceId}/requirements")
@PreAuthorize("hasAnyRole('CITIZEN','MANAGER')")
@RequiredArgsConstructor
@Tag(name = "Requisitos", description = "Requisitos de um serviço público: leitura para cidadão e gestor, escrita só para gestor")
public class RequirementController {
    private final RequirementService requirements;

    @GetMapping
    @Operation(summary = "Lista os requisitos de um serviço (paginado)")
    @ApiResponse(responseCode = "404", description = "Serviço não encontrado")
    public PagedModel<RequirementResponse> findAll(@PathVariable @Positive Long serviceId,
                                                   @PageableDefault(sort = "id") Pageable pageable) {
        return new PagedModel<>(requirements.findAll(serviceId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um requisito por ID dentro do serviço")
    @ApiResponse(responseCode = "404", description = "Requisito não encontrado para o serviço")
    public RequirementResponse findById(@PathVariable @Positive Long serviceId, @PathVariable @Positive Long id) {
        return requirements.findById(serviceId, id);
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Cria um requisito e incrementa a versão do serviço")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Requisito criado"),
            @ApiResponse(responseCode = "400", description = "Erro de validação"),
            @ApiResponse(responseCode = "403", description = "Apenas gestor"),
            @ApiResponse(responseCode = "404", description = "Serviço não encontrado")
    })
    public ResponseEntity<RequirementResponse> create(@PathVariable @Positive Long serviceId,
                                                      @RequestBody @Valid RequirementRequest request) {
        var response = requirements.create(serviceId, request);
        return ResponseEntity.created(URI.create("/services/" + serviceId + "/requirements/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Atualiza um requisito e incrementa a versão do serviço")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Requisito atualizado"),
            @ApiResponse(responseCode = "400", description = "Erro de validação"),
            @ApiResponse(responseCode = "403", description = "Apenas gestor"),
            @ApiResponse(responseCode = "404", description = "Requisito não encontrado para o serviço")
    })
    public RequirementResponse update(@PathVariable @Positive Long serviceId, @PathVariable @Positive Long id,
                                      @RequestBody @Valid RequirementRequest request) {
        return requirements.update(serviceId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Remove um requisito e incrementa a versão do serviço")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Requisito removido"),
            @ApiResponse(responseCode = "403", description = "Apenas gestor"),
            @ApiResponse(responseCode = "404", description = "Requisito não encontrado para o serviço")
    })
    public ResponseEntity<Void> delete(@PathVariable @Positive Long serviceId, @PathVariable @Positive Long id) {
        requirements.delete(serviceId, id);
        return ResponseEntity.noContent().build();
    }
}
