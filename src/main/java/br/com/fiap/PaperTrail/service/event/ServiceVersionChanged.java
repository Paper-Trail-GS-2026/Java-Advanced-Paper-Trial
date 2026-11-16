package br.com.fiap.PaperTrail.service.event;

import java.time.Instant;
import java.util.UUID;

public record ServiceVersionChanged(UUID eventId, Long serviceId, Long version, Instant occurredAt) {}
