package br.com.fiap.PaperTrail.guidance.retrieval;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Normaliza texto para a busca lexical: minúsculas, sem acento, sem stopwords PT e plural simples. */
public final class TextNormalizer {
    private static final Set<String> STOPWORDS = Set.of(
            "a", "o", "as", "os", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das", "em", "no", "na",
            "nos", "nas", "por", "para", "pra", "com", "sem", "sobre", "e", "ou", "que", "se", "ao", "aos",
            "qual", "quais", "quem", "como", "onde", "quando", "preciso", "posso", "pode", "precisa", "ser",
            "ter", "tem", "sao", "foi", "eu", "meu", "minha", "seu", "sua", "este", "esta", "esse", "essa",
            "isso", "isto", "mais", "muito", "ja", "nao", "sim", "ha", "fazer", "faco",
            "devo", "me", "apresentar", "levar", "quero", "gostaria", "saber");

    private static final Map<String, String> TERMS = Map.of(
            "papel", "documento", "cadastrar", "cadastro", "renovar", "renovacao");

    private TextNormalizer() {
    }

    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String plain = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        List<String> tokens = new ArrayList<>();
        for (String raw : plain.split("[^a-z0-9]+")) {
            if (raw.isEmpty() || STOPWORDS.contains(raw)) {
                continue;
            }
            String token = singular(raw);
            tokens.add(TERMS.getOrDefault(token, token));
        }
        return tokens;
    }

    private static String singular(String token) {
        if (token.length() > 3 && token.endsWith("s")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }
}
