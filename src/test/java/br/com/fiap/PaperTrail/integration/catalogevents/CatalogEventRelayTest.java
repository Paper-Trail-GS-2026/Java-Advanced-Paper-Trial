package br.com.fiap.PaperTrail.integration.catalogevents;

import br.com.fiap.PaperTrail.service.event.ServiceVersionChanged;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogEventRelayTest {
    private final CatalogEventPublisher publisher = mock(CatalogEventPublisher.class);
    private final CatalogEventsProperties properties = new CatalogEventsProperties(
            true, null, null, null, null, 3, Duration.ofMillis(1), null);
    private final CatalogEventRelay relay = new CatalogEventRelay(publisher, properties);
    private final ServiceVersionChanged event =
            new ServiceVersionChanged(UUID.randomUUID(), 7L, 4L, Instant.parse("2026-11-16T12:00:00Z"));

    @Test
    void jsonSegueContrato() {
        var json = CatalogEventMessage.toJson(event);
        assertEquals("{\"eventId\":\"" + event.eventId() + "\",\"serviceId\":7,\"version\":4,"
                + "\"occurredAt\":\"2026-11-16T12:00:00Z\"}", json);
    }

    @Test
    void reconciliacaoUsaMesmoEventIdParaMesmaVersao() {
        var first = CatalogEventMessage.reconciliation(7L, 4L);
        var again = CatalogEventMessage.reconciliation(7L, 4L);
        var next = CatalogEventMessage.reconciliation(7L, 5L);
        assertEquals(first.eventId(), again.eventId());
        assertNotEquals(first.eventId(), next.eventId());
    }

    @Test
    void publicaNaPrimeiraTentativaQuandoBrokerResponde() {
        relay.sendWithRetry(event);
        verify(publisher, times(1)).publish(event);
    }

    @Test
    void tentaDeNovoEAceitaDepoisDeFalhaTemporaria() {
        doThrow(new CatalogEventPublishException("fora do ar", null))
                .doNothing().when(publisher).publish(event);
        relay.sendWithRetry(event);
        verify(publisher, times(2)).publish(event);
    }

    @Test
    void desisteAposMaximoDeTentativasSemLancarExcecao() {
        doThrow(new CatalogEventPublishException("fora do ar", null)).when(publisher).publish(event);
        assertDoesNotThrow(() -> relay.sendWithRetry(event));
        verify(publisher, times(3)).publish(event);
    }
}
