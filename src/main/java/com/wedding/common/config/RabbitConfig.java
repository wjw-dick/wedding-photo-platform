package com.wedding.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String NOTIFY = "msg.notify";
    public static final String DELAY = "task.delay";
    public static final String REPORT = "report.async";
    public static final String MP_EVENT = "mp.event";
    public static final String DLX = "wedding.dlx";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange deadExchange() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    @Bean
    public Queue notifyQueue() {
        return QueueBuilder.durable(NOTIFY).deadLetterExchange(DLX).deadLetterRoutingKey("notify.dlq").build();
    }

    @Bean
    public Queue delayQueue() {
        // 延时通过消息级 expiration + 死信实现，支持不同业务的不同延时
        return QueueBuilder.durable(DELAY).deadLetterExchange(DLX).deadLetterRoutingKey("delay.dlq").build();
    }

    @Bean
    public Queue reportQueue() {
        return QueueBuilder.durable(REPORT).deadLetterExchange(DLX).deadLetterRoutingKey("report.dlq").build();
    }

    @Bean
    public Queue mpEventQueue() {
        return QueueBuilder.durable(MP_EVENT).deadLetterExchange(DLX).deadLetterRoutingKey("mp.dlq").build();
    }

    @Bean
    public Queue notifyDlq() {
        return QueueBuilder.durable(NOTIFY + ".dlq").build();
    }

    @Bean
    public Queue delayDlq() {
        return QueueBuilder.durable(DELAY + ".dlq").build();
    }

    @Bean
    public Queue reportDlq() {
        return QueueBuilder.durable(REPORT + ".dlq").build();
    }

    @Bean
    public Queue mpDlq() {
        return QueueBuilder.durable(MP_EVENT + ".dlq").build();
    }

    @Bean
    public Binding notifyDlqBinding() {
        return BindingBuilder.bind(notifyDlq()).to(deadExchange()).with("notify.dlq");
    }

    @Bean
    public Binding delayDlqBinding() {
        return BindingBuilder.bind(delayDlq()).to(deadExchange()).with("delay.dlq");
    }

    @Bean
    public Binding reportDlqBinding() {
        return BindingBuilder.bind(reportDlq()).to(deadExchange()).with("report.dlq");
    }

    @Bean
    public Binding mpDlqBinding() {
        return BindingBuilder.bind(mpDlq()).to(deadExchange()).with("mp.dlq");
    }
}
