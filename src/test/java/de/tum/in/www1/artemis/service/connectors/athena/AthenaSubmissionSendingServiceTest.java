package de.tum.in.www1.artemis.service.connectors.athena;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.AthenaRequestMockProvider;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;

class AthenaSubmissionSendingServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private AthenaRequestMockProvider athenaRequestMockProvider;

    @Autowired
    private AthenaSubmissionSendingService athenaSubmissionSendingService;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    private Feedback feedback;

    @BeforeEach
    void setUp() {
        athenaRequestMockProvider.enableMockingOfRequests();

        textExercise = new TextExercise();
        textExercise.setId(1L);
        textExercise.setTitle("Test Exercise");
        textExercise.setMaxPoints(10.0);

        textSubmission = new TextSubmission();
        textSubmission.setId(2L);

        feedback = new Feedback();
        feedback.setId(3L);
        feedback.setText("Feedback");
        feedback.setCredits(2.5);
    }

    @AfterEach
    void tearDown() throws Exception {
        athenaRequestMockProvider.reset();
    }

    @Test
    void sendFeedbackSuccess() {
        athenaRequestMockProvider.mockSendSubmissions();
        athenaSubmissionSendingService.sendSubmissions(textExercise);
    }
}
