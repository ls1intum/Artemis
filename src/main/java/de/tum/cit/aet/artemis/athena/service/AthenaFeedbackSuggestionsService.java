package de.tum.cit.aet.artemis.athena.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.athena.dto.ExerciseBaseDTO;
import de.tum.cit.aet.artemis.athena.dto.ModelingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.ProgrammingFeedbackDTO;
import de.tum.cit.aet.artemis.athena.dto.ResponseMetaDTO;
import de.tum.cit.aet.artemis.athena.dto.SubmissionBaseDTO;
import de.tum.cit.aet.artemis.athena.dto.TextFeedbackDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Service for receiving feedback suggestions from the Athena service.
 * Assumes that submissions and already given feedback have already been sent to Athena or that the feedback is non-graded.
 */
@Service
@Profile(PROFILE_ATHENA)
public class AthenaFeedbackSuggestionsService {

    private static final Logger log = LoggerFactory.getLogger(AthenaFeedbackSuggestionsService.class);

    private final AthenaConnector<RequestDTO, ResponseDTOText> textAthenaConnector;

    private final AthenaConnector<RequestDTO, ResponseDTOProgramming> programmingAthenaConnector;

    private final AthenaConnector<RequestDTO, ResponseDTOModeling> modelingAthenaConnector;

    private final AthenaModuleService athenaModuleService;

    private final AthenaDTOConverterService athenaDTOConverterService;

    private final LLMTokenUsageService llmTokenUsageService;

    /**
     * Create a new AthenaFeedbackSuggestionsService to receive feedback suggestions from the Athena service.
     *
     * @param athenaRestTemplate        REST template used for the communication with Athena
     * @param athenaModuleService       Athena module serviced used to determine the urls for different modules
     * @param athenaDTOConverterService Service to convert exrcises and submissions to DTOs
     * @param llmTokenUsageService      Service to store the usage of LLM tokens
     */
    public AthenaFeedbackSuggestionsService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, AthenaModuleService athenaModuleService,
            AthenaDTOConverterService athenaDTOConverterService, LLMTokenUsageService llmTokenUsageService) {
        textAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOText.class);
        programmingAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOProgramming.class);
        modelingAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOModeling.class);
        this.athenaDTOConverterService = athenaDTOConverterService;
        this.athenaModuleService = athenaModuleService;
        this.llmTokenUsageService = llmTokenUsageService;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record RequestDTO(ExerciseBaseDTO exercise, SubmissionBaseDTO submission, boolean isGraded) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record ResponseDTOText(List<TextFeedbackDTO> data, ResponseMetaDTO meta) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record ResponseDTOProgramming(List<ProgrammingFeedbackDTO> data, ResponseMetaDTO meta) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record ResponseDTOModeling(List<ModelingFeedbackDTO> data, ResponseMetaDTO meta) {
    }

    /**
     * Calls the remote Athena service to get feedback suggestions for a given submission.
     *
     * @param exercise   the {@link TextExercise} the suggestions are fetched for
     * @param submission the {@link TextSubmission} the suggestions are fetched for
     * @param isGraded   the {@link Boolean} should Athena generate grade suggestions or not
     * @return a list of feedback suggestions
     */
    public List<TextFeedbackDTO> getTextFeedbackSuggestions(TextExercise exercise, TextSubmission submission, boolean isGraded) throws NetworkingException {
        log.debug("Start Athena '{}' Feedback Suggestions Service for Exercise '{}' (#{}).", isGraded ? "Graded" : "Non Graded", exercise.getTitle(), exercise.getId());

        if (!Objects.equals(submission.getParticipation().getExercise().getId(), exercise.getId())) {
            log.error("Exercise id {} does not match submission's exercise id {}", exercise.getId(), submission.getParticipation().getExercise().getId());
            throw new ConflictException("Exercise id " + exercise.getId() + " does not match submission's exercise id " + submission.getParticipation().getExercise().getId(),
                    "Exercise", "exerciseIdDoesNotMatch");
        }

        final RequestDTO request = new RequestDTO(athenaDTOConverterService.ofExercise(exercise), athenaDTOConverterService.ofSubmission(exercise.getId(), submission), isGraded);
        ResponseDTOText response = textAthenaConnector.invokeWithRetry(athenaModuleService.getAthenaModuleUrl(exercise) + "/feedback_suggestions", request, 0);
        log.info("Athena responded to '{}' feedback suggestions request: {}", isGraded ? "Graded" : "Non Graded", response.data);
        storeTokenUsage(exercise, submission, response.meta, !isGraded);
        return response.data.stream().toList();
    }

    /**
     * Calls the remote Athena service to get feedback suggestions for a given programming submission.
     *
     * @param exercise   the {@link ProgrammingExercise} the suggestions are fetched for
     * @param submission the {@link ProgrammingSubmission} the suggestions are fetched for
     * @param isGraded   the {@link Boolean} should Athena generate grade suggestions or not
     * @return a list of feedback suggestions
     */
    public List<ProgrammingFeedbackDTO> getProgrammingFeedbackSuggestions(ProgrammingExercise exercise, ProgrammingSubmission submission, boolean isGraded)
            throws NetworkingException {
        log.debug("Start Athena '{}' Feedback Suggestions Service for Exercise '{}' (#{}).", isGraded ? "Graded" : "Non Graded", exercise.getTitle(), exercise.getId());
        final RequestDTO request = new RequestDTO(athenaDTOConverterService.ofExercise(exercise), athenaDTOConverterService.ofSubmission(exercise.getId(), submission), isGraded);
        ResponseDTOProgramming response = programmingAthenaConnector.invokeWithRetry(athenaModuleService.getAthenaModuleUrl(exercise) + "/feedback_suggestions", request, 0);
        log.info("Athena responded to '{}' feedback suggestions request: {}", isGraded ? "Graded" : "Non Graded", response.data);
        storeTokenUsage(exercise, submission, response.meta, !isGraded);
        return response.data.stream().toList();
    }

    /**
     * Retrieve feedback suggestions for a given modeling exercise submission from Athena
     *
     * @param exercise   the {@link ModelingExercise} the suggestions are fetched for
     * @param submission the {@link ModelingSubmission} the suggestions are fetched for
     * @param isGraded   the {@link Boolean} should Athena generate grade suggestions or not
     * @return a list of feedback suggestions generated by Athena
     */
    public List<ModelingFeedbackDTO> getModelingFeedbackSuggestions(ModelingExercise exercise, ModelingSubmission submission, boolean isGraded) throws NetworkingException {
        log.debug("Start Athena '{}' Feedback Suggestions Service for Modeling Exercise '{}' (#{}).", isGraded ? "Graded" : "Non Graded", exercise.getTitle(), exercise.getId());

        if (!Objects.equals(submission.getParticipation().getExercise().getId(), exercise.getId())) {
            throw new ConflictException("Exercise id " + exercise.getId() + " does not match submission's exercise id " + submission.getParticipation().getExercise().getId(),
                    "Exercise", "exerciseIdDoesNotMatch");
        }

        final RequestDTO request = new RequestDTO(athenaDTOConverterService.ofExercise(exercise), athenaDTOConverterService.ofSubmission(exercise.getId(), submission), isGraded);
        ResponseDTOModeling response = modelingAthenaConnector.invokeWithRetry(athenaModuleService.getAthenaModuleUrl(exercise) + "/feedback_suggestions", request, 0);
        log.info("Athena responded to '{}' feedback suggestions request: {}", isGraded ? "Graded" : "Non Graded", response.data);
        storeTokenUsage(exercise, submission, response.meta, !isGraded);
        return response.data;
    }

    /**
     * Store the usage of LLM tokens for a given submission
     *
     * @param exercise              the exercise the submission belongs to
     * @param submission            the submission for which the tokens were used
     * @param meta                  the meta information of the response from Athena
     * @param isPreliminaryFeedback whether the feedback is preliminary or not
     */
    private void storeTokenUsage(Exercise exercise, Submission submission, ResponseMetaDTO meta, Boolean isPreliminaryFeedback) {
        if (meta == null) {
            return;
        }

        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        User user = ((StudentParticipation) submission.getParticipation()).getStudent().orElse(null);
        List<LLMRequest> llmRequests = meta.llmRequests();
        llmTokenUsageService.saveLLMTokenUsage(llmRequests, LLMServiceType.ATHENA,
                (llmTokenUsageBuilder -> llmTokenUsageBuilder.withCourse(course).withExercise(exercise).withUser(user)));
    }
}
