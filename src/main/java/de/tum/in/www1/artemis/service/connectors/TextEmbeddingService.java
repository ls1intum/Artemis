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
import de.tum.in.www1.artemis.exception.NetworkingError;

@Service
@Profile("automaticText")
public class TextEmbeddingService {

    private final Logger log = LoggerFactory.getLogger(TextEmbeddingService.class);

    // region Request/Response DTOs
    private static class Request {

        public List<TextBlock> blocks;

        Request(List<TextBlock> blocks) {
            this.blocks = blocks;
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

    public List<TextEmbedding> embedTextBlocks(List<TextBlock> blocks) throws NetworkingError {
        return embedTextBlocks(blocks, 1);
    }

    public List<TextEmbedding> embedTextBlocks(List<TextBlock> blocks, int maxRetries) throws NetworkingError {
        final Request request = new Request(blocks);
        final Response response = connector.invokeWithRetry(API_ENDPOINT, request, authenticationHeaderForSecret(API_SECRET), maxRetries);

        return response.embeddings;
    }

}
