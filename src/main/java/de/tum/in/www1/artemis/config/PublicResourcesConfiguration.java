package de.tum.in.www1.artemis.config;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import tech.jhipster.config.JHipsterProperties;

/**
 * Configures serving of static resources from /public from both the resources folder and the host file system.
 */
@Configuration
public class PublicResourcesConfiguration implements WebMvcConfigurer {

    @Autowired
    private JHipsterProperties jHipsterProperties;

    @Override
    public void addResourceHandlers(@NotNull ResourceHandlerRegistry registry) {
        // Enable static resource serving in general from "/public" from both classpath and hosts filesystem
        addResourceHandlerForPath(registry);

        /*
         * Add caching for about us images / contributor images and for emojis. Contributor images are unlikely to change. Emojis must be cached for them to be displayed quickly,
         * see #5186. All other files will not be cached, especially files in /content/* as they might change with any PR.
         */

        var defaultCacheControl = CacheControl.maxAge(jHipsterProperties.getHttp().getCache().getTimeToLiveInDays(), TimeUnit.DAYS).cachePublic();

        addResourceHandlerForPath(registry, "images", "about").setCacheControl(defaultCacheControl);
        addResourceHandlerForPath(registry, "emoji").setCacheControl(defaultCacheControl);
    }

    /**
     * Adds a resource handler for a sub path of /public and returns the registration object for further modification
     *
     * @param registry the spring registry to use
     * @param subPaths the subpaths to register
     * @return the registration for further modification
     */
    private static ResourceHandlerRegistration addResourceHandlerForPath(ResourceHandlerRegistry registry, String... subPaths) {
        return registry.addResourceHandler(getResourceHandlerLocationForSubPaths(subPaths)).addResourceLocations(getFileSystemPublicSubPathResourceLocation(subPaths),
                getClasspathPublicSubPathLocation(subPaths));
    }

    /**
     * Create a resource location pattern including the given subpaths of /public
     *
     * @param subPaths the sub paths to use
     * @return the resource location as string
     */
    private static String getResourceHandlerLocationForSubPaths(String... subPaths) {
        return "/public%s/**".formatted(subPaths.length == 0 ? "" : "/" + String.join("/", subPaths));
    }

    /**
     * Create a class path URI including the given subpaths of /public
     *
     * @param subPaths the sub paths to use
     * @return the location as string
     */
    private static String getClasspathPublicSubPathLocation(String... subPaths) {
        return Stream.concat(Stream.of("classpath:public"), Arrays.stream(subPaths)).collect(Collectors.joining("/")) + "/";
    }

    /**
     * Create a file system URI including the given subpaths of /public
     *
     * @param subPaths the sub paths to use
     * @return the location as string
     */
    private static String getFileSystemPublicSubPathResourceLocation(String... subPaths) {
        var userDir = System.getProperty("user.dir");
        var morePaths = Stream.concat(Stream.of("public"), Arrays.stream(subPaths)).toList().toArray(new String[1 + subPaths.length]);
        return "file:" + Path.of(userDir, morePaths) + "/";
    }
}
