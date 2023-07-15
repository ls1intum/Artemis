package de.tum.in.www1.artemis.service.connectors.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.AthenaRequestMockProvider;
import de.tum.in.www1.artemis.domain.TextBlockRef;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.exception.NetworkingError;

class AthenaFeedbackSuggestionsServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private AthenaRequestMockProvider athenaRequestMockProvider;

    @Autowired
    private AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    @BeforeEach
    void setUp() {
        athenaRequestMockProvider.enableMockingOfRequests();

        textExercise = new TextExercise();
        textExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC); // needed for feedback suggestions
        textExercise.setId(1L);
        textExercise.setTitle("Test Exercise");
        textExercise.setMaxPoints(10.0);

        textSubmission = new TextSubmission();
        textSubmission.setId(2L);
        textSubmission.setText("This is a text submission");
    }

    @AfterEach
    void tearDown() throws Exception {
        athenaRequestMockProvider.reset();
    }

    @Test
    void testFeedbackSuggestions() throws NetworkingError {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect(jsonPath("$.exercise.id").value(textExercise.getId()),
                jsonPath("$.exercise.title").value(textExercise.getTitle()), jsonPath("$.submission.id").value(textSubmission.getId()),
                jsonPath("$.submission.text").value(textSubmission.getText()));
        List<TextBlockRef> suggestions = athenaFeedbackSuggestionsService.getFeedbackSuggestions(textExercise, textSubmission);
        assertThat(suggestions.get(0).getFeedback().getText()).isEqualTo("Not so good");
        assertThat(suggestions.get(0).getBlock().getStartIndex()).isEqualTo(3);
        assertThat(suggestions.get(0).getFeedback().getReference()).isEqualTo(suggestions.get(0).getBlock().getId());
    }
}
