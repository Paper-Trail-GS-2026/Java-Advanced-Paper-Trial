package br.com.fiap.PaperTrail.guidance.ai;

import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;

import java.util.List;

/** Porta para o modelo generativo. Permite testar o fluxo sem chamar a OpenAI (só em teste unitário). */
public interface GuidanceModel {
    /** Gera a resposta a partir da pergunta e dos trechos recuperados. Lança GuidanceModelException em falha. */
    String answer(String question, List<GuidanceChunk> sources);
}
