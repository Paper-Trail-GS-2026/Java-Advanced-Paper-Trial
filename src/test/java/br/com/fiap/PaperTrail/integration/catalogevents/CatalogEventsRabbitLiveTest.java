package br.com.fiap.PaperTrail.integration.catalogevents;

import br.com.fiap.PaperTrail.category.entity.Category;
import br.com.fiap.PaperTrail.category.repository.CategoryRepository;
import br.com.fiap.PaperTrail.service.entity.PublicService;
import br.com.fiap.PaperTrail.service.repository.PublicServiceRepository;
import br.com.fiap.PaperTrail.service.service.CatalogVersionService;
import br.com.fiap.PaperTrail.support.JwtTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste com RabbitMQ real. Só roda quando RABBITMQ_HOST está definido, por exemplo:
 * docker run -d -p 5672:5672 rabbitmq:4 e RABBITMQ_HOST=localhost.
 */
@SpringBootTest(properties = {
        "papertrail.catalog-events.enabled=true",
        "papertrail.catalog-events.max-attempts=2",
        "papertrail.catalog-events.retry-delay=100ms",
        "papertrail.catalog-events.confirm-timeout=3s"})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RABBITMQ_HOST", matches = ".+")
class CatalogEventsRabbitLiveTest extends JwtTestSupport {
    @Autowired private CategoryRepository categories;
    @Autowired private PublicServiceRepository services;
    @Autowired private CatalogVersionService versions;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private RabbitTemplate rabbit;
    @Autowired private CatalogEventsProperties properties;
    @Autowired private CatalogEventPublisher publisher;
    @Autowired private CatalogEventReconciler reconciler;
    private TransactionTemplate transactions;
    private Long serviceId;

    @BeforeEach
    void fixtures() {
        transactions = new TransactionTemplate(transactionManager);
        serviceId = transactions.execute(status -> {
            var category = new Category();
            category.setName("Events test " + UUID.randomUUID());
            category = categories.save(category);
            var service = new PublicService();
            service.setCategory(category);
            service.setTitle("Service for events");
            service.setDescription("Description");
            service.setCityName("Sao Paulo");
            return services.save(service).getId();
        });
        while (rabbit.receive(properties.queue()) != null) {
            // limpa mensagens de execuções anteriores
        }
    }

    private Message receive(long timeoutMs) {
        return rabbit.receive(properties.queue(), timeoutMs);
    }

    @Test
    void mudancaDeVersaoPublicaEventoDepoisDoCommit() {
        long newVersion = transactions.execute(status -> versions.incrementVersion(serviceId).getVersion());

        Message message = receive(5000);
        assertNotNull(message, "o evento deveria chegar à fila após o commit");
        var body = new String(message.getBody(), StandardCharsets.UTF_8);
        assertTrue(body.contains("\"serviceId\":" + serviceId), body);
        assertTrue(body.contains("\"version\":" + newVersion), body);
        assertTrue(body.contains("\"eventId\":\""), body);
        assertTrue(body.contains("\"occurredAt\":\""), body);
        assertEquals("application/json", message.getMessageProperties().getContentType());
    }

    @Test
    void transacaoRevertidaNaoPublicaEvento() {
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            versions.incrementVersion(serviceId);
            throw new IllegalStateException("rollback");
        }));
        assertNull(receive(1500), "rollback não pode publicar evento");
    }

    @Test
    void reconciliacaoReenviaVersaoAtualDeTodosOsServicos() {
        int sent = reconciler.reconcileNow();
        assertTrue(sent >= 1);
        var received = new ArrayList<String>();
        Message message;
        while ((message = receive(1500)) != null) {
            received.add(new String(message.getBody(), StandardCharsets.UTF_8));
        }
        assertTrue(received.stream().anyMatch(body -> body.contains("\"serviceId\":" + serviceId)),
                "o serviço criado deveria ter sido reenviado: " + received);
    }

    @Test
    void publicacaoSemBrokerFalhaDeFormaControlada() {
        var unreachable = new org.springframework.amqp.rabbit.connection.CachingConnectionFactory("localhost", 1);
        unreachable.setPublisherConfirmType(
                org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType.CORRELATED);
        unreachable.setConnectionTimeout(500);
        var broken = new CatalogEventPublisher(new RabbitTemplate(unreachable), properties);
        var event = CatalogEventMessage.reconciliation(serviceId, 99L);
        assertThrows(CatalogEventPublishException.class, () -> broken.publish(event));
        unreachable.destroy();
    }
}
