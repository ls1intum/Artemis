package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.service.connectors.RemoteArtemisServiceConnector.authenticationHeaderForSecret;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.exception.NetworkingError;

@Service
@Profile("automaticText")
public class TextSegmentationService {

    private final Logger log = LoggerFactory.getLogger(TextSegmentationService.class);

    // region Request/Response DTOs
    private static class Request {

        public List<TextSubmission> submissions;

        Request(@NotNull List<TextSubmission> submissions) {
            this.submissions = submissionDTOs(submissions);
        }

        /**
         * Create new TextSubmission as DTO.
         */
        @NotNull
        private List<TextSubmission> submissionDTOs(@NotNull List<TextSubmission> submissions) {
            return submissions.stream().map(textSubmission -> {
                final TextSubmission submission = new TextSubmission();
                submission.setText(textSubmission.getText());
                submission.setId(textSubmission.getId());
                return submission;
            }).collect(toList());
        }
    }

    private static class Response {

        // keywords can be used in future to calculate text blocks for a single submission
        public List<String> keywords;

        // TODO: textBlock.id is used to reference the submission id in the response of the segmentation service.
        // This should be refactored to be called submissionId, to not confuse this with the text block id, computed with textBlock.computeId().
        public List<TextBlock> textBlocks;

    }
    // endregion

    @Value("${artemis.automatic-text.segmentation-url}")
    private String API_ENDPOINT;

    @Value("${artemis.automatic-text.secret}")
    private String API_SECRET;

    private RemoteArtemisServiceConnector<Request, Response> connector = new RemoteArtemisServiceConnector<>(log, Response.class);

    /**
     * Calls the remote text segmentation service to segment a List of Submissions into TextBlocks
     * @param submissions a List of Submissions which should be segmented into TextBlocks
     * @return a List of textBlocks corresponding the submissions
     * @throws NetworkingError if the request isn't successful
     */
    public List<TextBlock> segmentSubmissions(List<TextSubmission> submissions) throws NetworkingError {
        return segmentSubmissions(submissions, 1);
    }

    /**
     * Calls the remote text segmentation service to segment a List of Submissions into TextBlocks
     * @param submissions a List of Submissions which should be segmented into TextBlocks
     * @param maxRetries number of retries before the request will be canceled
     * @return a List of textBlocks corresponding the submissions
     * @throws NetworkingError if the request isn't successful
     */
    public List<TextBlock> segmentSubmissions(List<TextSubmission> submissions, int maxRetries) throws NetworkingError {
        log.info("Calling Remote Service to segment " + submissions.size() + " submissions into text blocks.");

        final Request request = new Request(submissions);
        Response response = connector.invokeWithRetry(API_ENDPOINT, request, authenticationHeaderForSecret(API_SECRET), maxRetries);

        Map<String, TextSubmission> submissionsMap = submissions.stream()
                .collect(toMap(/* Key: */ (submission -> submission.getId().toString()), /* Value: */ (submission -> submission)));

        for (TextBlock textBlock : response.textBlocks) {
            // take the corresponding TextSubmission and add the text blocks.
            // The addBlocks method also sets the submission in the textBlock
            submissionsMap.get(textBlock.getId()).addBlock(textBlock);

            // compute correct ID for each textBlock
            textBlock.computeId();

            // Use start/end index to compute text of segment for easy access.
            textBlock.setText(textBlock.getSubmission().getText().substring(textBlock.getStartIndex(), textBlock.getEndIndex()));
        }

        log.info("Segmentation finished. " + response.textBlocks.size() + " TextBlocks calculated.");

        return response.textBlocks;
    }

}
