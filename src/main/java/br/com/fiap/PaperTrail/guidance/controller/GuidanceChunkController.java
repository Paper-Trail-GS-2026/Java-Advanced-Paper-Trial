package br.com.fiap.PaperTrail.guidance.controller;

import br.com.fiap.PaperTrail.guidance.dto.GuidanceChunkRequest;
import br.com.fiap.PaperTrail.guidance.dto.GuidanceChunkResponse;
import br.com.fiap.PaperTrail.guidance.service.GuidanceChunkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/services/{serviceId}/guidance-chunks")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
@Tag(name = "Trechos de orientação", description = "Manutenção dos trechos e fontes usados na orientação com RAG (somente gestor)")
public class GuidanceChunkController {
    private final GuidanceChunkService chunkService;

    @GetMapping
    @Operation(summary = "Lista os trechos de um serviço (paginado)")
    public PagedModel<GuidanceChunkResponse> findAll(@PathVariable @Positive Long serviceId, Pageable pageable) {
        return new PagedModel<>(chunkService.findChunks(serviceId, pageable)
                .map(GuidanceChunkResponse::fromEntity));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um trecho por ID dentro do serviço")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trecho encontrado"),
            @ApiResponse(responseCode = "404", description = "Trecho não encontrado para o serviço")
    })
    public GuidanceChunkResponse findById(@PathVariable @Positive Long serviceId, @PathVariable Long id) {
        return GuidanceChunkResponse.fromEntity(chunkService.findChunk(serviceId, id));
    }

    @PostMapping
    @Operation(summary = "Cria um trecho de orientação para o serviço")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Trecho criado"),
            @ApiResponse(responseCode = "400", description = "Erro de validação"),
            @ApiResponse(responseCode = "403", description = "Apenas gestor"),
            @ApiResponse(responseCode = "404", description = "Serviço não encontrado")
    })
    public ResponseEntity<GuidanceChunkResponse> create(@PathVariable @Positive Long serviceId,
                                                        @RequestBody @Valid GuidanceChunkRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(GuidanceChunkResponse.fromEntity(chunkService.createChunk(serviceId, request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza um trecho existente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trecho atualizado"),
            @ApiResponse(responseCode = "400", description = "Erro de validação"),
            @ApiResponse(responseCode = "404", description = "Trecho ou serviço não encontrado")
    })
    public GuidanceChunkResponse update(@PathVariable @Positive Long serviceId, @PathVariable Long id,
                                        @RequestBody @Valid GuidanceChunkRequest request) {
        return GuidanceChunkResponse.fromEntity(chunkService.updateChunk(serviceId, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove um trecho")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Trecho removido"),
            @ApiResponse(responseCode = "404", description = "Trecho não encontrado para o serviço")
    })
    public ResponseEntity<Void> delete(@PathVariable @Positive Long serviceId, @PathVariable Long id) {
        chunkService.deleteChunk(serviceId, id);
        return ResponseEntity.noContent().build();
    }
}
