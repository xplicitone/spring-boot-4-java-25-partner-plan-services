package com.partnerplan.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The outbox in action. Inside the one transaction below, TWO writes happen:
 *
 *   1. the application row flips to APPROVED
 *   2. Spring Modulith's event publication registry inserts the event into
 *      the event_publication table (because ApplicationApproved is @Externalized)
 *
 * Only after the transaction commits does Modulith hand the event to the SNS
 * externalizer. If the SNS publish fails or the pod dies, the row stays incomplete
 * and is re-published on restart — approval and notification can no longer diverge,
 * which is the failure mode the old direct REST push had.
 */
@Service
public class ApprovalService {

    private final PartnerApplicationRepository applications;
    private final ApplicationEventPublisher events;

    public ApprovalService(PartnerApplicationRepository applications, ApplicationEventPublisher events) {
        this.applications = applications;
        this.events = events;
    }

    @Transactional
    public void approve(String applicationId) {
        PartnerApplication application = applications.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown application " + applicationId));

        if (!application.approve()) {
            return; // already decided; re-approval must not re-publish
        }

        events.publishEvent(ApplicationApproved.of(application));
    }
}
