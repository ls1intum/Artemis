package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.net.URLDecoder.decode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.security.allowedTools.ToolsInterceptor;
import de.tum.cit.aet.artemis.core.security.filter.CachingHttpHeadersFilter;

/**
 * Configuration of web application with Servlet 3.0 APIs.
 */
@Profile(PROFILE_CORE)
@Configuration
@Lazy
public class WebConfigurer implements ServletContextInitializer, WebServerFactoryCustomizer<WebServerFactory>, WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfigurer.class);

    private final Environment env;

    private final ArtemisProperties jHipsterProperties;

    private final ToolsInterceptor toolsInterceptor;

    private final ObjectMapper objectMapper;

    public WebConfigurer(Environment env, ArtemisProperties jHipsterProperties, ToolsInterceptor toolsInterceptor, @Lazy ObjectMapper objectMapper) {
        this.env = env;
        this.jHipsterProperties = jHipsterProperties;
        this.toolsInterceptor = toolsInterceptor;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onStartup(ServletContext servletContext) {
        if (env.getActiveProfiles().length != 0) {
            log.debug("Web application configuration, using profiles: {}", (Object[]) env.getActiveProfiles());
        }
        setCachingHttpHeaders(servletContext);
        log.debug("Web application fully configured");
    }

    /**
     * Customize the Servlet engine: Mime types, the document root, the cache.
     */
    @Override
    public void customize(WebServerFactory server) {
        setMimeMappings(server);
        // When running in an IDE or with ./gradlew bootRun, set location of the static web assets.
        setLocationForStaticAssets(server);
    }

    private void setCachingHttpHeaders(ServletContext server) {
        FilterRegistration.Dynamic cachingHttpHeadersFilter = server.addFilter("cachingHttpHeadersFilter", new CachingHttpHeadersFilter(jHipsterProperties));
        EnumSet<DispatcherType> dispatcherTypes = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ASYNC);
        cachingHttpHeadersFilter.addMappingForUrlPatterns(dispatcherTypes, true, "*.js", "*.css", "/i18n/*");
        cachingHttpHeadersFilter.setAsyncSupported(true);
    }

    private void setMimeMappings(WebServerFactory server) {
        if (server instanceof ConfigurableServletWebServerFactory servletWebServer) {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            // IE issue, see https://github.com/jhipster/generator-jhipster/pull/711
            mappings.add("html", MediaType.TEXT_HTML_VALUE + ";charset=" + StandardCharsets.UTF_8.name().toLowerCase());
            // CloudFoundry issue, see https://github.com/cloudfoundry/gorouter/issues/64
            mappings.add("json", MediaType.TEXT_HTML_VALUE + ";charset=" + StandardCharsets.UTF_8.name().toLowerCase());
            servletWebServer.setMimeMappings(mappings);
        }
    }

    private void setLocationForStaticAssets(WebServerFactory server) {
        if (server instanceof ConfigurableServletWebServerFactory servletWebServer) {
            String prefixPath = resolvePathPrefix();
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String INVALID_PREFIX_ON_WINDOWS = "/";
            boolean isInvalidPrefixOnWindows = prefixPath.startsWith(INVALID_PREFIX_ON_WINDOWS);
            if (isWindows && isInvalidPrefixOnWindows) {
                prefixPath = prefixPath.substring(INVALID_PREFIX_ON_WINDOWS.length());
            }
            Path root = Path.of(prefixPath + "build/resources/main/static/");
            if (Files.exists(root) && Files.isDirectory(root)) {
                servletWebServer.setDocumentRoot(root.toFile());
            }
        }
    }

    /**
     * Resolve path prefix to static resources.
     */
    private String resolvePathPrefix() {
        String fullExecutablePath = decode(this.getClass().getResource("").getPath(), StandardCharsets.UTF_8);
        String rootPath = Path.of(".").toUri().normalize().getPath();
        String extractedPath = fullExecutablePath.replace(rootPath, "");
        int extractionEndIndex = extractedPath.indexOf("build/");
        if (extractionEndIndex <= 0) {
            return "";
        }
        return extractedPath.substring(0, extractionEndIndex);
    }

    /**
     * @return register the cors filter bean
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = jHipsterProperties.getCors();
        if (!CollectionUtils.isEmpty(config.getAllowedOrigins()) || !CollectionUtils.isEmpty(config.getAllowedOriginPatterns())) {
            log.debug("Registering CORS filter");
            source.registerCorsConfiguration("/api/**", config);
            source.registerCorsConfiguration("/management/**", config);
            source.registerCorsConfiguration("/v3/api-docs", config);
        }
        return new CorsFilter(source);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(toolsInterceptor).addPathPatterns("/api/**").excludePathPatterns("/api/*/public/**");
    }

    /**
     * In Spring Framework 7, the default message converter ordering causes ResponseEntity&lt;String&gt;
     * responses to be serialized as JSON strings (wrapped in quotes) instead of plain text.
     * This happens because MappingJackson2HttpMessageConverter is tried before StringHttpMessageConverter
     * and both support String types.
     * <p>
     * This method moves all StringHttpMessageConverter instances before the Jackson converter
     * so that String responses are written as plain text by default, matching the behavior
     * expected by the client and tests.
     */
    @SuppressWarnings({ "deprecation", "removal" }) // extendMessageConverters is deprecated for removal in Spring Framework 7, but the configureMessageConverters replacement
                                                    // causes converter registration issues
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Add a Jackson 2.x converter with the Hibernate7Module-aware ObjectMapper before the Jackson 3.x converter.
        // Spring Framework 7 defaults to a Jackson 3.x converter that doesn't support the Hibernate7Module
        // (which is only available for Jackson 2.x). Without this, serializing entities with uninitialized lazy
        // collections throws LazyInitializationException.
        var jackson2Converter = new MappingJackson2HttpMessageConverter(objectMapper);
        converters.addFirst(jackson2Converter);

        // Collect all StringHttpMessageConverters and remove them from their current positions
        var stringConverters = converters.stream().filter(StringHttpMessageConverter.class::isInstance).toList();
        converters.removeAll(stringConverters);
        // Re-add them at the beginning so they take priority over Jackson for String responses
        converters.addAll(0, stringConverters);
    }
}
