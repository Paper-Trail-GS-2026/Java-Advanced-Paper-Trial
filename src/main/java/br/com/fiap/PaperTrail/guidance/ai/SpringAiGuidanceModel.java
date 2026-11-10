package br.com.fiap.PaperTrail.guidance.ai;

import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/** Implementação real via Spring AI + OpenAI. Modelo, timeout e chave vêm das propriedades/variáveis de ambiente. */
@Component
public class SpringAiGuidanceModel implements GuidanceModel {
    private final ChatClient chatClient;

    public SpringAiGuidanceModel(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(GuidancePrompt.SYSTEM).build();
    }

    @Override
    public String answer(String question, List<GuidanceChunk> sources) {
        try {
            String content = chatClient.prompt()
                    .user(GuidancePrompt.userMessage(question, sources))
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                throw new GuidanceModelException("O modelo devolveu resposta vazia");
            }
            return content.strip();
        } catch (GuidanceModelException e) {
            throw e;
        } catch (RuntimeException e) {
            // não repassa a mensagem original: pode conter detalhes do provedor
            throw new GuidanceModelException("Falha ao consultar o modelo", e);
        }
    }
}
