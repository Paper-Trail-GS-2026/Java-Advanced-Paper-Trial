package br.com.fiap.PaperTrail.integration.catalogevents;

import br.com.fiap.PaperTrail.service.repository.PublicServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "papertrail.catalog-events", name = "enabled", havingValue = "true")
public class CatalogEventReconciler {
    private static final Logger log = LoggerFactory.getLogger(CatalogEventReconciler.class);

    private final PublicServiceRepository services;
    private final CatalogEventPublisher publisher;

    public CatalogEventReconciler(PublicServiceRepository services, CatalogEventPublisher publisher) {
        this.services = services;
        this.publisher = publisher;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${papertrail.catalog-events.reconcile-interval:PT5M}")
    @Transactional(readOnly = true)
    public void reconcile() {
        int sent = reconcileNow();
        log.debug("Reconciliação de versões do catálogo: {} serviços reenviados.", sent);
    }

    /** @return quantidade de serviços reenviados com confirmação do broker. */
    @Transactional(readOnly = true)
    public int reconcileNow() {
        int sent = 0;
        for (var service : services.findAll()) {
            try {
                publisher.publish(CatalogEventMessage.reconciliation(service.getId(), service.getVersion()));
                sent++;
            } catch (RuntimeException e) {
                log.warn("Reconciliação não conseguiu publicar serviceId={}: {}", service.getId(), e.getMessage());
                return sent; // broker indisponível; tenta de novo no próximo ciclo
            }
        }
        return sent;
    }
}
