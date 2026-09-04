package de.tum.cit.aet.artemis.athena.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsageCollector;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * What the Athena feedback suggestion path records.
 * <p>
 * A unit test rather than an addition to {@code AthenaFeedbackSuggestionsServiceTest}, which is a Spring integration
 * test: observing the collector there would need a bean override on a leaf test class, and
 * {@code SpringContextConfigurationArchitectureTest} forbids that because it forks the test context and costs another
 * server start.
 */
class AthenaFeedbackSuggestionsUsageTest {

    private static final long EXERCISE_ID = 77L;

    private RestTemplate restTemplate;

    private FeatureUsageCollector featureUsageCollector;

    private AthenaFeedbackSuggestionsService service;

    private TextExercise exercise;

    private TextSubmission submission;

    @BeforeEach
    void init() {
        restTemplate = mock(RestTemplate.class);
        featureUsageCollector = mock(FeatureUsageCollector.class);
        var athenaModuleService = mock(AthenaModuleService.class);
        var dtoConverterService = mock(AthenaDTOConverterService.class);
        var resultRepository = mock(ResultRepository.class);

        // Only the module URL needs stubbing. Mockito already answers an empty Optional for the result lookup and null
        // for the DTO conversions, and the request is never serialised because the REST call below throws first.
        when(athenaModuleService.getAthenaModuleUrl(any())).thenReturn("http://athena.example.com/modules/text");

        service = new AthenaFeedbackSuggestionsService(restTemplate, athenaModuleService, dtoConverterService, mock(LLMTokenUsageService.class), resultRepository,
                Optional.<LearnerProfileApi>empty(), Optional.<CourseCompetencyApi>empty(), mock(UserAiPreferenceService.class), Optional.of(featureUsageCollector));

        exercise = new TextExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setFeedbackSuggestionModule("module_text_test");

        var participation = new StudentParticipation();
        participation.setId(5L);
        participation.setExercise(exercise);
        submission = new TextSubmission();
        submission.setId(9L);
        submission.setParticipation(participation);
    }

    /**
     * Athena being unreachable is exactly what an error rate on this feature should surface. Recording only after a
     * successful response, with {@code failed} hard-coded to false, made that structurally impossible: a failed request
     * produced no observation at all, so the feature could never show anything but a perfect record.
     */
    @Test
    void shouldRecordAFailedAthenaRequestAsAFailure() {
        when(restTemplate.postForEntity(anyString(), any(), any())).thenThrow(new ResourceAccessException("Athena is unreachable"));

        assertThatExceptionOfType(NetworkingException.class).isThrownBy(() -> service.getTextFeedbackSuggestions(exercise, submission, true, null));

        verify(featureUsageCollector).recordUsage(eq(FeatureKind.BACKGROUND), eq("athena"), eq("feedback-suggestions/text/graded"), eq(Role.ANONYMOUS), eq(true), anyLong());
    }

    /**
     * The counterpart, so the failure flag is not simply always set: an exercise with no feedback suggestion module
     * returns early and is not a use of the feature at all, failed or otherwise.
     */
    @Test
    void shouldRecordNoUsageWhenTheExerciseHasNoFeedbackSuggestionModule() throws NetworkingException {
        exercise.setFeedbackSuggestionModule(null);

        assertThat(service.getTextFeedbackSuggestions(exercise, submission, true, null)).isEmpty();

        verify(featureUsageCollector, never()).recordUsage(any(FeatureKind.class), anyString(), anyString(), any(Role.class), anyBoolean(), anyLong());
    }
}
