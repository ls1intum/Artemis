package de.tum.in.www1.artemis.config;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import tech.jhipster.config.JHipsterProperties;

@Configuration
public class PublicResourcesConfiguration implements WebMvcConfigurer {

    private final JHipsterProperties jhipsterProperties;

    public PublicResourcesConfiguration(JHipsterProperties jHipsterProperties) {
        this.jhipsterProperties = jHipsterProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Static assets, accessible without authorization.
        // Allowed locations are under $artemisRunDir/public/** and resource/public/**
        final var userDir = System.getProperty("user.dir");
        final var publicFiles = Paths.get(userDir, "public");
        final var maxAge = jhipsterProperties.getHttp().getCache().getTimeToLiveInDays();
        registry.addResourceHandler("/public/**").addResourceLocations("file:" + publicFiles + "/", "classpath:public/")
                .setCacheControl(CacheControl.maxAge(maxAge, TimeUnit.DAYS));
        ;
    }
}
