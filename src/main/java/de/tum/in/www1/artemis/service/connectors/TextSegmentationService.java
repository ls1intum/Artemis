package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.service.connectors.RemoteArtemisServiceConnector.authenticationHeaderForSecret;

import java.util.*;
import java.util.stream.Collectors;

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

        Request(List<TextSubmission> submissions) {
            this.submissions = submissions;
        }
    }

    private static class Response {

        // keywords can be used in future to calculate text blocks for a single submission
        public List<String> keywords;

        public List<TextBlock> textBlocks;

    }
    // endregion

    @Value("${artemis.automatic-text.segmentation-url}")
    private String API_ENDPOINT;

    @Value("${artemis.automatic-text.secret}")
    private String API_SECRET;

    private RemoteArtemisServiceConnector<Request, Response> connector = new RemoteArtemisServiceConnector<>(log, Response.class);

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

        Map<String, TextSubmission> submissionsMap = submissions.stream().collect(Collectors.toMap(submission -> submission.getId().toString(), item -> item));

        // for each textBlock, take the corresponding TextSubmission and add the text blocks.
        // The addBlocks method also sets the submission in the textBlock
        response.textBlocks.forEach(textBlock -> submissionsMap.get(textBlock.getId()).addBlocks(textBlock));

        // compute correct ID for each textBlock
        response.textBlocks.forEach(textBlock -> textBlock.computeId());

        response.textBlocks.forEach(textBlock -> textBlock.setText(textBlock.getSubmission().getText().substring(textBlock.getStartIndex(), textBlock.getEndIndex())));

        return response.textBlocks;
    }

}
