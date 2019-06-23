package de.tum.in.www1.artemis.service.connectors;

import java.util.Dictionary;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.config.SentryConfiguration;

@Service
@Profile("automaticText")
public class TextSimilarityClusteringService {

    // region Entities
    class Cluster {

        double[][] distanceMatrix;

        double[] probabilities;

        TextBlock[] blocks;
    }

    class TextBlock {

        String id;

        String text;
    }
    // endregion

    // region Request/Response DTOs
    private class ClusteringRequest {

        List<TextBlock> blocks;

        ClusteringRequest(List<TextBlock> blocks) {
            this.blocks = blocks;
        }
    }

    private class ClusterResponse {

        Dictionary<Integer, Cluster> clusters;
    }
    // endregion

    // region Exceptions
    class NetworkingError extends Exception {

        NetworkingError(String message) {
            super(message);
        }
    }
    // endregion

    @Value("${automaticText.endpoint}")
    private String API_ENDPOINT;

    @Value("${automaticText.secret}")
    private String API_SECRET;

    private RestTemplate restTemplate = new RestTemplate();

    private final Logger log = LoggerFactory.getLogger(SentryConfiguration.class);

    public Dictionary<Integer, Cluster> clusterTextBlocks(List<TextBlock> blocks) throws NetworkingError {
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
