package de.tum.in.www1.artemis.versioning;

import static de.tum.in.www1.artemis.config.VersioningConfiguration.API_VERSIONS;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.servlet.mvc.condition.*;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.in.www1.artemis.exception.ApiVersionAnnotationMismatchException;

/**
 * This class is responsible for integrating the API versions into the request mapping.
 */
public class VersionRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private final String pathPrefixSegment;

    private final String versionPrefix;

    private static final int LATEST_VERSION = API_VERSIONS.get(API_VERSIONS.size() - 1);

    /**
     * Creates a new VersionRequestMappingHandlerMapping.
     *
     * @param pathPrefixSegment The full path segment on the beginning of endpoint path
     * @param versionPrefix     The prefix for the version segment
     */
    public VersionRequestMappingHandlerMapping(String pathPrefixSegment, String versionPrefix) {
        this.pathPrefixSegment = pathPrefixSegment;
        this.versionPrefix = versionPrefix;
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
        if (method.getAnnotation(IgnoreGlobalMapping.class) != null) {
            return info;
        }
        VersionRanges versionRangesAnnotation = AnnotationUtils.findAnnotation(method, VersionRanges.class);
        VersionRange versionRangeAnnotation = AnnotationUtils.findAnnotation(method, VersionRange.class);
        if (versionRangesAnnotation != null && versionRangeAnnotation != null) {
            throw new ApiVersionAnnotationMismatchException();
        }
        if (versionRangesAnnotation != null) {
            return createApiVersionInfo(new VersionRangesRequestCondition(versionRangesAnnotation.value())).combine(info);
        }
        if (versionRangeAnnotation != null) {
            return createApiVersionInfo(new VersionRangesRequestCondition(versionRangeAnnotation)).combine(info);
        }

        // No version is defined. We assume the endpoint is available in all versions.
        return createApiVersionInfo(new VersionRangesRequestCondition()).combine(info);
    }

    /**
     * Create the request mapping info for the given version ranges.
     *
     * @param customCondition The condition containing the version ranges
     * @return The request mapping info
     */
    private RequestMappingInfo createApiVersionInfo(VersionRangesRequestCondition customCondition) {
        List<String> patterns = customCondition.getApplicableVersions().stream().map(e -> "/" + pathPrefixSegment + "/" + versionPrefix + e).collect(Collectors.toList());

        // Add an endpoint without version as a fallback to the latest version
        if (customCondition.getApplicableVersions().contains(LATEST_VERSION)) {
            patterns.add("/" + pathPrefixSegment);
        }
        return RequestMappingInfo.paths(patterns.toArray(new String[] {})).customCondition(customCondition).build();
    }
}
