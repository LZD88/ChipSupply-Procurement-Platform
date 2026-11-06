package com.chipsupply.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ 领域事件拓扑：持久化 Topic Exchange、业务队列和死信队列。 */
@Configuration
@EnableRabbit
public class RabbitEventConfiguration {
    @Bean
    TopicExchange businessEventExchange(@Value("${chipsupply.events.exchange:chipsupply.events}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    TopicExchange businessEventDeadLetterExchange(
            @Value("${chipsupply.events.dead-letter-exchange:chipsupply.events.dlx}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    Queue businessEventQueue(
            @Value("${chipsupply.events.queue:chipsupply.platform.events}") String name,
            @Value("${chipsupply.events.dead-letter-exchange:chipsupply.events.dlx}") String deadLetterExchange) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey("failed")
                .build();
    }

    @Bean
    Queue businessEventDeadLetterQueue(
            @Value("${chipsupply.events.dead-letter-queue:chipsupply.platform.events.dlq}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean
    Binding businessEventBinding(
            @Qualifier("businessEventQueue") Queue businessEventQueue,
            @Qualifier("businessEventExchange") TopicExchange businessEventExchange) {
        return BindingBuilder.bind(businessEventQueue).to(businessEventExchange).with("#");
    }

    @Bean
    Binding deadLetterBinding(
            @Qualifier("businessEventDeadLetterQueue") Queue businessEventDeadLetterQueue,
            @Qualifier("businessEventDeadLetterExchange") TopicExchange businessEventDeadLetterExchange) {
        return BindingBuilder.bind(businessEventDeadLetterQueue)
                .to(businessEventDeadLetterExchange).with("failed");
    }

    @Bean
    MessageConverter rabbitJsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
