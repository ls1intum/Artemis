package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;

/**
 * Service class to load resources from the file system (if possible) and the classpath (as fallback).
 */
@Service
public class ResourceLoaderService {

    private static final Logger log = LoggerFactory.getLogger(ResourceLoaderService.class);

    @Value("${artemis.template-path:#{null}}")
    private Optional<Path> templateFileSystemPath;

    private final ResourcePatternResolver resourceLoader;

    /**
     * Files that start with a prefix that is included in this list can be overwritten from the file system
     */
    private static final List<Path> ALLOWED_OVERRIDE_PREFIXES = List.of(Path.of("templates", "jenkins"));

    public ResourceLoaderService(ResourceLoader resourceLoader) {
        this.resourceLoader = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
    }

    /**
     * Loads the resource from the specified path.
     * <p>
     * Only relative paths are allowed.
     *
     * @param path A relative path to a resource.
     * @return The resource located by the specified path.
     */
    public Resource getResource(final Path path) {
        checkValidPathElseThrow(path);

        Resource resource = null;

        // Try to load from filesystem if override is allowed for path
        if (isOverrideAllowed(path)) {
            final String resourceLocation = getFileResourceLocation(path);
            resource = resourceLoader.getResource(resourceLocation);
        }

        // If loading from filesystem is not allowed or was not successful, load from classpath
        if (resource == null || !resource.exists()) {
            final String resourceLocation = getClassPathResourceLocation(path);
            resource = resourceLoader.getResource(resourceLocation);
        }

        return resource;
    }

    /**
     * Loads the resources from the specified path pattern.
     * <p>
     * Only relative paths are allowed.
     * <p>
     * Examples for path patterns: {@code some/path/*.sh}, {@code base/**}.
     *
     * @param pathPattern A relative path pattern to a resource.
     * @return The resources located by the specified pathPattern.
     */
    @Nonnull
    public Resource[] getResources(final Path pathPattern) {
        checkValidPathElseThrow(pathPattern);

        Resource[] resources = null;

        // Try to load from filesystem if override is allowed for pathPattern
        if (isOverrideAllowed(pathPattern)) {
            final String resourceLocation = getFileResourceLocation(pathPattern);
            try {
                resources = resourceLoader.getResources(resourceLocation);
            }
            catch (IOException e) {
                log.warn("Could not load resources '{}' from filesystem.", resourceLocation, e);
            }
        }

        // If loading from filesystem is not allowed or was not successful, load from classpath
        if (resources == null || resources.length == 0) {
            final String resourceLocation = getClassPathResourceLocation(pathPattern);
            try {
                resources = resourceLoader.getResources(resourceLocation);
            }
            catch (IOException e) {
                log.warn("Could not load resources '{}' from classpath.", resourceLocation, e);
            }
        }

        return Objects.requireNonNullElseGet(resources, () -> new Resource[0]);
    }

    private void checkValidPathElseThrow(final Path path) {
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("Cannot load resources from absolute paths!");
        }
    }

    private String getFileResourceLocation(final Path resourcePath) {
        return "file:" + resolveResourcePath(resourcePath).toString();
    }

    private String getClassPathResourceLocation(final Path resourcePath) {
        return "classpath:/" + resourcePath.toString();
    }

    private Path resolveResourcePath(final Path resource) {
        return templateFileSystemPath.map(path -> path.resolve(resource)).orElse(resource);
    }

    private boolean isOverrideAllowed(final Path path) {
        return ALLOWED_OVERRIDE_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
