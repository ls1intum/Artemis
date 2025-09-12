package de.tum.cit.aet.artemis.atlas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration class for Atlas Agent with Azure OpenAI integration.
 */
@Configuration
@AtlasAgentEnabled
@Lazy
public class AtlasAgentConfiguration {

    @Value("${artemis.azure.openai.api-key}")
    private String azureApiKey;

    @Value("${artemis.azure.openai.endpoint}")
    private String azureEndpoint;

    private static final String AZURE_API_VERSION = "2025-01-01-preview";

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
        return AZURE_API_VERSION;
    }

}
