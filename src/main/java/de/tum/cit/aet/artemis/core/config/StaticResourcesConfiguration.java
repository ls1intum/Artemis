package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Profile(PROFILE_CORE)
@Configuration
public class StaticResourcesConfiguration {

    @Bean
    public WebMvcConfigurer webMvcConfigurer(IndexHtmlTransformer transformer) {
        return new WebMvcConfigurer() {

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {

                registry.addResourceHandler("/*.html").addResourceLocations("classpath:/static/").resourceChain(true).addTransformer(transformer);
            }
        };
    }

    @Bean
    public IndexHtmlTransformer transformer(Environment environment) {
        return new IndexHtmlTransformer(environment);
    }
}
