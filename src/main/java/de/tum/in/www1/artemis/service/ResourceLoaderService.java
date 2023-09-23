package de.tum.in.www1.artemis.service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.apache.commons.io.FileUtils;
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

    private static final String ALL_FILES_GLOB = "**" + File.separator + "*.*";

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
     * Recursively loads the resources from the specified directory.
     * <p>
     * Only relative paths are allowed.
     *
     * @param basePath A relative path pattern to a resource.
     * @return The resources located by the specified pathPattern.
     */
    @Nonnull
    public Resource[] getResources(final Path basePath) {
        return getResources(basePath, ALL_FILES_GLOB);
    }

    /**
     * Loads the resources from the specified path pattern.
     * <p>
     * Only relative paths are allowed.
     * <p>
     * Examples for path patterns: {@code *.sh}, {@code base/**}. Use forward slashes to separate path parts.
     *
     * @param basePath A relative path pattern to a resource.
     * @param pattern  A pattern that limits which files in the directory of the base path are matched.
     * @return The resources located by the specified pathPattern.
     */
    @Nonnull
    public Resource[] getResources(final Path basePath, final String pattern) {
        checkValidPathElseThrow(basePath);

        Resource[] resources = null;

        // Try to load from filesystem if override is allowed for pathPattern
        if (isOverrideAllowed(basePath)) {
            final String resourceLocation = getFileResourceLocation(basePath, pattern);
            try {
                resources = resourceLoader.getResources(resourceLocation);
            }
            catch (IOException e) {
                log.debug("Could not load resources '{}' from filesystem.", resourceLocation, e);
            }
        }

        // If loading from filesystem is not allowed or was not successful, load from classpath
        if (resources == null || resources.length == 0) {
            final String resourceLocation = getClassPathResourceLocation(basePath, pattern);
            try {
                resources = resourceLoader.getResources(resourceLocation);
            }
            catch (IOException e) {
                log.debug("Could not load resources '{}' from classpath.", resourceLocation, e);
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

    private String getFileResourceLocation(final Path resourcePath, final String pathPattern) {
        final String systemPathPattern = File.separator + adaptPathPatternToSystem(pathPattern);
        return "file:" + resolveResourcePath(resourcePath).toString() + systemPathPattern;
    }

    private String getClassPathResourceLocation(final Path resourcePath) {
        return "classpath:/" + ensureUnixPath(resourcePath.toString());
    }

    private String getClassPathResourceLocation(final Path resourcePath, final String pathPattern) {
        return "classpath:/" + ensureUnixPath(resourcePath.toString() + "/" + pathPattern);
    }

    private String adaptPathPatternToSystem(final String pathPattern) {
        if ("/".equals(File.separator)) {
            return ensureUnixPath(pathPattern);
        }
        else {
            return pathPattern.replace("/", "\\");
        }
    }

    private String ensureUnixPath(final String pathPattern) {
        return pathPattern.replace("\\", "/");
    }

    private Path resolveResourcePath(final Path resource) {
        return templateFileSystemPath.map(path -> path.resolve(resource)).orElse(resource);
    }

    private boolean isOverrideAllowed(final Path path) {
        return ALLOWED_OVERRIDE_PREFIXES.stream().anyMatch(path::startsWith);
    }

    /**
     * Get the path to a file in the 'resources' folder.
     * If the file is in the file system, the path to the file is returned.
     * If the file is in a jar file, the file is extracted to a temporary file and the path to the temporary file is returned.
     *
     * @param path the path to the file in the 'resources' folder.
     * @return the path to the file in the file system or in the jar file.
     */
    public Path getResourceFilePath(Path path) throws IOException, URISyntaxException {

        Resource resource = getResource(path);

        if (!resource.exists()) {
            throw new IOException("Resource does not exist: " + path);
        }

        URL resourceUrl = resource.getURL();

        if ("file".equals(resourceUrl.getProtocol())) {
            // Resource is in the file system.
            return Paths.get(resourceUrl.toURI());
        }
        else if ("jar".equals(resourceUrl.getProtocol())) {
            // Resource is in a jar file.
            Path resourcePath = Files.createTempFile(UUID.randomUUID().toString(), "");
            File file = resourcePath.toFile();
            file.deleteOnExit();
            FileUtils.copyInputStreamToFile(resource.getInputStream(), file);
            return resourcePath;
        }
        throw new IllegalArgumentException("Unsupported protocol: " + resourceUrl.getProtocol());
    }
}
