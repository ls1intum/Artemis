package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.service.connectors.RemoteArtemisServiceConnector.authenticationHeaderForSecret;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextEmbedding;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.exception.NetworkingError;

@Service
@Profile("automaticText")
public class TextEmbeddingService {

    private final Logger log = LoggerFactory.getLogger(TextEmbeddingService.class);

    // region Request/Response DTOs
    private static class Request {

        public List<TextBlock> blocks;

        public Long courseId;

        public Long exerciseId;

        Request(List<TextBlock> blocks, Long courseId, long exerciseId) {
            this.blocks = blocks;
            this.courseId = courseId;
            this.exerciseId = exerciseId;
        }
    }

    private static class Response {

        public List<TextEmbedding> embeddings;
    }
    // endregion

    @Value("${artemis.automatic-text.embedding-url}")
    private String API_ENDPOINT;

    @Value("${artemis.automatic-text.secret}")
    private String API_SECRET;

    private RemoteArtemisServiceConnector<Request, Response> connector = new RemoteArtemisServiceConnector<>(log, Response.class);

    /**
     * Calls the remote embedding service to embedd a List of textBlocks
     * @param blocks a List of TextBlocks which should be embedded
     * @param exercise the exercise from which the text blocks are extracted
     * @return a List of TextEmbedding corresponding to the given TextBlocks
     * @throws NetworkingError if the request isn't successful
     */
    public List<TextEmbedding> embedTextBlocks(List<TextBlock> blocks, TextExercise exercise) throws NetworkingError {
        return embedTextBlocks(blocks, exercise, 1);
    }

    /**
     * Calls the remote embedding service to embedd a List of textBlocks
     * @param blocks a List of TextBlocks which should be embedded
     * @param exercise the exercise from which the text blocks are extracted
     * @param maxRetries number of retries before the request will be canceled
     * @return a List of TextEmbedding corresponding to the given TextBlocks
     * @throws NetworkingError if the request isn't successful
     */
    public List<TextEmbedding> embedTextBlocks(List<TextBlock> blocks, TextExercise exercise, int maxRetries) throws NetworkingError {
        log.info("Calling Remote Service to embed " + blocks.size() + " student text answer blocks.");
        final Request request = new Request(blocks, exercise.getCourseViaExerciseGroupOrCourseMember().getId(), exercise.getId());
        final Response response = connector.invokeWithRetry(API_ENDPOINT, request, authenticationHeaderForSecret(API_SECRET), maxRetries);

        return response.embeddings;
    }

}
