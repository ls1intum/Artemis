package de.tum.in.www1.artemis.service.connectors.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractAthenaTest;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.service.dto.athena.TextFeedbackDTO;

class AthenaFeedbackSuggestionsServiceTest extends AbstractAthenaTest {

    @Autowired
    private AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    @BeforeEach
    void setUp() {
        athenaRequestMockProvider.enableMockingOfRequests();

        textExercise = textExerciseUtilService.createSampleTextExercise(null);
        textSubmission = new TextSubmission(2L).text("This is a text submission");
    }

    @Test
    void testFeedbackSuggestions() throws NetworkingException {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect(jsonPath("$.exercise.id").value(textExercise.getId()),
                jsonPath("$.exercise.title").value(textExercise.getTitle()), jsonPath("$.submission.id").value(textSubmission.getId()),
                jsonPath("$.submission.text").value(textSubmission.getText()));
        List<TextFeedbackDTO> suggestions = athenaFeedbackSuggestionsService.getTextFeedbackSuggestions(textExercise, textSubmission);
        assertThat(suggestions.get(0).title()).isEqualTo("Not so good");
        assertThat(suggestions.get(0).indexStart()).isEqualTo(3);
    }
}
