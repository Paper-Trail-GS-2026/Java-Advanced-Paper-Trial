package br.com.fiap.PaperTrail.guidance;

import br.com.fiap.PaperTrail.guidance.config.GuidanceProperties;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import br.com.fiap.PaperTrail.guidance.retrieval.LexicalRetriever;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class OfficialSourcesRetrievalTest {
    record Requirement(String title, boolean required) {}
    record Chunk(String topic, String content) {}
    record Service(String key, String title, String description, String cityName, String sourceTitle,
                   String sourceUrl, List<Requirement> requirements, List<Chunk> chunks) {}
    record Corpus(String reviewedAt, String category, List<Service> services) {}
    record Question(String service, String text, String expectedTopic) {}

    private Corpus corpus() throws IOException {
        try (var input = getClass().getResourceAsStream("/guidance/official-services.json")) {
            assertNotNull(input);
            return JsonMapper.builder().build().readValue(input, Corpus.class);
        }
    }

    @Test
    void cargaTemFontesOficiaisEConteudoDentroDosContratos() throws IOException {
        var corpus = corpus();
        assertEquals(3, corpus.services().size());
        assertEquals(3, corpus.services().stream().map(Service::key).distinct().count());
        assertNotNull(OffsetDateTime.parse(corpus.reviewedAt()));
        for (var service : corpus.services()) {
            var address = URI.create(service.sourceUrl());
            assertEquals("https", address.getScheme());
            assertTrue(address.getHost().endsWith(".gov.br"));
            assertTrue(service.title().length() <= 160);
            assertTrue(service.cityName().length() <= 120);
            assertTrue(service.description().length() <= 2000);
            assertFalse(service.requirements().isEmpty());
            assertTrue(service.chunks().size() >= 3);
            service.requirements().forEach(requirement -> assertTrue(requirement.title().length() <= 200));
            service.chunks().forEach(chunk -> {
                assertFalse(chunk.content().isBlank());
                assertTrue(chunk.content().length() <= 4000);
                assertTrue((service.sourceTitle() + " | " + chunk.topic()).length() <= 200);
            });
        }
    }

    @TestFactory
    Stream<DynamicTest> recuperaTrechosDasPerguntasDaDemonstracao() throws IOException {
        var corpus = corpus();
        var questions = List.of(
                new Question("cin-sp", "Qual certidão devo levar para a CIN?", "Documentos e certidões"),
                new Question("cin-sp", "Preciso informar o CPF?", "CPF"),
                new Question("cin-sp", "Como agendar o atendimento?", "Agendamento e atendimento"),
                new Question("cin-sp", "A primeira emissão é gratuita?", "Gratuidade"),
                new Question("cadunico", "Quais documentos da família devo levar?", "Documentação"),
                new Question("cadunico", "Onde acontece o cadastramento e tem cobrança?", "Local e custo"),
                new Question("cadunico", "A inscrição garante benefício automático?", "Cadastro e benefícios"),
                new Question("cadunico", "Quem mora sozinho pode se cadastrar?", "Quem mora sozinho"),
                new Question("cnh-sp", "Quais números da CNH devo informar?", "Documentos e identificação"),
                new Question("cnh-sp", "Preciso passar por exame médico?", "Exame médico"),
                new Question("cnh-sp", "Quem tem EAR precisa avaliação psicológica?", "Categorias e EAR"),
                new Question("cnh-sp", "A CNH digital é gratuita?", "Formato e cobrança"),
                new Question("cin-sp", "Preciso exame toxicológico?", null),
                new Question("cadunico", "Como renovar minha CNH?", null),
                new Question("cnh-sp", "Qual a receita do bolo de cenoura?", null));
        return questions.stream().map(question -> DynamicTest.dynamicTest(question.service() + ": " + question.text(), () -> {
            int index = IntStream.range(0, corpus.services().size())
                    .filter(i -> corpus.services().get(i).key().equals(question.service())).findFirst().orElseThrow();
            var service = corpus.services().get(index);
            var chunks = IntStream.range(0, service.chunks().size()).mapToObj(i -> GuidanceChunk.builder()
                    .id((long) i + 1).serviceId((long) index + 1).version(1L)
                    .content(service.chunks().get(i).content())
                    .sourceTitle(service.sourceTitle() + " | " + service.chunks().get(i).topic())
                    .sourceUrl(service.sourceUrl()).reviewedAt(OffsetDateTime.parse(corpus.reviewedAt())).build()).toList();
            var found = new LexicalRetriever(new GuidanceProperties(3, 3000, 0.34)).retrieve(question.text(), chunks);
            if (question.expectedTopic() == null) {
                assertTrue(found.isEmpty());
            } else {
                assertTrue(found.stream().anyMatch(item -> item.chunk().getSourceTitle().endsWith(question.expectedTopic())),
                        () -> "Trechos recuperados: " + found.stream().map(item -> item.chunk().getSourceTitle()).toList());
            }
        }));
    }
}
