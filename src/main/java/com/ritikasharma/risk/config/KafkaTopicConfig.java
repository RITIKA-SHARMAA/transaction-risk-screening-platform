package com.ritikasharma.risk.config;

import com.ritikasharma.risk.messaging.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Configuration
public class KafkaTopicConfig {

    private final int partitions;
    private final short replicas;

    public KafkaTopicConfig(@Value("${risk.kafka.topics.partitions}") int partitions,
                            @Value("${risk.kafka.topics.replicas}") short replicas) {
        this.partitions = partitions;
        this.replicas = replicas;
    }

    @Bean
    public KafkaAdmin.NewTopics riskTopics() {
        List<NewTopic> topics = new ArrayList<>();
        Stream.of(Topics.TXN_SUBMITTED, Topics.TXN_SCREENING_SIGNAL, Topics.TXN_RISK_SIGNAL, Topics.TXN_DECIDED)
                .forEach(name -> {
                    topics.add(TopicBuilder.name(name).partitions(partitions).replicas(replicas).build());
                    topics.add(TopicBuilder.name(name + Topics.DLT_SUFFIX).partitions(partitions).replicas(replicas).build());
                });
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }
}
