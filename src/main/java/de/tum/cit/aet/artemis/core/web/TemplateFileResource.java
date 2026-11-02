package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * REST controller for static programming and course templates.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.LECTURE_UNITS)
@RestController
@RequestMapping("api/core/")
public class TemplateFileResource {

    private static final Logger log = LoggerFactory.getLogger(TemplateFileResource.class);

    private final ResourceLoaderService resourceLoaderService;

    public TemplateFileResource(ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * GET /files/templates/:language/:projectType : Get the template file with the given filename<br/>
     * GET /files/templates/:language : Get the template file with the given filename
     * <p>
     * The readme file contains the default problem statement for new programming exercises.
     *
     * @param language    The programming language for which the template file should be returned
     * @param projectType The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @return The requested file, or 404 if the file doesn't exist
     */
    @FeatureUsage(UserFeature.PROGRAMMING_AUTHORING)
    @GetMapping({ "files/templates/{language}/{projectType}", "files/templates/{language}" })
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getTemplateFile(@PathVariable ProgrammingLanguage language, @PathVariable Optional<ProjectType> projectType) {
        log.debug("REST request to get readme file for programming language {} and project type {}", language, projectType);

        String languagePrefix = language.name().toLowerCase(Locale.ROOT);
        String projectTypePrefix = projectType.map(type -> type.name().toLowerCase(Locale.ROOT)).orElse("");

        return getTemplateFileContentWithResponse(languagePrefix, projectTypePrefix);
    }

    private ResponseEntity<byte[]> getTemplateFileContentWithResponse(String languagePrefix, String projectTypePrefix) {
        try {
            Resource fileResource = resourceLoaderService.getResource(Path.of("templates", languagePrefix, projectTypePrefix, "readme"));
            if (!fileResource.exists() && !projectTypePrefix.isEmpty()) {
                // Load without project type if not found with project type
                fileResource = resourceLoaderService.getResource(Path.of("templates", languagePrefix, "readme"));
            }
            byte[] fileContent;
            try (InputStream inputStream = fileResource.getInputStream()) {
                fileContent = inputStream.readAllBytes();
            }
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<>(fileContent, responseHeaders, HttpStatus.OK);
        }
        catch (IOException ex) {
            log.debug("Error when retrieving template file : {}", ex.getMessage());
            HttpHeaders responseHeaders = new HttpHeaders();
            return new ResponseEntity<>(null, responseHeaders, HttpStatus.NOT_FOUND);
        }
    }

    /**
     * GET /files/templates/code-of-conduct : Get the Code of Conduct template
     *
     * @return The requested file, 403 if the logged-in user is not allowed to access it, or 404 if the file doesn't exist
     */
    @FeatureUsage(UserFeature.CONVERSATION_ORGANIZATION)
    @GetMapping("files/templates/code-of-conduct")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getCourseCodeOfConduct() throws IOException {
        // TODO: store a Constant
        var templatePath = Path.of("templates", "codeofconduct", "README.md");
        log.debug("REST request to get template : {}", templatePath);
        var resource = resourceLoaderService.getResource(templatePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return ResponseEntity.ok(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

}
