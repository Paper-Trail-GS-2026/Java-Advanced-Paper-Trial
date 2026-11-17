package br.com.fiap.PaperTrail.integration.catalogevents;

import br.com.fiap.PaperTrail.service.event.ServiceVersionChanged;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@ConditionalOnProperty(prefix = "papertrail.catalog-events", name = "enabled", havingValue = "true")
public class CatalogEventRelay {
    private static final Logger log = LoggerFactory.getLogger(CatalogEventRelay.class);

    private final CatalogEventPublisher publisher;
    private final CatalogEventsProperties properties;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "catalog-event-relay");
        thread.setDaemon(true);
        return thread;
    });

    public CatalogEventRelay(CatalogEventPublisher publisher, CatalogEventsProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onVersionChanged(ServiceVersionChanged event) {
        executor.execute(() -> sendWithRetry(event));
    }

    void sendWithRetry(ServiceVersionChanged event) {
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                publisher.publish(event);
                log.info("Evento de catálogo publicado: serviceId={} version={} eventId={}",
                        event.serviceId(), event.version(), event.eventId());
                return;
            } catch (RuntimeException e) {
                log.warn("Falha ao publicar evento serviceId={} version={} (tentativa {}/{}): {}",
                        event.serviceId(), event.version(), attempt, properties.maxAttempts(), e.getMessage());
                if (attempt < properties.maxAttempts() && !pause()) {
                    return;
                }
            }
        }
        log.error("Evento serviceId={} version={} não publicado após {} tentativas; o reconciliador reenviará.",
                event.serviceId(), event.version(), properties.maxAttempts());
    }

    private boolean pause() {
        try {
            Thread.sleep(properties.retryDelay().toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
