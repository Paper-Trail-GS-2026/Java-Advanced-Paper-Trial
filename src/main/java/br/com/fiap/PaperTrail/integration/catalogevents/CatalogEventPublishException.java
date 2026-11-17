package br.com.fiap.PaperTrail.integration.catalogevents;

public class CatalogEventPublishException extends RuntimeException {
    public CatalogEventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
