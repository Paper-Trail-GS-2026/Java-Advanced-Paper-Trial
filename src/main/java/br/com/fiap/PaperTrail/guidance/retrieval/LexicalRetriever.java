package br.com.fiap.PaperTrail.guidance.retrieval;

import br.com.fiap.PaperTrail.guidance.config.GuidanceProperties;
import br.com.fiap.PaperTrail.guidance.entity.GuidanceChunk;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recuperação lexical por BM25 simples sobre os trechos já filtrados por serviço/versão.
 * Sem embeddings (proposta aprovada; confirmar com o professor).
 */
@Component
@RequiredArgsConstructor
public class LexicalRetriever {
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final GuidanceProperties properties;

    public record ScoredChunk(GuidanceChunk chunk, double score) {
    }

    public List<ScoredChunk> retrieve(String question, List<GuidanceChunk> candidates) {
        List<String> queryTerms = TextNormalizer.tokenize(question).stream().distinct().toList();
        if (queryTerms.isEmpty() || candidates.isEmpty()) {
            return List.of();
        }

        List<Map<String, Integer>> frequencies = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (GuidanceChunk chunk : candidates) {
            List<String> tokens = TextNormalizer.tokenize(chunk.getSourceTitle() + " " + chunk.getContent());
            Map<String, Integer> tf = new HashMap<>();
            tokens.forEach(t -> tf.merge(t, 1, Integer::sum));
            frequencies.add(tf);
            lengths.add(tokens.size());
            Set<String> unique = new HashSet<>(tf.keySet());
            unique.forEach(t -> documentFrequency.merge(t, 1, Integer::sum));
        }
        double averageLength = Math.max(1.0, lengths.stream().mapToInt(Integer::intValue).average().orElse(1.0));
        int total = candidates.size();

        List<ScoredChunk> ranked = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            double score = 0;
            int matched = 0;
            for (String term : queryTerms) {
                int tf = frequencies.get(i).getOrDefault(term, 0);
                if (tf == 0) {
                    continue;
                }
                matched++;
                int df = documentFrequency.getOrDefault(term, 0);
                double idf = Math.log(1 + (total - df + 0.5) / (df + 0.5));
                double norm = tf + K1 * (1 - B + B * lengths.get(i) / averageLength);
                score += idf * (tf * (K1 + 1)) / norm;
            }
            if (matched > 0 && (double) matched / queryTerms.size() >= properties.minMatchRatio()) {
                ranked.add(new ScoredChunk(candidates.get(i), score));
            }
        }
        ranked.sort(Comparator.comparingDouble(ScoredChunk::score).reversed()
                .thenComparing(s -> s.chunk().getId(), Comparator.nullsLast(Comparator.naturalOrder())));

        List<ScoredChunk> selected = new ArrayList<>();
        int usedChars = 0;
        for (ScoredChunk scored : ranked) {
            if (selected.size() >= properties.topK()) {
                break;
            }
            int size = scored.chunk().getContent().length();
            if (!selected.isEmpty() && usedChars + size > properties.maxContextChars()) {
                continue;
            }
            selected.add(scored);
            usedChars += size;
        }
        return selected;
    }
}
