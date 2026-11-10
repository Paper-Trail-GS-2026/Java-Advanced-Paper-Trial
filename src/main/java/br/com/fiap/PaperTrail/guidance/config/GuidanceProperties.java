package br.com.fiap.PaperTrail.guidance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parâmetros da recuperação lexical (prefixo papertrail.guidance).
 *
 * @param topK             máximo de trechos enviados ao modelo
 * @param maxContextChars  limite de caracteres somados dos trechos enviados
 * @param minMatchRatio    fração mínima (0 a 1) dos termos da pergunta que o trecho precisa conter para
 *                         contar como evidência; o BM25 só ordena (um limiar absoluto de BM25 não é estável
 *                         em corpus pequeno)
 */
@ConfigurationProperties(prefix = "papertrail.guidance")
public record GuidanceProperties(int topK, int maxContextChars, double minMatchRatio) {
    public GuidanceProperties {
        topK = topK <= 0 ? 3 : topK;
        maxContextChars = maxContextChars <= 0 ? 3000 : maxContextChars;
        minMatchRatio = minMatchRatio <= 0 || minMatchRatio > 1 ? 0.34 : minMatchRatio;
    }
}
