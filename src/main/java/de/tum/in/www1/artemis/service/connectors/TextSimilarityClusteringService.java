package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.service.connectors.RemoteArtemisServiceConnector.authenticationHeaderForSecret;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.text.TextCluster;
import de.tum.in.www1.artemis.domain.text.TextEmbedding;
import de.tum.in.www1.artemis.domain.text.TextTreeNode;
import de.tum.in.www1.artemis.exception.NetworkingError;

@Service
@Profile("automaticText")
public class TextSimilarityClusteringService {

    private final Logger log = LoggerFactory.getLogger(TextSimilarityClusteringService.class);

    // region Request/Response DTOs
    public static class Request {

        public List<TextEmbedding> embeddings;

        Request(List<TextEmbedding> embeddings) {
            this.embeddings = embeddings;
        }
    }

    public static class Response {

        public LinkedHashMap<Integer, TextCluster> clusters;

        public List<List<Double>> distanceMatrix;

        public List<TextTreeNode> clusterTree;

    }
    // endregion

    @Value("${artemis.automatic-text.clustering-url}")
    private String API_ENDPOINT;

    @Value("${artemis.automatic-text.secret}")
    private String API_SECRET;

    private RemoteArtemisServiceConnector<Request, Response> connector = new RemoteArtemisServiceConnector<>(log, Response.class);

    public Response clusterTextBlocks(List<TextEmbedding> embeddings) throws NetworkingError {
        return clusterTextBlocks(embeddings, 1);
    }

    /**
     * Calls the remote Clusteringservice to cluster TextBlocks based on their TextEmbeddings
     *
     * @param embeddings the Embeddings corresponding to the TextBlocks, that should be clustered
     * @param maxRetries number of retries before the request will be canceled
     * @return a Map of ClusterIDs and Clusters
     * @throws NetworkingError if the request isn't successful
     */
    public Response clusterTextBlocks(List<TextEmbedding> embeddings, int maxRetries) throws NetworkingError {
        log.info("Calling Remote Service to cluster student text answers.");
        final Request request = new Request(embeddings);
        return connector.invokeWithRetry(API_ENDPOINT, request, authenticationHeaderForSecret(API_SECRET), maxRetries);
    }

}
