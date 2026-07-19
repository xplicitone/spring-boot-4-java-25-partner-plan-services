package com.partnerplan.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.events.core.EventPublicationRegistry;

/**
 * Proves the outbox guarantee end-to-end against H2: approving writes the event to the
 * event_publication table in the approval's transaction, and with SNS unreachable
 * (endpoint points at a closed port) the publication stays incomplete — i.e. it would
 * be re-published on restart instead of silently lost.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:outbox;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.cloud.aws.region.static=us-east-1",
        "spring.cloud.aws.credentials.access-key=test",
        "spring.cloud.aws.credentials.secret-key=test",
        "spring.cloud.aws.sns.endpoint=http://localhost:1",
        "events.application-events-topic-arn=arn:aws:sns:us-east-1:000000000000:test-application-events"
})
class ApprovalOutboxTest {

    @Autowired
    ApprovalService approvalService;

    @Autowired
    PartnerApplicationRepository applications;

    @Autowired
    EventPublicationRegistry registry;

    private long incompletePublicationsFor(String applicationId) {
        return registry.findIncompletePublications().stream()
                .filter(p -> p.getEvent() instanceof ApplicationApproved approved
                        && approved.applicationId().equals(applicationId))
                .count();
    }

    @Test
    void approvalWritesOutboxRowThatSurvivesSnsFailure() {
        applications.save(new PartnerApplication("app-42", "acme"));

        approvalService.approve("app-42");

        assertThat(applications.findById("app-42").orElseThrow().getStatus())
                .isEqualTo(PartnerApplication.Status.APPROVED);
        assertThat(incompletePublicationsFor("app-42")).isEqualTo(1);
    }

    @Test
    void reApprovingDoesNotPublishAgain() {
        applications.save(new PartnerApplication("app-43", "acme"));

        approvalService.approve("app-43");
        approvalService.approve("app-43");

        assertThat(incompletePublicationsFor("app-43")).isEqualTo(1);
    }
}
