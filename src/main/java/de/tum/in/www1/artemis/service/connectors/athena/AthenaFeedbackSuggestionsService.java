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
 * Assumes that submissions and already given feedback have already been sent to Athena.
 */
@Service
@Profile("athena")
public class AthenaFeedbackSuggestionsService {

    private static final Logger log = LoggerFactory.getLogger(AthenaFeedbackSuggestionsService.class);

    private final AthenaConnector<RequestDTO, ResponseDTOText> textAthenaConnector;

    private final AthenaConnector<RequestDTO, ResponseDTOProgramming> programmingAthenaConnector;

    private final AthenaModuleUrlHelper athenaModuleUrlHelper;

    private final AthenaDTOConverter athenaDTOConverter;

    /**
     * Creates a new AthenaFeedbackSuggestionsService to receive feedback suggestions from the Athena service.
     */
    public AthenaFeedbackSuggestionsService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, AthenaModuleUrlHelper athenaModuleUrlHelper,
            AthenaDTOConverter athenaDTOConverter) {
        textAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOText.class);
        programmingAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOProgramming.class);
        this.athenaDTOConverter = athenaDTOConverter;
        this.athenaModuleUrlHelper = athenaModuleUrlHelper;
    }

    private record RequestDTO(ExerciseDTO exercise, SubmissionDTO submission) {
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
     * @return a list of feedback suggestions
     */
    public List<TextFeedbackDTO> getTextFeedbackSuggestions(TextExercise exercise, TextSubmission submission) throws NetworkingException {
        log.debug("Start Athena Feedback Suggestions Service for Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        if (!Objects.equals(submission.getParticipation().getExercise().getId(), exercise.getId())) {
            log.error("Exercise id {} does not match submission's exercise id {}", exercise.getId(), submission.getParticipation().getExercise().getId());
            throw new ConflictException("Exercise id " + exercise.getId() + " does not match submission's exercise id " + submission.getParticipation().getExercise().getId(),
                    "Exercise", "exerciseIdDoesNotMatch");
        }

        final RequestDTO request = new RequestDTO(athenaDTOConverter.ofExercise(exercise), athenaDTOConverter.ofSubmission(exercise.getId(), submission));
        ResponseDTOText response = textAthenaConnector.invokeWithRetry(athenaModuleUrlHelper.getAthenaModuleUrl(exercise.getExerciseType()) + "/feedback_suggestions", request, 0);
        log.info("Athena responded to feedback suggestions request: {}", response.data);
        return response.data.stream().toList();
    }

    /**
     * Calls the remote Athena service to get feedback suggestions for a given programming submission.
     *
     * @param exercise   the {@link ProgrammingExercise} the suggestions are fetched for
     * @param submission the {@link ProgrammingSubmission} the suggestions are fetched for
     * @return a list of feedback suggestions
     */
    public List<ProgrammingFeedbackDTO> getProgrammingFeedbackSuggestions(ProgrammingExercise exercise, ProgrammingSubmission submission) throws NetworkingException {
        log.debug("Start Athena Feedback Suggestions Service for Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        final RequestDTO request = new RequestDTO(athenaDTOConverter.ofExercise(exercise), athenaDTOConverter.ofSubmission(exercise.getId(), submission));
        ResponseDTOProgramming response = programmingAthenaConnector.invokeWithRetry(athenaModuleUrlHelper.getAthenaModuleUrl(exercise.getExerciseType()) + "/feedback_suggestions",
                request, 0);
        log.info("Athena responded to feedback suggestions request: {}", response.data);
        return response.data.stream().toList();
    }
}
