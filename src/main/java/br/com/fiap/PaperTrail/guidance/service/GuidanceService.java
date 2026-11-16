package br.com.fiap.PaperTrail.guidance.service;

import br.com.fiap.PaperTrail.guidance.ai.GuidanceModel;
import br.com.fiap.PaperTrail.guidance.ai.GuidanceModelException;
import br.com.fiap.PaperTrail.guidance.dto.GuidanceResponse;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import br.com.fiap.PaperTrail.guidance.repository.GuidanceChunkRepository;
import br.com.fiap.PaperTrail.guidance.retrieval.LexicalRetriever;
import br.com.fiap.PaperTrail.guidance.retrieval.LexicalRetriever.ScoredChunk;
import br.com.fiap.PaperTrail.service.CatalogReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuidanceService {
    public static final String INSUFFICIENT_ANSWER =
            "Não encontrei nas fontes cadastradas informação suficiente para responder a essa pergunta. "
                    + "Confirme diretamente com o órgão responsável pelo serviço.";

    private final GuidanceChunkRepository chunkRepository;
    private final CatalogReader catalog;
    private final LexicalRetriever retriever;
    private final GuidanceModel model;

    public GuidanceResponse ask(Long serviceId, String question) {
        long version = catalog.currentVersion(serviceId);
        List<GuidanceChunk> candidates = chunkRepository.findByServiceIdAndVersion(serviceId, version);
        List<ScoredChunk> retrieved = retriever.retrieve(question, candidates);

        if (retrieved.isEmpty()) {
            log.info("Orientação serviceId={} sem evidência suficiente; modelo não chamado", serviceId);
            return new GuidanceResponse(INSUFFICIENT_ANSWER, List.of(), true);
        }

        List<GuidanceChunk> sources = retrieved.stream().map(ScoredChunk::chunk).toList();
        log.info("Orientação serviceId={} trechos usados={}", serviceId,
                sources.stream().map(GuidanceChunk::getId).toList());

        String answer;
        try {
            answer = model.answer(question, sources);
        } catch (GuidanceModelException e) {
            log.warn("Falha do modelo na orientação serviceId={}: {}", serviceId, e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A orientação por IA está indisponível no momento. Tente novamente mais tarde.");
        }

        // as fontes vêm sempre dos trechos recuperados, nunca do texto do modelo
        List<GuidanceResponse.Source> responseSources = sources.stream()
                .map(c -> new GuidanceResponse.Source(c.getSourceUrl(), c.getSourceTitle(), c.getReviewedAt()))
                .distinct()
                .toList();
        return new GuidanceResponse(answer, responseSources, false);
    }

}
