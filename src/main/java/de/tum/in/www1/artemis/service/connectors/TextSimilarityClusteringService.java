package de.tum.in.www1.artemis.service.connectors;

import java.io.IOException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Profile("automaticText")
public class TextSimilarityClusteringService {

    private final Logger log = LoggerFactory.getLogger(TextSimilarityClusteringService.class);

    // region Entities
    static class Cluster {

        public double[][] distanceMatrix;

        public double[] probabilities;

        public TextBlock[] blocks;
    }

    static class TextBlock {

        public String id;

        public String text;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TextBlock) {
                TextBlock other = (TextBlock) obj;
                return Objects.equals(id, other.id) && Objects.equals(text, other.text);
            }
            return false;
        }
    }
    // endregion

    // region Request/Response DTOs
    private static class ClusteringRequest {

        public List<TextBlock> blocks;

        ClusteringRequest(List<TextBlock> blocks) {
            this.blocks = blocks;
        }
    }

    private static class ClusterResponse {

        public LinkedHashMap<Integer, Cluster> clusters;

    }
    // endregion

    // region Exceptions
    class NetworkingError extends Exception {

        NetworkingError(String message) {
            super(message);
        }
    }
    // endregion

    @Value("${artemis.automatic-text.url}")
    private String API_ENDPOINT;

    @Value("${artemis.automatic-text.secret}")
    private String API_SECRET;

    private RestTemplate restTemplate = new RestTemplate();

    public Map<Integer, Cluster> clusterTextBlocks(List<TextBlock> blocks) throws NetworkingError, IOException {
        long start = System.currentTimeMillis();
        log.debug("Calling Remote Service to cluster student text answers.");

        final ClusteringRequest clusteringRequest = new ClusteringRequest(blocks);
        final HttpEntity<ClusteringRequest> request = new HttpEntity<>(clusteringRequest, authenticationHeader());

        final ResponseEntity<ClusterResponse> response = restTemplate.postForEntity(API_ENDPOINT, request, ClusterResponse.class);

        if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
            throw new NetworkingError("An Error occurred while clustering student answers. Check Remote Logs for debugging information.");
        }

        final ClusterResponse clusterResponse = response.getBody();
        assert clusterResponse != null;
        log.info("Finished clustering remote call in " + (System.currentTimeMillis() - start) + "ms");

        return clusterResponse.clusters;
    }

    private HttpHeaders authenticationHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(API_SECRET);
        return headers;
    }

}
