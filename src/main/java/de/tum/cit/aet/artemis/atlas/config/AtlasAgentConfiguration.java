package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration class for Atlas Agent with Azure OpenAI integration.
 */
@Profile("atlas-agent")
@Configuration
@AtlasAgentEnabled
@Lazy
public class AtlasAgentConfiguration {

    @Value("${artemis.atlas.agent.azure.api-key}")
    private String azureApiKey;

    @Value("${artemis.atlas.agent.azure.endpoint}")
    private String azureEndpoint;

    @Value("${artemis.atlas.agent.azure.api-version}")
    private String azureApiVersion;

    /**
     * REST template configured for Azure OpenAI API calls.
     *
     * @return a configured RestTemplate for Atlas Agent
     */
    @Bean("atlasAgentRestTemplate")
    @Lazy
    public RestTemplate atlasAgentRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);
        requestFactory.setReadTimeout(30000);
        restTemplate.setRequestFactory(requestFactory);

        return restTemplate;
    }

    public String getAzureApiKey() {
        return azureApiKey;
    }

    public String getAzureEndpoint() {
        return azureEndpoint;
    }

    public String getAzureApiVersion() {
        return azureApiVersion;
    }
}
