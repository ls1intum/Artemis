package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.service.dto.athena.*;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

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

    private final AthenaModuleService athenaModuleService;

    private final AthenaDTOConverterService athenaDTOConverterService;

    /**
     * Creates a new AthenaFeedbackSuggestionsService to receive feedback suggestions from the Athena service.
     */
    public AthenaFeedbackSuggestionsService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, AthenaModuleService athenaModuleService,
            AthenaDTOConverterService athenaDTOConverterService) {
        textAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOText.class);
        programmingAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOProgramming.class);
        this.athenaDTOConverterService = athenaDTOConverterService;
        this.athenaModuleService = athenaModuleService;
    }

    private record RequestDTO(ExerciseDTO exercise, SubmissionDTO submission, boolean isGraded) {
    }

    private record ResponseDTOText(List<TextFeedbackDTO> data) {
    }

    private record ResponseDTOProgramming(List<ProgrammingFeedbackDTO> data) {
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
}
