package de.tum.cit.aet.artemis.core.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class NebulaRestClientConfiguration {

    @Bean
    @Qualifier("nebulaRestClient")
    public RestClient nebulaRestClient(RestClient.Builder builder) {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();

        RequestConfig config = RequestConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(60000)).setResponseTimeout(Timeout.ofMilliseconds(1800000)).build();

        CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(config).build();

        requestFactory.setHttpClient(httpClient);

        return builder.requestFactory(requestFactory).build();
    }

}
