package de.tum.in.www1.artemis.config;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * Configures the available API versions and the versioned OpenAPI/Swagger documentation.
 */
@Configuration
public class VersioningConfiguration implements BeanDefinitionRegistryPostProcessor {

    /**
     * Defines the list of existing versions. Extend this if new version is created.
     *
     * @return List of supported versions
     */
    @Bean
    public List<Integer> apiVersions() {
        return List.of(1);
    }

    /**
     * Specifies all basic information for api documentation
     *
     * @return OpenAPI object holding the information
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Artemis API").description("API for the Interactive Learning Platform Artemis")
                        .license(new License().name("MIT").url("https://github.com/ls1intum/Artemis/blob/develop/LICENSE")))
                .externalDocs(new ExternalDocumentation().description("Artemis Documentation").url("https://docs.artemis.ase.in.tum.de/"));
    }

    /**
     * Programmatically registers all API versions to the OpenAPI documentation
     *
     * @param beanFactory the bean factory used by the application context
     */
    @Override
    public void postProcessBeanFactory(@NotNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        for (var version : apiVersions()) {
            var docket = createSwaggerInfo(version);
            beanFactory.registerSingleton("v" + version, docket);
        }
    }

    /**
     * Builds one documentation object for a version by filtering the paths for the correct version
     *
     * @param version the version to be documented
     * @return the documentation object
     */
    private GroupedOpenApi createSwaggerInfo(int version) {
        return GroupedOpenApi.builder().group("v" + version).pathsToMatch("/api/v" + version + "/**")
                .addOpenApiCustomiser(openApi -> openApi.info(openApi.getInfo().version("v" + version))).build();
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@NotNull BeanDefinitionRegistry registry) throws BeansException {
        // Intentionally empty
    }
}
