package com.chipsupply.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 发布消息并等待 Broker Confirm，只有 RabbitMQ 明确 ACK 后 Outbox 才标记完成。 */
@Component
@RequiredArgsConstructor
public class RabbitEventPublisher {
    private final RabbitTemplate rabbit;

    @Value("${chipsupply.events.exchange:chipsupply.events}")
    private String exchange;

    public void publish(Map<String, String> event) {
        String eventId = event.get("eventId");
        String routingKey = routingKey(event.get("eventType"));
        CorrelationData correlation = new CorrelationData(eventId);
        rabbit.convertAndSend(exchange, routingKey, new LinkedHashMap<>(event), message -> {
            message.getMessageProperties().setMessageId(eventId);
            message.getMessageProperties().setHeader("x-trace-id", event.get("traceId"));
            message.getMessageProperties().setHeader("x-event-type", event.get("eventType"));
            message.getMessageProperties().setContentType("application/json");
            return message;
        }, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ NACK: " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ 消息无法路由: "
                        + correlation.getReturned().getReplyText());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 RabbitMQ Confirm 时被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("RabbitMQ Confirm 超时或失败", exception);
        }
    }

    private String routingKey(String eventType) {
        return eventType == null ? "business.unknown" : "business." + eventType.toLowerCase().replace('_', '.');
    }
}
