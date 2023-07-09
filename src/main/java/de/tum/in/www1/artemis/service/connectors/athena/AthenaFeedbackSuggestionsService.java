package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.service.dto.athena.TextExerciseDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextFeedbackDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextSubmissionDTO;

@Service
@Profile("athena")
public class AthenaFeedbackSuggestionsService {

    private final Logger log = LoggerFactory.getLogger(AthenaFeedbackSuggestionsService.class);

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    private final AthenaConnector<RequestDTO, ResponseDTO> connector;

    public AthenaFeedbackSuggestionsService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate) {
        connector = new AthenaConnector<>(log, athenaRestTemplate, ResponseDTO.class);
    }

    private static class RequestDTO {

        public TextExerciseDTO exercise;

        public TextSubmissionDTO submission;

        RequestDTO(@NotNull TextExercise exercise, @NotNull TextSubmission submission) {
            this.exercise = TextExerciseDTO.of(exercise);
            this.submission = TextSubmissionDTO.of(exercise.getId(), submission);
        }
    }

    private static class ResponseDTO {

        public List<TextFeedbackDTO> data;
    }

    /**
     * Calls the remote Athena service to get feedback suggestions for a given submission.
     *
     * @param exercise   the exercise the suggestions are fetched for
     * @param submission the submission the suggestions are fetched for
     */
    public List<TextBlockRef> getFeedbackSuggestions(TextExercise exercise, TextSubmission submission) throws NetworkingError {
        log.debug("Start Athena Feedback Suggestions Service for Text Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        log.info("Calling Remote Service with exercise and submission.");

        try {
            final RequestDTO request = new RequestDTO(exercise, submission);
            // TODO: make module selection dynamic (based on exercise)
            ResponseDTO response = connector.invokeWithRetry(athenaUrl + "/modules/text/module_text_cofee/feedback_suggestions", request, 0);
            log.info("Remote Service responded to feedback suggestions request: {}", response.data);
            return response.data.stream().map((feedbackDTO) -> feedbackDTO.toTextBlockRef(submission)).toList();
        }
        catch (NetworkingError networkingError) {
            log.error("Error while calling Remote Service: {}", networkingError.getMessage());
            throw networkingError;
        }
    }

}
