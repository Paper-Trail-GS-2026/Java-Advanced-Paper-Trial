package br.com.fiap.PaperTrail.guidance.ai;

public class GuidanceModelException extends RuntimeException {
    public GuidanceModelException(String message, Throwable cause) {
        super(message, cause);
    }

    public GuidanceModelException(String message) {
        super(message);
    }
}
