package com.partnerplan.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.Externalized;
import org.springframework.modulith.events.RoutingTarget;

/**
 * Routes externalized events to the env-prefixed SNS topic created by the infra repo
 * (output application_events_topic_arn). Done programmatically instead of hardcoding
 * the destination in the @Externalized annotation so the topic stays configuration.
 */
@Configuration
class EventExternalizationConfig {

    @Bean
    EventExternalizationConfiguration eventExternalization(
            @Value("${events.application-events-topic-arn}") String topicArn) {
        return EventExternalizationConfiguration.externalizing()
                .selectByAnnotation(Externalized.class)
                .route(ApplicationApproved.class, event -> RoutingTarget.forTarget(topicArn).withoutKey())
                .build();
    }
}
