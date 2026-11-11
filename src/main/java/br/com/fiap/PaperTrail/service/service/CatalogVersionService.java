package br.com.fiap.PaperTrail.service.service;

import br.com.fiap.PaperTrail.service.CatalogReader;
import br.com.fiap.PaperTrail.service.entity.PublicService;
import br.com.fiap.PaperTrail.service.event.ServiceVersionChanged;
import br.com.fiap.PaperTrail.service.repository.PublicServiceRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogVersionService implements CatalogReader {
    private final PublicServiceRepository services;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public long currentVersion(Long serviceId) {
        return services.findCurrentVersion(serviceId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found."));
    }

    // Call before editing the service or its requirements, in the same transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    public PublicService incrementVersion(Long serviceId) {
        var service = services.findForUpdate(serviceId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found."));
        entityManager.refresh(service);
        if (service.getVersion() >= 999999999999999999L) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Service version limit reached.");
        }
        service.setVersion(service.getVersion() + 1);
        events.publishEvent(new ServiceVersionChanged(UUID.randomUUID(), serviceId, service.getVersion(), Instant.now()));
        return service;
    }
}
