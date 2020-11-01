package de.tum.in.www1.artemis;

import java.io.IOException;
import java.util.Optional;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;

/**
 * Service class to load resources from the file system (if possible) and the classpath (as fallback).
 */
@Service
public class ResourceLoaderService {

    private final ResourceLoader resourceLoader;

    @Value("artemis.template-path")
    private Optional<String> templateFileSystemPath;

    public ResourceLoaderService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Load the resource from the specified path. The path MUST NOT start with a '/', it is appended automatically if needed.
     * File will be loaded from the relative path, if it exists, from the classpath otherwise.
     * @param path the path to load the file from. Must not start with a '/'.
     * @return the loaded resource, which might not exist ({@link Resource#exists()}.
     */
    public Resource getResource(String path) {
        Resource resource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource("file:" + getTemplateFileSystemPath() + path);
        if (!resource.exists()) {
            resource = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResource("classpath:/" + path);
        }

        return resource;
    }

    /**
     * Load the resource from the specified path.
     * File will be loaded from the relative path, if it exists, from the classpath otherwise.
     * @param pathSegments the segments of the path (e.g. ["templates", "java", "pom.xml"]). Will automatically be joined with '/'.
     * @return the loaded resource, which might not exist ({@link Resource#exists()}.
     */
    public Resource getResource(String... pathSegments) {
        return getResource(StringUtils.join(pathSegments, "/"));
    }

    /**
     * Load the resources from the specified path. The path MUST NOT start with a '/', it is appended automatically if needed.
     * Files will be loaded from the relative path, it is non-empty (at least one resource), from the classpath otherwise.
     * @param path the path to load the file from. Must not start with a '/'.
     * @return the loaded resources, which might be null
     */
    public Resource[] getResources(String path) {
        Resource[] resources = null;
        try {
            resources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources("file:" + getTemplateFileSystemPath() + path);
        }
        catch (IOException ignored) {
        }

        if (resources == null || resources.length == 0) {
            try {
                resources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources("classpath:/" + path);
            }
            catch (IOException ignored) {
            }
        }

        return resources;
    }

    /**
     * Load the resources from the specified path.
     * Files will be loaded from the relative path, it is non-empty (at least one resource), from the classpath otherwise.
     * @param pathSegments the segments of the path (e.g. ["templates", "java"]). Will automatically be joined with '/'.
     * @return the loaded resources, which might be null
     */
    public Resource[] getResources(String... pathSegments) {
        return getResources(StringUtils.join(pathSegments, "/"));
    }

    /**
     * Return the file system path were templates are stored.
     * If no template path is defined, the current directory where Artemis was started from is used (e.g. the `templates` folder next to the Artemis.war file).
     * If a template path is defined, it is used.
     * @return the template system path if defined (with a trailing '/') or "" if is not set
     */
    private String getTemplateFileSystemPath() {
        if (templateFileSystemPath.isEmpty()) {
            return "";
        }

        if (templateFileSystemPath.get().endsWith("/")) {
            return templateFileSystemPath.get();
        }
        else {
            return templateFileSystemPath.get() + "/";
        }
    }
}
