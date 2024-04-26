package de.tum.in.www1.artemis.service.connectors.athena;

import static de.tum.in.www1.artemis.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static de.tum.in.www1.artemis.connector.AthenaRequestMockProvider.ATHENA_MODULE_TEXT_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractAthenaTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.service.dto.athena.ProgrammingFeedbackDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextFeedbackDTO;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

class AthenaFeedbackSuggestionsServiceTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenafeedbacksuggestionsservicetest";

    @Autowired
    private AthenaFeedbackSuggestionsService athenaFeedbackSuggestionsService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission;

    @BeforeEach
    void setUp() {
        athenaRequestMockProvider.enableMockingOfRequests();

        textExercise = textExerciseUtilService.createSampleTextExercise(null);
        textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);
        textSubmission = new TextSubmission(2L).text("This is a text submission");
        textSubmission.setParticipation(new StudentParticipation().exercise(textExercise));

        programmingExercise = programmingExerciseUtilService.createSampleProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setId(3L);
        programmingSubmission.setParticipation(new StudentParticipation().exercise(programmingExercise));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testFeedbackSuggestionsText() throws NetworkingException {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text", jsonPath("$.exercise.id").value(textExercise.getId()),
                jsonPath("$.exercise.title").value(textExercise.getTitle()), jsonPath("$.submission.id").value(textSubmission.getId()),
                jsonPath("$.submission.text").value(textSubmission.getText()));
        List<TextFeedbackDTO> suggestions = athenaFeedbackSuggestionsService.getTextFeedbackSuggestions(textExercise, textSubmission);
        assertThat(suggestions.get(0).title()).isEqualTo("Not so good");
        assertThat(suggestions.get(0).indexStart()).isEqualTo(3);
        athenaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testFeedbackSuggestionsProgramming() throws NetworkingException {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("programming", jsonPath("$.exercise.id").value(programmingExercise.getId()),
                jsonPath("$.exercise.title").value(programmingExercise.getTitle()), jsonPath("$.submission.id").value(programmingSubmission.getId()),
                jsonPath("$.submission.repositoryUri")
                        .value("https://artemislocal.ase.in.tum.de/api/public/athena/programming-exercises/" + programmingExercise.getId() + "/submissions/3/repository"));
        List<ProgrammingFeedbackDTO> suggestions = athenaFeedbackSuggestionsService.getProgrammingFeedbackSuggestions(programmingExercise, programmingSubmission);
        assertThat(suggestions.get(0).title()).isEqualTo("Not so good");
        assertThat(suggestions.get(0).lineStart()).isEqualTo(3);
        athenaRequestMockProvider.verify();
    }

    @Test
    void testFeedbackSuggestionsIdConflict() {
        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text");
        var otherExercise = new TextExercise();
        textSubmission.setParticipation(new StudentParticipation().exercise(otherExercise)); // Add submission to wrong exercise
        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> athenaFeedbackSuggestionsService.getTextFeedbackSuggestions(textExercise, textSubmission));
    }
}
