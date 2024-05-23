package de.tum.in.www1.artemis.versioning;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import de.tum.in.www1.artemis.exception.ApiVersionAnnotationMismatchException;

/**
 * This class is responsible for integrating the API versions into the request mapping.
 */
public class VersionRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private final String pathPrefixSegment;

    private final String versionPrefix;

    private final List<Integer> apiVersions;

    private final int latestVersion;

    private final RequestMappingInfo nonVersionedApiInfo;

    /**
     * Creates a new VersionRequestMappingHandlerMapping.
     *
     * @param pathPrefixSegment The full path segment on the beginning of endpoint path
     * @param versionPrefix     The prefix for the version segment
     */
    public VersionRequestMappingHandlerMapping(List<Integer> apiVersions, String pathPrefixSegment, String versionPrefix) {
        this.apiVersions = apiVersions;
        this.latestVersion = this.apiVersions.getLast();
        this.pathPrefixSegment = pathPrefixSegment;
        this.versionPrefix = versionPrefix;
        this.nonVersionedApiInfo = createNonVersionedApiInfo();
    }

    /**
     * This method is called by the Spring framework to get the mapping for a specific endpoint method containing all versions.
     *
     * @param method      the method to provide a mapping for
     * @param handlerType the handler type, not relevant to us
     * @return the mapping for the method
     */
    @Override
    protected RequestMappingInfo getMappingForMethod(@NotNull Method method, @NotNull Class<?> handlerType) {
        RequestMappingInfo info = super.getMappingForMethod(method, handlerType);
        if (info == null) {
            return null;
        }
        // We don't handle endpoints we didn't create
        if (!method.getDeclaringClass().getPackageName().contains("de.tum.in.www1.artemis")) {
            return info;
        }
        // We don't handle endpoints that are explicitly ignored
        var ignoreGlobalMappingAnnotation = method.getAnnotation(IgnoreGlobalMapping.class);
        if (ignoreGlobalMappingAnnotation != null && ignoreGlobalMappingAnnotation.ignorePaths()) {
            return info;
        }
        var useVersioningMethodAnnotation = method.getAnnotation(UseVersioning.class);
        var useVersioningTypeAnnotation = method.getDeclaringClass().getAnnotation(UseVersioning.class);
        // We don't handle endpoints that are not explicitly versioned as of now. See the annotation for more information.
        // TODO: Remove this check once all endpoints are versioned
        if (useVersioningMethodAnnotation == null && useVersioningTypeAnnotation == null) {
            return nonVersionedApiInfo.combine(info);
        }
        VersionRanges versionRangesAnnotation = AnnotationUtils.findAnnotation(method, VersionRanges.class);
        VersionRange versionRangeAnnotation = AnnotationUtils.findAnnotation(method, VersionRange.class);
        if (versionRangesAnnotation != null && versionRangeAnnotation != null) {
            throw new ApiVersionAnnotationMismatchException();
        }
        if (versionRangesAnnotation != null) {
            return createApiVersionInfo(new VersionRangesRequestCondition(apiVersions, versionRangesAnnotation.value())).combine(info);
        }
        if (versionRangeAnnotation != null) {
            return createApiVersionInfo(new VersionRangesRequestCondition(apiVersions, versionRangeAnnotation)).combine(info);
        }

        // No version is defined. We assume the endpoint is available in all versions.
        return createApiVersionInfo(new VersionRangesRequestCondition(apiVersions)).combine(info);
    }

    /**
     * Create the request mapping info for the given version ranges.
     *
     * @param customCondition The condition containing the version ranges
     * @return The request mapping info
     */
    private RequestMappingInfo createApiVersionInfo(VersionRangesRequestCondition customCondition) {
        List<String> patterns = customCondition.getApplicableVersions().stream().map(e -> "/" + pathPrefixSegment + "/" + versionPrefix + e)
                .collect(Collectors.toCollection(ArrayList::new));

        // Add an endpoint without version as a fallback to the latest version
        if (customCondition.getApplicableVersions().contains(latestVersion)) {
            patterns.add("/" + pathPrefixSegment);
        }
        var builderConfiguration = new RequestMappingInfo.BuilderConfiguration();
        builderConfiguration.setPatternParser(new PathPatternParser());
        return RequestMappingInfo.paths(patterns.toArray(new String[] {})).options(builderConfiguration).customCondition(customCondition).build();
    }

    private RequestMappingInfo createNonVersionedApiInfo() {
        var builderConfiguration = new RequestMappingInfo.BuilderConfiguration();
        builderConfiguration.setPatternParser(new PathPatternParser());
        return RequestMappingInfo.paths("/" + pathPrefixSegment).options(builderConfiguration).build();
    }
}
