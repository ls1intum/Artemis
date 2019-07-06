package de.tum.in.www1.artemis.service.connectors;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.net.ssl.SSLContext;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.TextCluster;

public class TextSimilarityClusteringServiceTest {

    @Ignore
    @Test
    public void clusterTextBlocks() throws TextSimilarityClusteringService.NetworkingError, IOException {
        final TextSimilarityClusteringService service = new TextSimilarityClusteringService();
        ReflectionTestUtils.setField(service, "API_ENDPOINT", "https://tac.ase.in.tum.de/cluster");
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate());

        final Set<TextBlock> blocks = Stream.of("foo", "bar").map(text -> new TextBlock().text(text)).peek(block -> block.setId((long) (Math.random() * 1000)))
                .collect(Collectors.toSet());

        final Map<Integer, TextCluster> clusterDictionary = service.clusterTextBlocks(blocks);

        assertThat(clusterDictionary.keySet(), hasSize(1));
        assertThat(clusterDictionary.keySet(), hasItem(-1));
        final TextCluster cluster = clusterDictionary.get(-1);
        final Set<TextBlock> blocks1 = cluster.getBlocks();

        assertThat(blocks1.toArray(), is(equalTo(blocks.toArray())));

        final double[][] distanceMatrix = cluster.getDistanceMatrix();
        assertThat(distanceMatrix[0][1], is(equalTo(distanceMatrix[1][0])));
        assertThat((int) (distanceMatrix[0][1] * 10), is(equalTo(6)));
    }

    // TODO: Remove once HTTPs is configures with valid certificate
    private RestTemplate restTemplate() {
        TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
        SSLContext sslContext = null;
        try {
            sslContext = org.apache.http.ssl.SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
        }
        catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            e.printStackTrace();
        }
        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
        CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(csf).build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);

        return new RestTemplate(requestFactory);
    }
}
