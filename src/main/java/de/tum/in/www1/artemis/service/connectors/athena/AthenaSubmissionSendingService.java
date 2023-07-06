package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.*;

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
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.service.dto.athena.TextExerciseDTO;
import de.tum.in.www1.artemis.service.dto.athena.TextSubmissionDTO;

@Service
@Profile("athena")
public class AthenaSubmissionSendingService {

    private final Logger log = LoggerFactory.getLogger(AthenaSubmissionSendingService.class);

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    private final TextSubmissionRepository textSubmissionRepository;

    private final AthenaConnector<RequestDTO, ResponseDTO> connector;

    public AthenaSubmissionSendingService(TextSubmissionRepository textSubmissionRepository, @Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate) {
        this.textSubmissionRepository = textSubmissionRepository;
        connector = new AthenaConnector<>(log, athenaRestTemplate, ResponseDTO.class);
    }

    private static class RequestDTO {

        public TextExerciseDTO exercise;

        public List<TextSubmissionDTO> submissions;

        RequestDTO(@NotNull TextExercise exercise, @NotNull List<TextSubmission> submissions) {
            this.exercise = TextExerciseDTO.of(exercise);
            this.submissions = submissions.stream().map(submission -> TextSubmissionDTO.of(submission, exercise.getId())).toList();
        }
    }

    private static class ResponseDTO {

        public String data;
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
     * Note: See `TextSubmissionService:getTextSubmissionsByExerciseId` for selection of Submissions.
     *
     * @param exercise   the exercise the automatic assessments should be calculated for
     * @param maxRetries number of retries before the request will be canceled
     */
    public void sendSubmissions(TextExercise exercise, int maxRetries) {
        log.debug("Start Athena Service for Text Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        // Find all text submissions for exercise (later we will support others)
        List<TextSubmission> textSubmissions = textSubmissionRepository.getTextSubmissionsWithTextBlocksByExerciseId(exercise.getId());

        log.info("Calling Remote Service to calculate automatic feedback for {} submissions.", textSubmissions.size());

        try {
            final RequestDTO request = new RequestDTO(exercise, textSubmissions);
            // TODO: make module selection dynamic (based on exercise)
            ResponseDTO response = connector.invokeWithRetry(athenaUrl + "/modules/text/module_text_cofee/submissions", request, maxRetries);
            log.info("Remote Service to calculate automatic feedback responded: {}", response.data);
        }
        catch (NetworkingError networkingError) {
            log.error("Error while calling Remote Service: {}", networkingError.getMessage());
        }
    }

}
