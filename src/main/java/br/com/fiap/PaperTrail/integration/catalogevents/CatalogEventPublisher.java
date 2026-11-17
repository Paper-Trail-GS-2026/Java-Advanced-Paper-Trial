package br.com.fiap.PaperTrail.integration.catalogevents;

import br.com.fiap.PaperTrail.service.event.ServiceVersionChanged;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;


@Component
@ConditionalOnProperty(prefix = "papertrail.catalog-events", name = "enabled", havingValue = "true")
public class CatalogEventPublisher {
    private final RabbitTemplate rabbit;
    private final CatalogEventsProperties properties;

    public CatalogEventPublisher(RabbitTemplate rabbit, CatalogEventsProperties properties) {
        this.rabbit = rabbit;
        this.properties = properties;
    }

     @throws
    public void publish(ServiceVersionChanged event) {
        Message message = MessageBuilder.withBody(CatalogEventMessage.toBytes(event))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setMessageId(event.eventId().toString())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        var correlation = new CorrelationData(event.eventId().toString());
        try {
            rabbit.send(properties.exchange(), properties.routingKey(), message, correlation);
            var confirm = correlation.getFuture().get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                throw new CatalogEventPublishException("Broker recusou o evento: " + confirm.reason(), null);
            }
            if (correlation.getReturned() != null) {
                throw new CatalogEventPublishException("Evento sem fila de destino (retornado pelo broker).", null);
            }
        } catch (CatalogEventPublishException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogEventPublishException("Publicação interrompida.", e);
        } catch (Exception e) {
            throw new CatalogEventPublishException("Falha ao publicar o evento no RabbitMQ.", e);
        }
    }
}
