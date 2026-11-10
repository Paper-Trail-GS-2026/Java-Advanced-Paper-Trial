package br.com.fiap.PaperTrail.guidance.evaluation;

import br.com.fiap.PaperTrail.guidance.ai.GuidanceModel;
import br.com.fiap.PaperTrail.guidance.ai.SpringAiGuidanceModel;
import br.com.fiap.PaperTrail.guidance.config.GuidanceProperties;
import br.com.fiap.PaperTrail.guidance.dto.GuidanceResponse;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import br.com.fiap.PaperTrail.guidance.evaluation.EvaluationSupport.EvalCase;
import br.com.fiap.PaperTrail.guidance.retrieval.LexicalRetriever;
import br.com.fiap.PaperTrail.guidance.service.GuidanceService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * AVALIAÇÃO REAL contra a OpenAI (opt-in: só roda com OPENAI_API_KEY). Sem mock de IA.
 * Só o banco é substituído por memória. Grava build/reports/guidance-evaluation-live.md com
 * resultado, latência e resposta truncada por caso. O caso de "modelo fora do ar" (M1) é coberto
 * pelo SpringAiGuidanceModelLiveTest e pelo RetrievalEvaluationTest.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@SpringBootTest(classes = SpringAiGuidanceModel.class)
@ImportAutoConfiguration({OpenAiChatAutoConfiguration.class, ChatClientAutoConfiguration.class,
        ToolCallingAutoConfiguration.class})
class LiveEvaluationTest {
    private static final Pattern URL = Pattern.compile("https?://[^\\s)\\]>\"']+");

    @Autowired
    private GuidanceModel model;

    @Autowired
    private Environment environment;

    record Result(EvalCase evalCase, boolean passed, String detail, long millis, String answer) {
    }

    @TestFactory
    Stream<DynamicTest> avaliaFluxoCompletoComIaReal() {
        List<GuidanceChunk> corpus = EvaluationSupport.loadCorpus();
        GuidanceService service = new GuidanceService(EvaluationSupport.inMemoryRepository(corpus),
                new LexicalRetriever(new GuidanceProperties(3, 3000, 0.34)), model);
        List<Result> results = new ArrayList<>();
        for (EvalCase c : EvaluationSupport.loadDataset()) {
            if (c.expectedHttpStatus() == 503) {
                continue;
            }
            results.add(run(c, service, corpus));
        }
        EvaluationSupport.writeReport("guidance-evaluation-live.md", report(results));
        return results.stream().map(r -> dynamicTest(r.evalCase().id() + " [" + r.evalCase().category() + "]",
                () -> {
                    Assumptions.assumeTrue(r.passed() || r.evalCase().knownLimitation() == null,
                            "Limitação conhecida: " + r.evalCase().knownLimitation());
                    assertTrue(r.passed(), r.detail());
                }));
    }

    private Result run(EvalCase c, GuidanceService service, List<GuidanceChunk> corpus) {
        long start = System.nanoTime();
        List<String> problems = new ArrayList<>();
        String answer = "";
        try {
            GuidanceResponse r = service.ask(c.serviceId(), c.question());
            answer = r.answer();
            if (r.insufficientEvidence() != c.expectInsufficientEvidence()) {
                problems.add("insufficientEvidence=" + r.insufficientEvidence());
            }
            List<Long> ids = r.sources().stream().map(s -> EvaluationSupport.chunkIdByUrl(corpus, s.sourceUrl())).toList();
            c.expectedChunkIds().stream().filter(id -> !ids.contains(id))
                    .forEach(id -> problems.add("fonte esperada " + id + " ausente"));
            c.forbiddenChunkIds().stream().filter(ids::contains)
                    .forEach(id -> problems.add("fonte proibida " + id + " presente"));
            String lower = answer.toLowerCase(Locale.ROOT);
            c.answerMustNotContain().stream().filter(s -> lower.contains(s.toLowerCase(Locale.ROOT)))
                    .forEach(s -> problems.add("resposta contém '" + s + "'"));
            c.answerMustContain().stream().filter(s -> !lower.contains(s.toLowerCase(Locale.ROOT)))
                    .forEach(s -> problems.add("resposta não contém '" + s + "'"));
            Matcher m = URL.matcher(answer);
            while (m.find()) {
                String found = m.group();
                if (r.sources().stream().noneMatch(s -> found.startsWith(s.sourceUrl()))) {
                    problems.add("URL na resposta que não é fonte recuperada: " + found);
                }
            }
        } catch (ResponseStatusException e) {
            problems.add("HTTP " + e.getStatusCode().value() + " (modelo indisponível ou recusou a chamada)");
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        return new Result(c, problems.isEmpty(), c.id() + ": " + String.join("; ", problems), millis, answer);
    }

    private String report(List<Result> results) {
        long ok = results.stream().filter(Result::passed).count();
        double avg = results.stream().mapToLong(Result::millis).average().orElse(0);
        long max = results.stream().mapToLong(Result::millis).max().orElse(0);
        StringBuilder sb = new StringBuilder("# Avaliação real com a OpenAI\n\n")
                .append("Modelo: ").append(environment.getProperty("spring.ai.openai.chat.model"))
                .append(" | timeout: ").append(environment.getProperty("spring.ai.openai.timeout")).append("\n")
                .append("Corpus FICTÍCIO. Casos: ").append(results.size()).append(" | acertos: ").append(ok)
                .append(" | falhas: ").append(results.size() - ok)
                .append(" | latência média: ").append(Math.round(avg)).append(" ms | máxima: ").append(max).append(" ms\n\n")
                .append("| Caso | Categoria | ms | Resultado | Resposta (truncada) |\n|---|---|---|---|---|\n");
        for (Result r : results) {
            String answer = r.answer().replace('\n', ' ').replace("|", "/");
            sb.append("| ").append(r.evalCase().id()).append(" | ").append(r.evalCase().category())
                    .append(" | ").append(r.millis()).append(" | ").append(r.passed() ? "OK" : "FALHA: " + r.detail())
                    .append(" | ").append(answer.length() > 300 ? answer.substring(0, 300) + "..." : answer).append(" |\n");
        }
        return sb.toString();
    }
}
