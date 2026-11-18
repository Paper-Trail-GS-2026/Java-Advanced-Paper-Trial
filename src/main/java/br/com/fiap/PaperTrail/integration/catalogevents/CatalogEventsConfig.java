package br.com.fiap.PaperTrail.integration.catalogevents;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(CatalogEventsProperties.class)
@EnableScheduling
@ConditionalOnProperty(prefix = "papertrail.catalog-events", name = "enabled", havingValue = "true")
public class CatalogEventsConfig {

    @Bean
    DirectExchange catalogExchange(CatalogEventsProperties properties) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    Queue catalogVersionQueue(CatalogEventsProperties properties) {
        return QueueBuilder.durable(properties.queue()).build();
    }

    @Bean
    Binding catalogVersionBinding(Queue catalogVersionQueue, DirectExchange catalogExchange,
                                  CatalogEventsProperties properties) {
        return BindingBuilder.bind(catalogVersionQueue).to(catalogExchange).with(properties.routingKey());
    }
}
