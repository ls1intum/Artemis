package de.tum.in.www1.artemis.web.rest;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.connectors.BuildScriptProvider;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;

/**
 * Service for retrieving aeolus template files based on the programming language, project type, and
 * the different options (static analysis, sequential runs, test coverage) as well as the default
 * image for the programming language and project type for the artemis instance.
 */
@RestController
@Profile("aeolus | localci")
@RequestMapping("/api/aeolus")
public class AeolusTemplateResource {

    private final Logger logger = LoggerFactory.getLogger(AeolusTemplateResource.class);

    private final AeolusTemplateService aeolusTemplateService;

    private final BuildScriptProvider buildScriptProvider;

    /**
     * Constructor for the AeolusTemplateResource
     *
     * @param aeolusTemplateService the service for retrieving the aeolus template files
     */
    public AeolusTemplateResource(AeolusTemplateService aeolusTemplateService, BuildScriptProvider buildScriptProvider) {
        this.aeolusTemplateService = aeolusTemplateService;
        this.buildScriptProvider = buildScriptProvider;
    }

    /**
     * GET /api/aeolus/templates/:language/:projectType : Get the aeolus template file with the given filename<br/>
     * GET /api/aeolus/templates/:language : Get the aeolus template file with the given filename
     * <p>
     * The windfile contains the default build plan configuration for new programming exercises.
     *
     * @param language       The programming language for which the aeolus template file should be returned
     * @param projectType    The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis Whether the static analysis template should be used
     * @param sequentialRuns Whether the sequential runs template should be used
     * @param testCoverage   Whether the test coverage template should be used
     * @return The requested file, or 404 if the file doesn't exist
     */
    @GetMapping({ "/templates/{language}/{projectType}", "/templates/{language}" })
    @EnforceAtLeastEditor
    public ResponseEntity<String> getAeolusTemplate(@PathVariable ProgrammingLanguage language, @PathVariable Optional<ProjectType> projectType,
            @RequestParam(value = "staticAnalysis", defaultValue = "false") boolean staticAnalysis,
            @RequestParam(value = "sequentialRuns", defaultValue = "false") boolean sequentialRuns,
            @RequestParam(value = "testCoverage", defaultValue = "false") boolean testCoverage) {
        logger.debug("REST request to get aeolus template for programming language {} and project type {}, static Analysis: {}, sequential Runs {}, testCoverage: {}", language,
                projectType, staticAnalysis, sequentialRuns, testCoverage);

        String projectTypePrefix = projectType.map(type -> type.name().toLowerCase()).orElse("");

        return getAeolusTemplateFileContentWithResponse(language, projectTypePrefix, staticAnalysis, sequentialRuns, testCoverage);
    }

    /**
     * GET /api/aeolus/templates/:language/:projectType : Get the aeolus template file with the given filename<br/>
     * GET /api/aeolus/templates/:language : Get the aeolus template file with the given filename
     * <p>
     * The windfile contains the default build plan configuration for new programming exercises.
     *
     * @param language       The programming language for which the aeolus template file should be returned
     * @param projectType    The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis Whether the static analysis template should be used
     * @param sequentialRuns Whether the sequential runs template should be used
     * @param testCoverage   Whether the test coverage template should be used
     * @return The requested file, or 404 if the file doesn't exist
     */
    @GetMapping({ "/templateScripts/{language}/{projectType}", "/templateScripts/{language}" })
    @EnforceAtLeastEditor
    public ResponseEntity<String> getAeolusTemplateScript(@PathVariable ProgrammingLanguage language, @PathVariable Optional<ProjectType> projectType,
            @RequestParam(value = "staticAnalysis", defaultValue = "false") boolean staticAnalysis,
            @RequestParam(value = "sequentialRuns", defaultValue = "false") boolean sequentialRuns,
            @RequestParam(value = "testCoverage", defaultValue = "false") boolean testCoverage) {
        logger.debug("REST request to get aeolus template for programming language {} and project type {}, static Analysis: {}, sequential Runs {}, testCoverage: {}", language,
                projectType, staticAnalysis, sequentialRuns, testCoverage);

        String projectTypePrefix = projectType.map(type -> type.name().toLowerCase()).orElse("");

        return getAeolusTemplateScriptWithResponse(language, projectTypePrefix, staticAnalysis, sequentialRuns, testCoverage);
    }

    /**
     * Returns the file content of the template file for the given language and project type as JSON. The windfile in
     * the templates directory only specifies the actual actions, since an Artemis admin is allowed to change the
     * docker image and flags we intersect and inject the values for the particular instance before sending it to the
     * client.
     *
     * @param language          The programming language for which the template file should be returned
     * @param projectTypePrefix The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis    Whether the static analysis template should be used
     * @param sequentialRuns    Whether the sequential runs template should be used
     * @param testCoverage      Whether the test coverage template should be used
     * @return The requested file, or 404 if the file doesn't exist
     */
    private ResponseEntity<String> getAeolusTemplateFileContentWithResponse(ProgrammingLanguage language, String projectTypePrefix, boolean staticAnalysis, boolean sequentialRuns,
            boolean testCoverage) {
        try {
            Optional<ProjectType> optionalProjectType = Optional.empty();
            if (!projectTypePrefix.isEmpty()) {
                optionalProjectType = Optional.of(ProjectType.valueOf(projectTypePrefix.toUpperCase()));
            }
            Windfile windfile = aeolusTemplateService.getWindfileFor(language, optionalProjectType, staticAnalysis, sequentialRuns, testCoverage);
            if (windfile == null) {
                return new ResponseEntity<>(null, null, HttpStatus.NOT_FOUND);
            }
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.APPLICATION_JSON);
            String json = new ObjectMapper().writeValueAsString(windfile);
            return new ResponseEntity<>(json, responseHeaders, HttpStatus.OK);
        }
        catch (IOException ex) {
            logger.warn("Error when retrieving aeolus template file", ex);
            HttpHeaders responseHeaders = new HttpHeaders();
            return new ResponseEntity<>(null, responseHeaders, HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Returns the file content of the template script for the given language and project type as JSON.
     *
     * @param language          The programming language for which the template file should be returned
     * @param projectTypePrefix The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis    Whether the static analysis template should be used
     * @param sequentialRuns    Whether the sequential runs template should be used
     * @param testCoverage      Whether the test coverage template should be used
     * @return The requested file, or 404 if the file doesn't exist
     */
    private ResponseEntity<String> getAeolusTemplateScriptWithResponse(ProgrammingLanguage language, String projectTypePrefix, boolean staticAnalysis, boolean sequentialRuns,
            boolean testCoverage) {
        try {
            Optional<ProjectType> optionalProjectType = Optional.empty();
            if (!projectTypePrefix.isEmpty()) {
                optionalProjectType = Optional.of(ProjectType.valueOf(projectTypePrefix.toUpperCase()));
            }
            String script = buildScriptProvider.getScriptFor(language, optionalProjectType, staticAnalysis, sequentialRuns, testCoverage);
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.TEXT_PLAIN);
            return new ResponseEntity<>(script, responseHeaders, HttpStatus.OK);
        }
        catch (IOException ex) {
            logger.warn("Error when retrieving aeolus template script", ex);
            HttpHeaders responseHeaders = new HttpHeaders();
            return new ResponseEntity<>(null, responseHeaders, HttpStatus.NOT_FOUND);
        }
    }
}
