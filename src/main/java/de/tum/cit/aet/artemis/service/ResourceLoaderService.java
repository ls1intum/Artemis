package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;

/**
 * Service class to load resources from the file system (if possible) and the classpath (as fallback).
 */
@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Service
public class ResourceLoaderService {

    private static final Logger log = LoggerFactory.getLogger(ResourceLoaderService.class);

    private static final String ALL_PATHS_ANT_PATTERN = "**";

    @Value("${artemis.template-path:#{null}}")
    private Optional<Path> templateFileSystemPath;

    private final ResourcePatternResolver resourceLoader;

    /**
     * Files that start with a prefix that is included in this list can be overwritten from the file system
     */
    private static final List<Path> ALLOWED_OVERRIDE_PREFIXES = List.of(Path.of("templates"));

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
            final String resourceLocation = getFileSystemResourceLocation(path);
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
     * Only relative paths are allowed. Does not return directories.
     *
     * @param basePath A relative path pattern to a resource.
     * @return The resources located by the specified pathPattern.
     */
    @NotNull
    public Resource[] getFileResources(final Path basePath) {
        return getFileResources(basePath, ALL_PATHS_ANT_PATTERN);
    }

    /**
     * Loads the resources from the specified path pattern.
     * <p>
     * Only relative paths are allowed. Does not return directories.
     * <p>
     * Examples for path patterns: {@code *.sh}, {@code base/**}. Use forward slashes to separate path parts.
     *
     * @param basePath A relative path pattern to a resource.
     * @param pattern  A pattern that limits which files in the directory of the base path are matched.
     * @return The resources located by the specified pathPattern.
     */
    @NotNull
    public Resource[] getFileResources(final Path basePath, final String pattern) {
        checkValidPathElseThrow(basePath);

        Resource[] resources = null;

        // Try to load from filesystem if override is allowed for pathPattern
        if (isOverrideAllowed(basePath)) {
            final String resourceLocation = getFileSystemResourceLocation(basePath, pattern);
            try {
                resources = getFileResources(resourceLocation);
            }
            catch (IOException e) {
                log.debug("Could not load resources '{}' from filesystem.", resourceLocation, e);
            }
        }

        // If loading from filesystem is not allowed or was not successful, load from classpath
        if (resources == null || resources.length == 0) {
            final String resourceLocation = getClassPathResourceLocation(basePath, pattern);
            try {
                resources = getFileResources(resourceLocation);
            }
            catch (IOException e) {
                log.debug("Could not load resources '{}' from classpath.", resourceLocation, e);
            }
        }

        return Objects.requireNonNullElseGet(resources, () -> new Resource[0]);
    }

    /**
     * Loads non-directory resources from the specified patterns.
     * <p>
     * Each resource can be read via {@link Resource#getInputStream()}.
     *
     * @param locationPattern The resource pattern passed to the resource loader.
     * @return The resources with readable content located by the specified locationPattern.
     * @throws IOException in case of I/O errors
     */
    private Resource[] getFileResources(final String locationPattern) throws IOException {
        final var fileAndDirectoryResources = resourceLoader.getResources(locationPattern);

        return Arrays.stream(fileAndDirectoryResources).filter(Resource::isReadable).toArray(Resource[]::new);
    }

    private void checkValidPathElseThrow(final Path path) {
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("Cannot load resources from absolute paths!");
        }
    }

    private String getFileSystemResourceLocation(final Path resourcePath) {
        return "file:" + resolveResourcePath(resourcePath).toString();
    }

    private String getFileSystemResourceLocation(final Path resourcePath, final String pathPattern) {
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
