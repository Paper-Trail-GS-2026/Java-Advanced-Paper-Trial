package br.com.fiap.PaperTrail.guidance;

import br.com.fiap.PaperTrail.guidance.ai.GuidanceModel;
import br.com.fiap.PaperTrail.guidance.ai.GuidanceModelException;
import br.com.fiap.PaperTrail.guidance.ai.SpringAiGuidanceModel;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TESTE REAL contra a OpenAI (opt-in): só roda se OPENAI_API_KEY existir. Não usa mock.
 * Sobe apenas o cliente de IA, sem banco.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@SpringBootTest(classes = SpringAiGuidanceModel.class)
@ImportAutoConfiguration({OpenAiChatAutoConfiguration.class, ChatClientAutoConfiguration.class,
        org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class})
class SpringAiGuidanceModelLiveTest {

    @Autowired
    private GuidanceModel model;

    @Test
    void respondeComBaseNasFontes() {
        GuidanceChunk chunk = GuidanceChunk.builder().id(1L).serviceId(1L)
                .sourceTitle("Documentos de exemplo (fictício)")
                .content("O cadastro fictício exige documento de identificação e comprovante de endereço.")
                .sourceUrl("https://example.invalid/papertrail/1")
                .reviewedAt(OffsetDateTime.parse("2026-11-01T00:00:00Z")).version(1L).build();

        String answer = model.answer("Quais documentos preciso para o cadastro?", List.of(chunk));

        assertFalse(answer.isBlank());
        System.out.println("[live] resposta com " + answer.length() + " caracteres");
    }

    @Test
    void modeloIndisponivelLancaExcecaoDeDominio() {
        // usa um endereço inalcançável para simular provedor fora do ar de verdade (sem mock)
        var down = new SpringAiGuidanceModel(
                org.springframework.ai.chat.client.ChatClient.builder(
                        org.springframework.ai.openai.OpenAiChatModel.builder()
                                .options(org.springframework.ai.openai.OpenAiChatOptions.builder()
                                        .baseUrl("http://127.0.0.1:9").apiKey("invalida").timeout(java.time.Duration.ofSeconds(3))
                                        .build())
                                .build()));
        assertThrows(GuidanceModelException.class, () -> down.answer("pergunta", List.of()));
    }
}
