package br.com.fiap.PaperTrail.guidance;

import br.com.fiap.PaperTrail.guidance.config.GuidanceProperties;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import br.com.fiap.PaperTrail.guidance.retrieval.LexicalRetriever;
import br.com.fiap.PaperTrail.guidance.retrieval.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Textos fictícios; não representam exigências de nenhum órgão. */
class LexicalRetrieverTest {

    private final LexicalRetriever retriever = new LexicalRetriever(new GuidanceProperties(2, 3000, 0.34));

    private GuidanceChunk chunk(long id, String title, String content) {
        return GuidanceChunk.builder().id(id).serviceId(1L).sourceTitle(title).content(content)
                .sourceUrl("https://example.invalid/" + id).reviewedAt(OffsetDateTime.parse("2026-11-01T00:00:00Z"))
                .version(1L).build();
    }

    private final List<GuidanceChunk> corpus = List.of(
            chunk(1, "Documentos de exemplo", "O cadastro fictício exige documento de identificação e comprovante de endereço."),
            chunk(2, "Prazo de exemplo", "O protocolo fictício tem prazo de análise informado no formulário."),
            chunk(3, "Taxa de exemplo", "A taxa fictícia de emissão é paga em guia própria."));

    @Test
    void normalizaAcentoCaixaStopwordsEPlural() {
        assertEquals(List.of("documento", "identificacao"), TextNormalizer.tokenize("Os DOCUMENTOS de Identificação"));
    }

    @Test
    void encontraTrechoRespondivel() {
        var result = retriever.retrieve("Quais documentos de identificação preciso levar?", corpus);
        assertEquals(1L, result.get(0).chunk().getId());
    }

    @Test
    void encontraParafraseComTermosProximos() {
        var result = retriever.retrieve("quanto custa a taxa de emissão?", corpus);
        assertEquals(3L, result.get(0).chunk().getId());
    }

    @Test
    void perguntaSemRelacaoNaoRecuperaNada() {
        assertTrue(retriever.retrieve("Como funciona o rodízio de veículos?", corpus).isEmpty());
    }

    @Test
    void respeitaTopKELimiteDeContexto() {
        var all = retriever.retrieve("fictício exemplo", corpus);
        assertTrue(all.size() <= 2);
        var limited = new LexicalRetriever(new GuidanceProperties(3, 10, 0.34))
                .retrieve("fictício exemplo", corpus);
        assertTrue(limited.isEmpty());
    }

    @Test
    void encontraDocumentoComFotoNaPerguntaInformal() {
        var result = retriever.retrieve("Que papel com foto devo apresentar para me cadastrar?",
                List.of(chunk(4, "Documentação", "O cadastro exige documento com foto.")));
        assertEquals(4L, result.get(0).chunk().getId());
    }

    @Test
    void ignoraTrechoGrandeEUsaOutroQueCabeNoContexto() {
        var limited = new LexicalRetriever(new GuidanceProperties(3, 40, 0.34));
        var result = limited.retrieve("documento",
                List.of(chunk(4, "Documento", "Documento " + "x".repeat(100)),
                        chunk(5, "Documento", "Documento com foto.")));
        assertEquals(List.of(5L), result.stream().map(item -> item.chunk().getId()).toList());
    }

    @Test
    void perguntaSoComStopwordsNaoRecupera() {
        assertTrue(retriever.retrieve("o que é isso?", corpus).isEmpty());
    }
}
