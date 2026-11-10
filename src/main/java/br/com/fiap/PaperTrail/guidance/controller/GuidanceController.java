package br.com.fiap.PaperTrail.guidance.controller;

import br.com.fiap.PaperTrail.guidance.dto.GuidanceRequest;
import br.com.fiap.PaperTrail.guidance.dto.GuidanceResponse;
import br.com.fiap.PaperTrail.guidance.service.GuidanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/services/{serviceId}/guidance")
@PreAuthorize("hasAnyRole('CITIZEN','MANAGER')")
@RequiredArgsConstructor
@Tag(name = "Orientação", description = "Explicação de um serviço com base apenas nas fontes cadastradas (RAG)")
public class GuidanceController {
    private final GuidanceService guidanceService;

    @PostMapping
    @Operation(summary = "Responde uma pergunta sobre o serviço com referências das fontes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resposta com fontes, ou abstenção (insufficientEvidence=true)"),
            @ApiResponse(responseCode = "400", description = "Pergunta inválida"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "503", description = "Modelo de IA indisponível")
    })
    public GuidanceResponse ask(@PathVariable @Positive Long serviceId,
                                @RequestBody @Valid GuidanceRequest request) {
        return guidanceService.ask(serviceId, request.question());
    }
}
