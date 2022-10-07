package de.tum.in.www1.artemis.config;

import static java.net.URLDecoder.decode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;

import de.tum.in.www1.artemis.security.jgitServlet.JGitFetchFilter;
import de.tum.in.www1.artemis.security.jgitServlet.JGitPushFilter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RemoteRepositoryException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import tech.jhipster.config.JHipsterProperties;
import tech.jhipster.web.filter.CachingHttpHeadersFilter;

import org.eclipse.jgit.http.server.GitServlet;

/**
 * Configuration of web application with Servlet 3.0 APIs.
 */
@Configuration
public class WebConfigurer implements ServletContextInitializer, WebServerFactoryCustomizer<WebServerFactory> {

    private final Logger log = LoggerFactory.getLogger(WebConfigurer.class);

    private final Environment env;

    private final JHipsterProperties jHipsterProperties;

    private final JGitFetchFilter jGitFetchFilter;
    private final JGitPushFilter jGitPushFilter;

    @Value("${artemis.local-git-server-path}")
    private String localGitPath;

    public WebConfigurer(Environment env, JHipsterProperties jHipsterProperties, JGitFetchFilter jGitFetchFilter, JGitPushFilter jGitPushFilter) {
        this.env = env;
        this.jHipsterProperties = jHipsterProperties;
        this.jGitFetchFilter = jGitFetchFilter;
        this.jGitPushFilter = jGitPushFilter;
    }

    @Override
    public void onStartup(ServletContext servletContext) {
        if (env.getActiveProfiles().length != 0) {
            log.info("Web application configuration, using profiles: {}", (Object[]) env.getActiveProfiles());
        }
        setCachingHttpHeaders(servletContext);

        // Setup JGit Servlet
       /* try {
            Repository repository = createNewRepository();
            populateRepository(repository);
            GitServlet gs = new GitServlet();
            gs.setRepositoryResolver((req, name) -> {
                repository.incrementOpen();
                return repository;
            });

            ServletRegistration.Dynamic jgitServlet = servletContext.addServlet("JgitServlet", gs);
            jgitServlet.addMapping("/git/*");
            jgitServlet.setAsyncSupported(true);
            jgitServlet.setLoadOnStartup(2);
        } catch (Exception e) {
            System.out.println("Something went wrong creating the test repository.");
        } */


        log.info("Web application fully configured");
    }

    private final HashMap<String, Repository> repositories = new HashMap<>();

    @Bean
    public ServletRegistrationBean<GitServlet> jgitServlet(ApplicationContext applicationContext) {

        try {
            //Repository repository = createNewRepository();
            //populateRepository(repository);
            GitServlet gs = new GitServlet();
            gs.setRepositoryResolver((req, name) -> {
                // req – the current request, may be used to inspect session state including cookies or user authentication.
                // name – name of the repository, as parsed out of the URL.
                // Returns the opened repository instance, never null.

                // Find the local repository depending on the name and return an opened instance. Must be closed later on.
                log.debug("Path to resolve repository from: " + localGitPath + File.separator + name);
                if (!new File(localGitPath + File.separator + name).exists()) {
                    log.error("Could not find local repository with name {}", name);
                    throw new RepositoryNotFoundException(name);
                }
                //String projectKey = name.split("-")[0].toUpperCase();
                //File gitDir = new File(localGitPath + "/" + projectKey + "/" + name + ".git");
                File gitDir = new File(localGitPath + File.separator + name + File.separator + ".git");

                Repository repository = null;

                if (repositories.containsKey(name)) {
                    log.debug("Retrieving existing local repository {}", name);
                    repository = repositories.get(name);
                } else {
                    log.debug("Opening local repository {}", name);
                    try {
                        repository = FileRepositoryBuilder.create(gitDir);
                        this.repositories.put(name, repository);
                    } catch (IOException e) {
                        log.error("Unable to open local repository {}", name);
                        throw new RepositoryNotFoundException(name);
                    }
                }

                // Enable pushing without credentials
                repository.getConfig().setString("http", null, "receivepack", "true");

                repository.incrementOpen();
                return repository;
            });

            gs.addUploadPackFilter(jGitFetchFilter);
            gs.addReceivePackFilter(jGitPushFilter);

            return new ServletRegistrationBean<>(gs, "/git/*");


        } catch (Exception e) {
            System.out.println("Something went wrong creating the test repository.");
        }
        return null;
    }

    private void populateRepository(Repository repository) throws IOException, GitAPIException {
        // enable pushing to the sample repository via http
        repository.getConfig().setString("http", null, "receivepack", "true");

        try (Git git = new Git(repository)) {
            File myfile = new File(repository.getDirectory().getParent(), "testfile");
            if(!myfile.createNewFile()) {
                throw new IOException("Could not create file " + myfile);
            }

            git.add().addFilepattern("testfile").call();

            log.debug("Added file " + myfile + " to repository at " + repository.getDirectory());

            git.commit().setMessage("Test-Checkin").call();
        }
    }

    private Repository createNewRepository() throws IOException {
        // prepare a new folder
        File localPath = File.createTempFile("TestGitRepository", "");
        if(!localPath.delete()) {
            throw new IOException("Could not delete temporary file " + localPath);
        }

        if(!localPath.mkdirs()) {
            throw new IOException("Could not create directory " + localPath);
        }

        // create the directory
        Repository repository = FileRepositoryBuilder.create(new File(localPath, ".git"));
        repository.create();

        log.debug("Created repository at {}", localPath);

        return repository;
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
            File root;
            String prefixPath = resolvePathPrefix();
            root = new File(prefixPath + "build/resources/main/static/");
            if (root.exists() && root.isDirectory()) {
                servletWebServer.setDocumentRoot(root);
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
        if (config.getAllowedOrigins() != null && !config.getAllowedOrigins().isEmpty()) {
            log.debug("Registering CORS filter");
            source.registerCorsConfiguration("/api/**", config);
            source.registerCorsConfiguration("/management/**", config);
            source.registerCorsConfiguration("/v3/api-docs", config);
        }
        return new CorsFilter(source);
    }
}
