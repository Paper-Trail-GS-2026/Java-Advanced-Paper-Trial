package br.com.fiap.PaperTrail.guidance;

import br.com.fiap.PaperTrail.guidance.ai.GuidanceModel;
import br.com.fiap.PaperTrail.guidance.ai.SpringAiGuidanceModel;
import br.com.fiap.PaperTrail.guidance.config.GuidanceProperties;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import br.com.fiap.PaperTrail.guidance.repository.GuidanceChunkRepository;
import br.com.fiap.PaperTrail.guidance.retrieval.LexicalRetriever;
import br.com.fiap.PaperTrail.guidance.service.GuidanceService;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@SpringBootTest(classes = SpringAiGuidanceModel.class)
@ImportAutoConfiguration({OpenAiChatAutoConfiguration.class, ChatClientAutoConfiguration.class,
        ToolCallingAutoConfiguration.class})
class OfficialSourcesLiveTest {
    @Autowired private GuidanceModel model;
    record Question(String key, String text, List<String> expectedPatterns, boolean insufficient) {}
    record Result(String service, String question, boolean passed, long latencyMs, boolean insufficientEvidence,
                  List<String> sourceUrls, String problem) {}

    @TestFactory
    Stream<DynamicTest> avaliaFontesOficiaisComModeloReal() throws Exception {
        OfficialSourcesRetrievalTest.Corpus corpus;
        var mapper = JsonMapper.builder().build();
        try (var input = getClass().getResourceAsStream("/guidance/official-services.json")) {
            corpus = mapper.readValue(input, OfficialSourcesRetrievalTest.Corpus.class);
        }
        var chunks = new ArrayList<GuidanceChunk>();
        for (int serviceIndex = 0; serviceIndex < corpus.services().size(); serviceIndex++) {
            var service = corpus.services().get(serviceIndex);
            for (var chunk : service.chunks()) {
                chunks.add(GuidanceChunk.builder().id((long) chunks.size() + 1).serviceId((long) serviceIndex + 1)
                        .version(1L).sourceUrl(service.sourceUrl()).sourceTitle(service.sourceTitle() + " | " + chunk.topic())
                        .reviewedAt(OffsetDateTime.parse(corpus.reviewedAt())).content(chunk.content()).build());
            }
        }
        var repository = mock(GuidanceChunkRepository.class);
        when(repository.findByServiceIdAndVersion(anyLong(), anyLong())).thenAnswer(call -> chunks.stream()
                .filter(chunk -> chunk.getServiceId().equals(call.getArgument(0)) && chunk.getVersion().equals(call.getArgument(1)))
                .toList());
        var guidance = new GuidanceService(repository, ignored -> 1,
                new LexicalRetriever(new GuidanceProperties(3, 3000, 0.34)), model);
        var questions = List.of(
                new Question("cin-sp", "Sou solteiro. Qual certidão levo para a primeira CIN?", List.of("nascimento"), false),
                new Question("cin-sp", "A primeira emissão da CIN é gratuita?", List.of("gratui|sem (cobranca|taxa)|nao (tem|ha) cobranca"), false),
                new Question("cadunico", "Posso fazer meu Cadastro Único no CRAS?", List.of("cras"), false),
                new Question("cadunico", "A inscrição garante automaticamente um benefício?", List.of("nao"), false),
                new Question("cnh-sp", "Quais números preciso informar para renovar a CNH?", List.of("cpf", "registro", "espelho"), false),
                new Question("cnh-sp", "Preciso passar por exame médico para renovar?", List.of("medic"), false),
                new Question("cin-sp", "Preciso exame toxicológico?", List.of(), true),
                new Question("cadunico", "Como renovar minha CNH?", List.of(), true),
                new Question("cnh-sp", "Qual a receita do bolo de cenoura?", List.of(), true));
        var results = new ArrayList<Result>();
        for (var question : questions) {
            long start = System.nanoTime();
            var problems = new ArrayList<String>();
            var urls = new ArrayList<String>();
            boolean insufficient = false;
            String answerText = "";
            try {
                int index = 0;
                while (!corpus.services().get(index).key().equals(question.key())) index++;
                var response = guidance.ask((long) index + 1, question.text());
                answerText = response.answer();
                insufficient = response.insufficientEvidence();
                if (insufficient != question.insufficient()) problems.add("Unexpected evidence classification");
                urls.addAll(response.sources().stream().map(source -> source.sourceUrl()).toList());
                if (!question.insufficient()) {
                    String answer = Normalizer.normalize(response.answer().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                            .replaceAll("\\p{M}+", "");
                    question.expectedPatterns().stream().filter(pattern -> !answer.matches("(?s).*(" + pattern + ").*"))
                            .forEach(pattern -> problems.add("Expected content absent: " + pattern));
                    if (urls.isEmpty() || urls.stream().anyMatch(url -> chunks.stream().noneMatch(chunk -> chunk.getSourceUrl().equals(url))))
                        problems.add("Invalid source references");
                    if (!response.answer().matches("(?s).*\\[\\d+].*")) problems.add("Citation marker absent");
                } else if (!urls.isEmpty()) problems.add("Abstention returned sources");
            } catch (Exception exception) {
                problems.add(exception.getClass().getSimpleName());
            }
            results.add(new Result(question.key(), question.text(), problems.isEmpty(),
                    (System.nanoTime() - start) / 1_000_000, insufficient, urls,
                    String.join("; ", problems) + (problems.isEmpty() ? "" : " | Answer: " + answerText)));
        }
        var report = Path.of("build/reports/guidance-official-live.json");
        Files.createDirectories(report.getParent());
        Files.writeString(report, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        return results.stream().map(result -> DynamicTest.dynamicTest(result.service() + ": " + result.question(),
                () -> assertTrue(result.passed(), result.problem())));
    }
}
