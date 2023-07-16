package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.List;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.service.dto.athena.TextExerciseDTO;

/**
 * Service for selecting the "best" submission to assess right now using Athena, e.g. by the highest information gain.
 * Assumes that submissions have already been sent to Athena (it only sends submission IDs to choose from).
 * The default choice if Athena does not respond is to choose a random submission.
 */
@Service
@Profile("athena")
public class AthenaSubmissionSelectionService {

    // pretty short timeout, because this should be fast, and it's not too bad if it fails
    private static final int REQUEST_TIMEOUT_MS = 1000;

    private final Logger log = LoggerFactory.getLogger(AthenaSubmissionSelectionService.class);

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    private final AthenaConnector<RequestDTO, ResponseDTO> connector;

    private static class RequestDTO {

        public TextExerciseDTO exercise;

        public List<Long> submissionIds; // Athena just needs submission IDs => quicker request, because less data is sent

        RequestDTO(@NotNull TextExercise exercise, @NotNull List<Long> submissionIds) {
            this.exercise = TextExerciseDTO.of(exercise);
            this.submissionIds = submissionIds;
        }
    }

    private record ResponseDTO(long data // will contain the submission ID to choose, or -1 if no submission was explicitly chosen
    ) {
    }

    /**
     * Create a new AthenaSubmissionSelectionService, which uses a custom timeout for requests to Athena
     */
    public AthenaSubmissionSelectionService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate) {
        // Configure rest template to use the given timeout
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(REQUEST_TIMEOUT_MS);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT_MS);
        athenaRestTemplate.setRequestFactory(requestFactory);
        // Create connector
        connector = new AthenaConnector<>(log, athenaRestTemplate, ResponseDTO.class);
    }

    /**
     * Fetches the proposedTextSubmission for a given exercise from Athena
     *
     * @param exercise    the exercise to get the proposed Submission for
     * @param submissions assessable submissions of the exercise
     * @return a Submission suggested by the Athena submission selector (e.g. chosen by the highest information gain)
     * @throws IllegalArgumentException if exercise isn't automatically assessable
     */
    public Optional<TextSubmission> getProposedSubmission(TextExercise exercise, List<TextSubmission> submissions) {
        if (!exercise.isFeedbackSuggestionsEnabled()) {
            throw new IllegalArgumentException("The Exercise does not have feedback suggestions enabled.");
        }
        if (submissions.isEmpty()) {
            return Optional.empty();
        }

        log.debug("Start Athena Submission Selection Service for Text Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        log.info("Calling Remote Service to calculate next proposed submissions for {} submissions.", submissions.size());

        try {
            final RequestDTO request = new RequestDTO(exercise, submissions.stream().mapToLong(TextSubmission::getId).boxed().toList());
            // TODO: make module selection dynamic (based on exercise)
            // allow no retries because this should be fast and it's not too bad if it fails
            ResponseDTO response = connector.invokeWithRetry(athenaUrl + "/modules/text/module_text_cofee/select_submission", request, 0);
            log.info("Remote Service to calculate next proposes submissions responded: {}", response.data);
            long submissionId = response.data;
            if (submissionId == -1) {
                return Optional.empty();
            }
            return submissions.stream().filter(s -> s.getId() == submissionId).findFirst();
        }
        catch (NetworkingError networkingError) {
            log.error("Error while calling Remote Service: {}", networkingError.getMessage());
        }

        return Optional.empty();
    }
}
