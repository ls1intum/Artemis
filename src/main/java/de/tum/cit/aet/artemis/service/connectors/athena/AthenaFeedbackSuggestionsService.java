package de.tum.cit.aet.artemis.service.connectors.athena;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.domain.TextExercise;
import de.tum.cit.aet.artemis.domain.TextSubmission;
import de.tum.cit.aet.artemis.domain.modeling.ModelingExercise;
import de.tum.cit.aet.artemis.domain.modeling.ModelingSubmission;
import de.tum.cit.aet.artemis.exception.NetworkingException;
import de.tum.cit.aet.artemis.service.dto.athena.ExerciseBaseDTO;
import de.tum.cit.aet.artemis.service.dto.athena.ModelingFeedbackDTO;
import de.tum.cit.aet.artemis.service.dto.athena.ProgrammingFeedbackDTO;
import de.tum.cit.aet.artemis.service.dto.athena.SubmissionBaseDTO;
import de.tum.cit.aet.artemis.service.dto.athena.TextFeedbackDTO;
import de.tum.cit.aet.artemis.web.rest.errors.ConflictException;

/**
 * Service for receiving feedback suggestions from the Athena service.
 * Assumes that submissions and already given feedback have already been sent to Athena or that the feedback is non-graded.
 */
@Service
@Profile("athena")
public class AthenaFeedbackSuggestionsService {

    private static final Logger log = LoggerFactory.getLogger(AthenaFeedbackSuggestionsService.class);

    private final AthenaConnector<RequestDTO, ResponseDTOText> textAthenaConnector;

    private final AthenaConnector<RequestDTO, ResponseDTOProgramming> programmingAthenaConnector;

    private final AthenaConnector<RequestDTO, ResponseDTOModeling> modelingAthenaConnector;

    private final AthenaModuleService athenaModuleService;

    private final AthenaDTOConverterService athenaDTOConverterService;

    /**
     * Create a new AthenaFeedbackSuggestionsService to receive feedback suggestions from the Athena service.
     *
     * @param athenaRestTemplate        REST template used for the communication with Athena
     * @param athenaModuleService       Athena module serviced used to determine the urls for different modules
     * @param athenaDTOConverterService Service to convert exr
     */
    public AthenaFeedbackSuggestionsService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, AthenaModuleService athenaModuleService,
            AthenaDTOConverterService athenaDTOConverterService) {
        textAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOText.class);
        programmingAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOProgramming.class);
        modelingAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOModeling.class);
        this.athenaDTOConverterService = athenaDTOConverterService;
        this.athenaModuleService = athenaModuleService;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record RequestDTO(ExerciseBaseDTO exercise, SubmissionBaseDTO submission, boolean isGraded) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record ResponseDTOText(List<TextFeedbackDTO> data) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record ResponseDTOProgramming(List<ProgrammingFeedbackDTO> data) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record ResponseDTOModeling(List<ModelingFeedbackDTO> data) {
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
        return response.data;
    }
}
