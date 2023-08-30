package de.tum.in.www1.artemis.service.connectors.athena;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.TextSubmissionService;
import de.tum.in.www1.artemis.service.dto.athena.TextExerciseDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextSubmissionDTO;

/**
 * Service for sending submissions to the Athena service for further processing
 * so that Athena can later give feedback suggestions on new submissions.
 */
@Service
@Profile("athena")
public class AthenaSubmissionSendingService {

    private static final int SUBMISSIONS_PER_REQUEST = 100;

    private final Logger log = LoggerFactory.getLogger(AthenaSubmissionSendingService.class);

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    private final TextSubmissionRepository textSubmissionRepository;

    private final AthenaConnector<RequestDTO, ResponseDTO> connector;

    /**
     * Creates a new AthenaSubmissionSendingService.
     *
     * @param athenaRestTemplate       the RestTemplate to use for requests to the Athena service
     * @param textSubmissionRepository the repository to use for finding submissions in the database
     */
    public AthenaSubmissionSendingService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, TextSubmissionRepository textSubmissionRepository) {
        this.textSubmissionRepository = textSubmissionRepository;
        connector = new AthenaConnector<>(athenaRestTemplate, ResponseDTO.class);
    }

    private static class RequestDTO {

        public TextExerciseDTO exercise;

        public List<TextSubmissionDTO> submissions;

        RequestDTO(@NotNull TextExercise exercise, @NotNull Set<TextSubmission> submissions) {
            this.exercise = TextExerciseDTO.of(exercise);
            this.submissions = submissions.stream().map(submission -> TextSubmissionDTO.of(exercise.getId(), submission)).toList();
        }
    }

    private record ResponseDTO(String data) {
    }

    /**
     * Calls the remote Athena service to submit a job for calculating automatic feedback
     *
     * @param exercise the exercise the automatic assessments should be calculated for
     */
    public void sendSubmissions(TextExercise exercise) {
        sendSubmissions(exercise, 1);
    }

    /**
     * Calls the remote Athena service to submit a Job for calculating automatic feedback
     *
     * @see TextSubmissionService :getTextSubmissionsByExerciseId` for selection of Submissions.
     *
     * @param exercise   the exercise the automatic assessments should be calculated for
     * @param maxRetries number of retries before the request will be canceled
     */
    public void sendSubmissions(TextExercise exercise, int maxRetries) {
        if (!exercise.isFeedbackSuggestionsEnabled()) {
            throw new IllegalArgumentException("The Exercise does not have feedback suggestions enabled.");
        }

        log.debug("Start Athena Submission Sending Service for Text Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        // Find all text submissions for exercise (later we will support others)
        Pageable pageRequest = PageRequest.of(0, SUBMISSIONS_PER_REQUEST);
        while (true) {
            Page<TextSubmission> textSubmissions = textSubmissionRepository.findByParticipation_ExerciseIdAndSubmittedIsTrue(exercise.getId(), pageRequest);
            sendSubmissions(exercise, textSubmissions.toSet(), maxRetries);
            if (textSubmissions.isLast()) {
                break;
            }
            pageRequest = pageRequest.next();
        }
    }

    /**
     * Calls the remote Athena service to submit a Job for calculating automatic feedback
     *
     * @param exercise        the exercise the automatic assessments should be calculated for
     * @param textSubmissions the submissions to send
     * @param maxRetries      number of retries before the request will be canceled
     */
    public void sendSubmissions(TextExercise exercise, Set<TextSubmission> textSubmissions, int maxRetries) {
        Set<TextSubmission> filteredTextSubmissions = new HashSet<>(textSubmissions);

        // filter submissions with an open participation (because of individual due dates)
        filteredTextSubmissions.removeIf(submission -> {
            var individualDueDate = submission.getParticipation().getIndividualDueDate();
            return individualDueDate != null && individualDueDate.isAfter(ZonedDateTime.now());
        });

        if (filteredTextSubmissions.isEmpty()) {
            log.info("No text submissions found to send (total: {}, filtered: 0)", textSubmissions.size());
            return;
        }

        log.info("Calling Athena to calculate automatic feedback for {} submissions.", textSubmissions.size());

        try {
            final RequestDTO request = new RequestDTO(exercise, filteredTextSubmissions);
            // TODO: make module selection dynamic (based on exercise)
            ResponseDTO response = connector.invokeWithRetry(athenaUrl + "/modules/text/module_text_cofee/submissions", request, maxRetries);
            log.info("Athena (calculating automatic feedback) responded: {}", response.data);
        }
        catch (NetworkingException error) {
            log.error("Error while calling Athena", error);
        }
    }

}
