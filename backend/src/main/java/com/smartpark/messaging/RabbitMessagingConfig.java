package com.smartpark.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "smartpark.messaging.enabled", havingValue = "true")
public class RabbitMessagingConfig {

    public static final String EXCHANGE = "smartpark.events";
    public static final String QUEUE = "smartpark.reservations.events";
    public static final String ROUTING_KEY = "reservation.event";

    @Bean
    public DirectExchange smartParkExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue smartParkReservationEventsQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding smartParkReservationEventsBinding(DirectExchange smartParkExchange, Queue smartParkReservationEventsQueue) {
        return BindingBuilder.bind(smartParkReservationEventsQueue).to(smartParkExchange).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}

