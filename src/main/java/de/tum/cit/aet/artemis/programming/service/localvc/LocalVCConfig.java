package de.tum.cit.aet.artemis.programming.service.localvc;

import java.net.MalformedURLException;
import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LocalVCConfig {

    @Value("${artemis.version-control.url}")
    private String localVCBaseUrlString;

    @Bean
    public URL localVCBaseUrl() throws MalformedURLException {
        return new URL(localVCBaseUrlString);
    }
}
