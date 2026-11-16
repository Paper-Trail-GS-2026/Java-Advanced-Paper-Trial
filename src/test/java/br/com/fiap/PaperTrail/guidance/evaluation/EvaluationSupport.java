package br.com.fiap.PaperTrail.guidance.evaluation;

import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import br.com.fiap.PaperTrail.guidance.repository.GuidanceChunkRepository;
import br.com.fiap.PaperTrail.service.CatalogReader;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Carrega o corpus FICTÍCIO e o dataset de avaliação versionados em src/test/resources/guidance. */
final class EvaluationSupport {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Map<Long, Long> CATALOG_VERSIONS = Map.of(1L, 2L, 2L, 1L, 3L, 1L);

    private EvaluationSupport() {
    }

    /** Caso de avaliação. Esperado = trechos recuperados, referências e abstenção (não a resposta literal). */
    record EvalCase(String id, String category, Long serviceId, String question,
                    List<Long> expectedChunkIds, List<Long> forbiddenChunkIds,
                    Boolean expectInsufficientEvidence, Integer expectedHttpStatus,
                    List<String> answerMustNotContain, List<String> answerMustContain,
                    String knownLimitation) {
        EvalCase {
            expectedChunkIds = expectedChunkIds == null ? List.of() : expectedChunkIds;
            forbiddenChunkIds = forbiddenChunkIds == null ? List.of() : forbiddenChunkIds;
            expectInsufficientEvidence = expectInsufficientEvidence != null && expectInsufficientEvidence;
            expectedHttpStatus = expectedHttpStatus == null ? 200 : expectedHttpStatus;
            answerMustNotContain = answerMustNotContain == null ? List.of() : answerMustNotContain;
            answerMustContain = answerMustContain == null ? List.of() : answerMustContain;
        }
    }

    static List<GuidanceChunk> loadCorpus() {
        return read("/guidance/corpus-ficticio.json", new TypeReference<>() {
        });
    }

    static List<EvalCase> loadDataset() {
        return read("/guidance/evaluation-dataset.json", new TypeReference<>() {
        });
    }

    /** Substitui APENAS o banco (Oracle) por memória; a recuperação e o serviço são os reais. */
    static GuidanceChunkRepository inMemoryRepository(List<GuidanceChunk> corpus) {
        GuidanceChunkRepository repository = mock(GuidanceChunkRepository.class);
        when(repository.findByServiceIdAndVersion(anyLong(), anyLong())).thenAnswer(inv -> {
            Long serviceId = inv.getArgument(0);
            Long version = inv.getArgument(1);
            return corpus.stream().filter(c -> c.getServiceId().equals(serviceId) && c.getVersion().equals(version)).toList();
        });
        return repository;
    }

    static CatalogReader inMemoryCatalog() {
        return serviceId -> {
            Long version = CATALOG_VERSIONS.get(serviceId);
            if (version == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found.");
            return version;
        };
    }

    static Long chunkIdByUrl(List<GuidanceChunk> corpus, String url) {
        return corpus.stream().filter(c -> c.getSourceUrl().equals(url)).map(GuidanceChunk::getId)
                .findFirst().orElse(null);
    }

    static void writeReport(String fileName, String content) {
        try {
            Path dir = Path.of("build", "reports");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(fileName), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static <T> T read(String resource, TypeReference<T> type) {
        try (InputStream in = EvaluationSupport.class.getResourceAsStream(resource)) {
            return MAPPER.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
