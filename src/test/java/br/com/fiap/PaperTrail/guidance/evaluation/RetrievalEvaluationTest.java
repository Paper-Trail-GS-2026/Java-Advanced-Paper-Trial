package br.com.fiap.PaperTrail.guidance.evaluation;

import br.com.fiap.PaperTrail.guidance.ai.GuidanceModel;
import br.com.fiap.PaperTrail.guidance.ai.GuidanceModelException;
import br.com.fiap.PaperTrail.guidance.config.GuidanceProperties;
import br.com.fiap.PaperTrail.guidance.dto.GuidanceResponse;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import br.com.fiap.PaperTrail.guidance.evaluation.EvaluationSupport.EvalCase;
import br.com.fiap.PaperTrail.guidance.retrieval.LexicalRetriever;
import br.com.fiap.PaperTrail.guidance.service.GuidanceService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Avaliação OFFLINE e determinística da recuperação, da abstenção e do 503 sobre o dataset versionado.
 * O modelo aqui é um dublê de teste (não é IA): só captura quais trechos chegariam ao modelo.
 * Não mede qualidade da resposta generativa (ver LiveEvaluationTest, opt-in).
 */
class RetrievalEvaluationTest {

    record Outcome(EvalCase evalCase, boolean passed, String detail, List<Long> retrievedIds, int httpStatus) {
    }

    @TestFactory
    Stream<DynamicTest> avaliaDatasetSemChamarIa() {
        List<GuidanceChunk> corpus = EvaluationSupport.loadCorpus();
        List<EvalCase> dataset = EvaluationSupport.loadDataset();
        List<Outcome> outcomes = new ArrayList<>();
        for (EvalCase c : dataset) {
            outcomes.add(run(c, corpus));
        }
        EvaluationSupport.writeReport("guidance-evaluation-offline.md", report(outcomes));
        return outcomes.stream().map(o -> dynamicTest(o.evalCase().id() + " [" + o.evalCase().category() + "]", () -> {
            // limitação conhecida e documentada no dataset: aparece como "ignorado" com o motivo, e passa a
            // contar como acerto automaticamente se o recuperador melhorar
            Assumptions.assumeTrue(o.passed() || o.evalCase().knownLimitation() == null,
                    "Limitação conhecida: " + o.evalCase().knownLimitation());
            assertTrue(o.passed(), o.detail());
        }));
    }

    private Outcome run(EvalCase c, List<GuidanceChunk> corpus) {
        List<Long> seenByModel = new ArrayList<>();
        boolean down = c.expectedHttpStatus() == 503;
        GuidanceModel capturing = (question, sources) -> {
            sources.forEach(s -> seenByModel.add(s.getId()));
            if (down) {
                throw new GuidanceModelException("modelo fora do ar (simulado em teste)");
            }
            return "resposta de teste";
        };
        GuidanceService service = new GuidanceService(EvaluationSupport.inMemoryRepository(corpus),
                new LexicalRetriever(new GuidanceProperties(3, 3000, 0.34)), capturing);

        int status = 200;
        GuidanceResponse response = null;
        try {
            response = service.ask(c.serviceId(), c.question());
        } catch (ResponseStatusException e) {
            status = e.getStatusCode().value();
        }

        List<String> problems = new ArrayList<>();
        if (status != c.expectedHttpStatus()) {
            problems.add("HTTP " + status + " (esperado " + c.expectedHttpStatus() + ")");
        }
        if (response != null) {
            if (response.insufficientEvidence() != c.expectInsufficientEvidence()) {
                problems.add("insufficientEvidence=" + response.insufficientEvidence()
                        + " (esperado " + c.expectInsufficientEvidence() + ")");
            }
            List<Long> sourceIds = response.sources().stream()
                    .map(s -> EvaluationSupport.chunkIdByUrl(corpus, s.sourceUrl())).toList();
            if (!sourceIds.equals(seenByModel.stream().distinct().toList())) {
                problems.add("sources " + sourceIds + " diferem dos trechos enviados " + seenByModel);
            }
        }
        for (Long expected : c.expectedChunkIds()) {
            if (!seenByModel.contains(expected)) {
                problems.add("trecho esperado " + expected + " não recuperado");
            }
        }
        for (Long forbidden : c.forbiddenChunkIds()) {
            if (seenByModel.contains(forbidden)) {
                problems.add("trecho proibido " + forbidden + " foi recuperado");
            }
        }
        return new Outcome(c, problems.isEmpty(), c.id() + ": " + String.join("; ", problems), seenByModel, status);
    }

    private String report(List<Outcome> outcomes) {
        long passed = outcomes.stream().filter(Outcome::passed).count();
        StringBuilder sb = new StringBuilder("# Avaliação offline (recuperação, abstenção, 503)\n\n")
                .append("Corpus FICTÍCIO. Modelo = dublê de teste (sem IA).\n\n")
                .append("Casos: ").append(outcomes.size()).append(" | acertos: ").append(passed)
                .append(" | falhas: ").append(outcomes.size() - passed).append("\n\n")
                .append("| Caso | Categoria | Recuperados | HTTP | Resultado |\n|---|---|---|---|---|\n");
        for (Outcome o : outcomes) {
            sb.append("| ").append(o.evalCase().id()).append(" | ").append(o.evalCase().category())
                    .append(" | ").append(o.retrievedIds()).append(" | ").append(o.httpStatus())
                    .append(" | ").append(o.passed() ? "OK" : (o.evalCase().knownLimitation() != null ? "FALHA CONHECIDA: " : "FALHA: ")
                            + o.detail()).append(" |\n");
        }
        return sb.toString();
    }
}
