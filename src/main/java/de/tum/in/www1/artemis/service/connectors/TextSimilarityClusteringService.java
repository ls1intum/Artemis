package de.tum.in.www1.artemis.service.connectors;

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

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;

@Service
@Profile("automaticText")
public class TextSimilarityClusteringService {

    private final Logger log = LoggerFactory.getLogger(TextSimilarityClusteringService.class);

    // region Request/Response DTOs
    private static class ClusteringRequest {

        public Set<TextBlock> blocks;

        ClusteringRequest(Set<TextBlock> blocks) {
            this.blocks = blocks;
        }
    }

    private static class ClusterResponse {

        public LinkedHashMap<Integer, TextCluster> clusters;

    }
    // endregion

    // region Exceptions
    public class NetworkingError extends Exception {

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

    public Map<Integer, TextCluster> clusterTextBlocks(Set<TextBlock> blocks) throws NetworkingError {
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

    public Map<Integer, TextCluster> clusterTextBlocks(Set<TextBlock> blocks, int maxRetries) throws NetworkingError {
        for (int retries = 0;; retries++) {
            try {
                return clusterTextBlocks(blocks);
            }
            catch (NetworkingError error) {
                if (retries >= maxRetries) {
                    throw error;
                }
            }
        }
    }

    private HttpHeaders authenticationHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(API_SECRET);
        return headers;
    }

}
