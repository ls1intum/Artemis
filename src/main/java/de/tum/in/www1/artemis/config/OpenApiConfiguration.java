package de.tum.in.www1.artemis.config;

import static springfox.documentation.builders.PathSelectors.regex;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Predicate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import io.github.jhipster.config.JHipsterConstants;
import io.github.jhipster.config.JHipsterProperties;
import io.github.jhipster.config.apidoc.customizer.SwaggerCustomizer;

@Configuration
@Profile(JHipsterConstants.SPRING_PROFILE_SWAGGER)
public class OpenApiConfiguration {

    @Bean
    public SwaggerCustomizer noApiFirstCustomizer() {
        return docket -> docket.select().apis(Predicate.not(RequestHandlerSelectors.basePackage("de.tum.in.www1.artemis.web.api")));
    }

    @Bean
    public Docket apiFirstDocket(JHipsterProperties jHipsterProperties) {
        JHipsterProperties.Swagger properties = jHipsterProperties.getSwagger();
        Contact contact = new Contact(properties.getContactName(), properties.getContactUrl(), properties.getContactEmail());

        ApiInfo apiInfo = new ApiInfo("API First " + properties.getTitle(), properties.getDescription(), properties.getVersion(), properties.getTermsOfServiceUrl(), contact,
                properties.getLicense(), properties.getLicenseUrl(), new ArrayList<>());

        return new Docket(DocumentationType.OAS_30).groupName("openapi").host(properties.getHost()).protocols(new HashSet<>(Arrays.asList(properties.getProtocols())))
                .apiInfo(apiInfo).useDefaultResponseMessages(properties.isUseDefaultResponseMessages()).forCodeGeneration(true)
                .directModelSubstitute(ByteBuffer.class, String.class).genericModelSubstitutes(ResponseEntity.class).ignoredParameterTypes(Pageable.class).select()
                .apis(RequestHandlerSelectors.basePackage("de.tum.in.www1.artemis.web.api")).paths(regex(properties.getDefaultIncludePattern())).build();
    }

}
