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

import de.tum.in.www1.artemis.domain.GradingInstruction;
import de.tum.in.www1.artemis.domain.TextBlockRef;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.repository.GradingInstructionRepository;
import de.tum.in.www1.artemis.service.dto.athena.TextExerciseDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextFeedbackDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextSubmissionDTO;

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

    private final AthenaConnector<RequestDTO, ResponseDTO> connector;

    private final GradingInstructionRepository gradingInstructionRepository;

    /**
     * Creates a new AthenaFeedbackSuggestionsService to receive feedback suggestions from the Athena service.
     */
    public AthenaFeedbackSuggestionsService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, GradingInstructionRepository gradingInstructionRepository) {
        connector = new AthenaConnector<>(athenaRestTemplate, ResponseDTO.class);
        this.gradingInstructionRepository = gradingInstructionRepository;
    }

    private static class RequestDTO {

        public TextExerciseDTO exercise;

        public TextSubmissionDTO submission;

        RequestDTO(@NotNull TextExercise exercise, @NotNull TextSubmission submission) {
            this.exercise = TextExerciseDTO.of(exercise);
            this.submission = TextSubmissionDTO.of(exercise.getId(), submission);
        }
    }

    private record ResponseDTO(List<TextFeedbackDTO> data) {
    }

    /**
     * Calls the remote Athena service to get feedback suggestions for a given submission.
     *
     * @param exercise   the exercise the suggestions are fetched for
     * @param submission the submission the suggestions are fetched for
     * @return a list of feedback suggestions
     */
    public List<TextBlockRef> getFeedbackSuggestions(TextExercise exercise, TextSubmission submission) throws NetworkingException {
        log.debug("Start Athena Feedback Suggestions Service for Text Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        log.info("Calling Athena with exercise and submission.");

        try {
            final RequestDTO request = new RequestDTO(exercise, submission);
            // TODO: make module selection dynamic (based on exercise)
            ResponseDTO response = connector.invokeWithRetry(athenaUrl + "/modules/text/module_text_cofee/feedback_suggestions", request, 0);
            log.info("Athena responded to feedback suggestions request: {}", response.data);
            return response.data.stream().map((feedbackDTO) -> {
                GradingInstruction gradingInstruction = null;
                if (feedbackDTO.gradingInstructionId() != null) {
                    gradingInstruction = gradingInstructionRepository.findById(feedbackDTO.gradingInstructionId()).orElse(null);
                }
                var ref = feedbackDTO.toTextBlockRef(submission, gradingInstruction);
                ref.block().automatic();
                ref.feedback().setType(FeedbackType.AUTOMATIC);
                // Add IDs to connect block and ID
                ref.block().computeId();
                ref.feedback().setReference(ref.block().getId());
                return ref;
            }).toList();
        }
        catch (NetworkingException error) {
            log.error("Error while calling Athena", error);
            throw error;
        }
    }

}
