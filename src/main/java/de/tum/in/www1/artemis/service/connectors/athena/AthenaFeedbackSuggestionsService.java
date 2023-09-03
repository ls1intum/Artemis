package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.service.dto.athena.ProgrammingFeedbackDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextFeedbackDTO;

/**
 * Service for receiving feedback suggestions from the Athena service.
 * Assumes that submissions and already given feedback have already been sent to Athena.
 */
@Service
@Profile("athena")
public class AthenaFeedbackSuggestionsService {

    private final Logger log = LoggerFactory.getLogger(AthenaFeedbackSuggestionsService.class);

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    private final AthenaConnector<RequestDTO, ResponseDTOText> textAthenaConnector;

    private final AthenaConnector<RequestDTO, ResponseDTOProgramming> programmingAthenaConnector;

    private final AthenaDTOConverter athenaDTOConverter;

    /**
     * Creates a new AthenaFeedbackSuggestionsService to receive feedback suggestions from the Athena service.
     */
    public AthenaFeedbackSuggestionsService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, AthenaDTOConverter athenaDTOConverter) {
        textAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOText.class);
        programmingAthenaConnector = new AthenaConnector<>(athenaRestTemplate, ResponseDTOProgramming.class);
        this.athenaDTOConverter = athenaDTOConverter;
    }

    private record RequestDTO(Object exercise, Object submission) {
    }

    private record ResponseDTOText(List<TextFeedbackDTO> data) {
    }

    private record ResponseDTOProgramming(List<ProgrammingFeedbackDTO> data) {
    }

    /**
     * Calls the remote Athena service to get feedback suggestions for a given submission.
     *
     * @param exercise   the exercise the suggestions are fetched for
     * @param submission the submission the suggestions are fetched for
     * @return a list of feedback suggestions
     */
    public List<TextFeedbackDTO> getTextFeedbackSuggestions(Exercise exercise, Submission submission) throws NetworkingException {
        log.debug("Start Athena Feedback Suggestions Service for Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        try {
            final RequestDTO request = new RequestDTO(athenaDTOConverter.ofExercise(exercise), athenaDTOConverter.ofSubmission(exercise.getId(), submission));
            // TODO: make module selection dynamic (based on exercise)
            ResponseDTOText response = textAthenaConnector.invokeWithRetry(athenaUrl + "/modules/text/module_text_cofee/feedback_suggestions", request, 0);
            log.info("Athena responded to feedback suggestions request: {}", response.data);
            return response.data.stream().toList();
        }
        catch (NetworkingException error) {
            log.error("Error while calling Athena", error);
            throw error;
        }
    }

    /**
     * Calls the remote Athena service to get feedback suggestions for a given submission.
     *
     * @param exercise   the exercise the suggestions are fetched for
     * @param submission the submission the suggestions are fetched for
     * @return a list of feedback suggestions
     */
    public List<ProgrammingFeedbackDTO> getProgrammingFeedbackSuggestions(Exercise exercise, Submission submission) throws NetworkingException {
        log.debug("Start Athena Feedback Suggestions Service for Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        try {
            final RequestDTO request = new RequestDTO(athenaDTOConverter.ofExercise(exercise), athenaDTOConverter.ofSubmission(exercise.getId(), submission));
            // TODO: make module selection dynamic (based on exercise)
            ResponseDTOProgramming response = programmingAthenaConnector.invokeWithRetry(athenaUrl + "/modules/text/module_text_cofee/feedback_suggestions", request, 0);
            log.info("Athena responded to feedback suggestions request: {}", response.data);
            return response.data.stream().toList();
        }
        catch (NetworkingException error) {
            log.error("Error while calling Athena", error);
            throw error;
        }
    }
}
