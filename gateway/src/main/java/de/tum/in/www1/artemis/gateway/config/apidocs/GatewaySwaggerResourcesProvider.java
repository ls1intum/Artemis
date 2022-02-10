package de.tum.in.www1.artemis.gateway.config.apidocs;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import reactor.core.scheduler.Schedulers;
import springfox.documentation.swagger.web.SwaggerResource;
import springfox.documentation.swagger.web.SwaggerResourcesProvider;
import tech.jhipster.config.JHipsterConstants;

/**
 * Retrieves all registered microservices Swagger resources.
 */
@Component
@Primary
@Profile(JHipsterConstants.SPRING_PROFILE_API_DOCS)
@Configuration
public class GatewaySwaggerResourcesProvider implements SwaggerResourcesProvider {

    @Value("${eureka.instance.appname:gateway}")
    private String gatewayName;

    private final RouteLocator routeLocator;

    public GatewaySwaggerResourcesProvider(RouteLocator routeLocator) {
        this.routeLocator = routeLocator;
    }

    @Override
    public List<SwaggerResource> get() {
        List<SwaggerResource> swaggerResources = new ArrayList<>();

        swaggerResources.add(swaggerResource(gatewayName.concat(" (default)"), "/v3/api-docs"));
        swaggerResources.add(swaggerResource(gatewayName.concat(" (management)"), "/v3/api-docs?group=management"));

        List<String> microservices = routeLocator.getRoutes().map(this::getMicroserviceName).collectList().defaultIfEmpty(Collections.emptyList())
                .subscribeOn(Schedulers.boundedElastic()).toFuture().orTimeout(10, TimeUnit.SECONDS).join();
        microservices.stream().filter(this::isNotGateway).forEach(microservice -> swaggerResources.add(swaggerResource(microservice, getMicroserviceApiDocs(microservice))));
        return swaggerResources;
    }

    /**
     * Create a new Swagger Resource
     *
     * @param name
     * @param location
     * @return the created swagger resource
     */
    public static SwaggerResource swaggerResource(String name, String location) {
        SwaggerResource swaggerResource = new SwaggerResource();
        swaggerResource.setName(name);
        swaggerResource.setLocation(location);
        swaggerResource.setSwaggerVersion("3.0");
        return swaggerResource;
    }

    private boolean isNotGateway(String name) {
        return !name.equalsIgnoreCase(gatewayName);
    }

    private String getMicroserviceApiDocs(String name) {
        return "/services/".concat(name).concat("/v3/api-docs");
    }

    private String getMicroserviceName(Route route) {
        return route.getUri().toString().replace("lb://", "").toLowerCase();
    }
}
