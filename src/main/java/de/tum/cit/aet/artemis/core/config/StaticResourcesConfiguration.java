package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures serving and caching of static resources.
 * <p>
 * This includes:
 * <ul>
 * <li>Static resources from /public (both classpath and file system)</li>
 * <li>Uploaded files such as course icons, profile pictures, drag-and-drop images, markdown files, and exam user images</li>
 * </ul>
 * <p>
 * Cache control headers are set to enable browser caching for these resources, improving performance
 * by reducing redundant downloads of unchanged files.
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
public class StaticResourcesConfiguration implements WebMvcConfigurer {

    private final ArtemisProperties jHipsterProperties;

    public StaticResourcesConfiguration(ArtemisProperties jHipsterProperties) {
        this.jHipsterProperties = jHipsterProperties;
    }

    @Value("${artemis.file-upload-path}")
    private String fileUploadPath;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Enable static resource serving in general from "/public" from both classpath and hosts filesystem
        addResourceHandlerForPath(registry);

        /*
         * Add caching for about us images / contributor images and for emojis. Contributor images are unlikely to change. Emojis must be cached for them to be displayed quickly,
         * see #5186. All other files will not be cached, especially files in /content/* as they might change with any PR.
         */

        var defaultCacheControl = CacheControl.maxAge(jHipsterProperties.getHttp().getCache().getTimeToLiveInDays(), TimeUnit.DAYS).cachePublic();

        addResourceHandlerForPath(registry, "content").setCacheControl(defaultCacheControl);
        addResourceHandlerForPath(registry, "documents").setCacheControl(defaultCacheControl);
        addResourceHandlerForPath(registry, "emoji").setCacheControl(defaultCacheControl);
        addResourceHandlerForPath(registry, "images").setCacheControl(defaultCacheControl);
        addResourceHandlerForPath(registry, "videos").setCacheControl(defaultCacheControl);

        // Add caching for course icons, user profile pictures, and drag and drop quiz pictures
        // Add resource handlers for dynamic image paths based on fileUploadPath
        // TODO: those paths have to be the same as in FilePathService, ideally we reuse the constants and define them only once
        registry.addResourceHandler("/course/icons/**").addResourceLocations("file:" + fileUploadPath + "/images/course/icons/").setCacheControl(defaultCacheControl);

        registry.addResourceHandler("/user/profile-pictures/**").addResourceLocations("file:" + fileUploadPath + "/images/user/profile-pictures/")
                .setCacheControl(defaultCacheControl);

        registry.addResourceHandler("/drag-and-drop/**").addResourceLocations("file:" + fileUploadPath + "/images/drag-and-drop/").setCacheControl(defaultCacheControl);

        registry.addResourceHandler("/markdown/**").addResourceLocations("file:" + fileUploadPath + "/markdown/").setCacheControl(defaultCacheControl);

        registry.addResourceHandler("/exam-user/signatures/**").addResourceLocations("file:" + fileUploadPath + "/images/exam-user/signatures/")
                .setCacheControl(defaultCacheControl);

        registry.addResourceHandler("/exam-user/**").addResourceLocations("file:" + fileUploadPath + "/images/exam-user/").setCacheControl(defaultCacheControl);
    }

    /**
     * Allows anonymous cross-origin reads of the KaTeX assets.
     * <p>
     * The server-rendered problem statement loads {@code katex.min.css} from the configured server URL, which need not be the origin the client is served from. A stylesheet
     * may be loaded cross-origin without CORS, but the {@code @font-face} rules inside it may not: font fetches are always CORS-aware, so on any deployment where the asset
     * origin differs from the page origin the browser would block them without this header and every formula would fall back to a system font. Verified against a real browser,
     * not only against this configuration.
     * <p>
     * These are public, unauthenticated, static assets (see the {@code permitAll} entry for {@code /assets/katex/**} in {@code SecurityConfiguration}), so the wildcard is
     * appropriate. Credentials are deliberately not allowed, which also keeps the wildcard legal.
     *
     * @param registry the spring registry to use
     */
    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/assets/katex/**").allowedOrigins("*").allowedMethods("GET", "HEAD").allowCredentials(false);
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
        var morePaths = Stream.concat(Stream.of("public"), Arrays.stream(subPaths)).toArray(String[]::new);
        return "file:" + Path.of(userDir, morePaths) + "/";
    }
}
