package br.com.fiap.PaperTrail.integration.catalogevents;

import br.com.fiap.PaperTrail.service.event.ServiceVersionChanged;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public final class CatalogEventMessage {
    private CatalogEventMessage() {}

    public static String toJson(ServiceVersionChanged event) {
        return "{\"eventId\":\"" + event.eventId()
                + "\",\"serviceId\":" + event.serviceId()
                + ",\"version\":" + event.version()
                + ",\"occurredAt\":\"" + event.occurredAt() + "\"}";
    }

    public static byte[] toBytes(ServiceVersionChanged event) {
        return toJson(event).getBytes(StandardCharsets.UTF_8);
    }

    public static ServiceVersionChanged reconciliation(Long serviceId, Long version) {
        var id = UUID.nameUUIDFromBytes(
                ("service:" + serviceId + ":version:" + version).getBytes(StandardCharsets.UTF_8));
        return new ServiceVersionChanged(id, serviceId, version, Instant.now());
    }
}
