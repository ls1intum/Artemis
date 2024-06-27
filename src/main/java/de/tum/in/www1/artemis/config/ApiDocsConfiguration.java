package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_OPENAPI;
import static de.tum.in.www1.artemis.config.VersioningConfiguration.API_VERSIONS;

import java.util.List;

import org.springdoc.core.customizers.SpringDocCustomizers;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.SpringDocProviders;
import org.springdoc.core.service.AbstractRequestService;
import org.springdoc.core.service.GenericResponseService;
import org.springdoc.core.service.OpenAPIService;
import org.springdoc.core.service.OperationService;
import org.springdoc.webmvc.api.MultipleOpenApiWebMvcResource;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * Configures the available API versions and the versioned OpenAPI/Swagger documentation.
 */
@Configuration
@Profile({ PROFILE_OPENAPI })
public class ApiDocsConfiguration {

    @Bean
    @Lazy(false)
    MultipleOpenApiWebMvcResource multipleOpenApiResource(List<GroupedOpenApi> groupedOpenApis, ObjectFactory<OpenAPIService> defaultOpenAPIBuilder,
            AbstractRequestService requestBuilder, GenericResponseService responseBuilder, OperationService operationParser, SpringDocConfigProperties springDocConfigProperties,
            SpringDocProviders springDocProviders, SpringDocCustomizers springDocCustomizers) {
        return new MultipleOpenApiWebMvcResource(groupedOpenApis, defaultOpenAPIBuilder, requestBuilder, responseBuilder, operationParser, springDocConfigProperties,
                springDocProviders, springDocCustomizers);
    }

    public ApiDocsConfiguration(ConfigurableListableBeanFactory beanFactory) {
        for (var version : API_VERSIONS) {
            var docket = createSwaggerInfo(version);
            beanFactory.registerSingleton("v" + version, docket);
        }
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
     * Builds one documentation object for a version by filtering the paths for the correct version
     *
     * @param version the version to be documented
     * @return the documentation object
     */
    private GroupedOpenApi createSwaggerInfo(int version) {
        return GroupedOpenApi.builder().group("v" + version).pathsToMatch("/api/v" + version + "/complaints")
                .addOpenApiCustomizer(openApi -> openApi.info(openApi.getInfo().version("v" + version))).build();
    }
}
