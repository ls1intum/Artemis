package de.tum.in.www1.artemis.config;

import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PublicResourcesConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Static assets, accessible without authorization.
        // Allowed locations are under $artemisRunDir/public/** and resource/public/**
        final var userDir = System.getProperty("user.dir");
        final var publicFiles = Paths.get(userDir, "public");
        registry.addResourceHandler("/public/**").addResourceLocations("file:" + publicFiles + "/", "classpath:public/");
    }
}
