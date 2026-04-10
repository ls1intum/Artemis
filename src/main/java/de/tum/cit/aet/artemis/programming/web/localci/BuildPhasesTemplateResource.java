package de.tum.cit.aet.artemis.programming.web.localci;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.service.localci.BuildPhasesTemplateService;

/**
 * Service for retrieving build phases template files based on the programming language, project type, and
 * the different options (static analysis, sequential runs) as well as the default
 * image for the programming language and project type for the artemis instance.
 */
@Profile("localci")
@Lazy
@RestController
@RequestMapping("api/programming/phases/")
public class BuildPhasesTemplateResource {

    private static final Logger log = LoggerFactory.getLogger(BuildPhasesTemplateResource.class);

    private final BuildPhasesTemplateService buildPhasesTemplateService;

    /**
     * Constructor for the BuildPhasesTemplateResource
     *
     * @param buildPhasesTemplateService the service for retrieving the build phases template files
     */
    public BuildPhasesTemplateResource(BuildPhasesTemplateService buildPhasesTemplateService) {
        this.buildPhasesTemplateService = buildPhasesTemplateService;
    }

    /**
     * GET /api/programming/phases/templates/:language/:projectType : Get the build phases template file with the given filename<br/>
     * GET /api/programming/phases/templates/:language : Get the build phases template file with the given filename
     * <p>
     * The build plan phases contain the default build plan configuration for new programming exercises.
     *
     * @param language       The programming language for which the build phases template file should be returned
     * @param projectType    The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis Whether the static analysis template should be used
     * @param sequentialRuns Whether the sequential runs template should be used
     * @return The requested build plan phases, or 404 if the file doesn't exist
     */
    @GetMapping({ "templates/{language}/{projectType}", "templates/{language}" })
    @EnforceAtLeastEditor
    public ResponseEntity<BuildPlanPhasesDTO> getBuildPhasesTemplate(@PathVariable ProgrammingLanguage language, @PathVariable Optional<ProjectType> projectType,
            @RequestParam(value = "staticAnalysis", defaultValue = "false") boolean staticAnalysis,
            @RequestParam(value = "sequentialRuns", defaultValue = "false") boolean sequentialRuns) {
        log.debug("REST request to get aeolus template for programming language {} and project type {}, static Analysis: {}, sequential Runs {}", language, projectType,
                staticAnalysis, sequentialRuns);

        String projectTypePrefix = projectType.map(type -> type.name().toLowerCase()).orElse("");

        return getBuildPhasesTemplateFileContentWithResponse(language, projectTypePrefix, staticAnalysis, sequentialRuns);
    }

    /**
     * Returns the content of the template file for the given language and project type as JSON. The build phases in
     * the templates directory only specify the actual actions, since an Artemis admin is allowed to change the
     * docker image and flags we intersect and inject the values for the particular instance before sending it to the
     * client.
     *
     * @param language          The programming language for which the template file should be returned
     * @param projectTypePrefix The project type for which the template file should be returned. If omitted, a default depending on the language will be used.
     * @param staticAnalysis    Whether the static analysis template should be used
     * @param sequentialRuns    Whether the sequential runs template should be used
     * @return The requested build plan phases, or 404 if the phases don't exist
     */
    private ResponseEntity<BuildPlanPhasesDTO> getBuildPhasesTemplateFileContentWithResponse(ProgrammingLanguage language, String projectTypePrefix, boolean staticAnalysis,
            boolean sequentialRuns) {
        try {
            Optional<ProjectType> optionalProjectType = Optional.empty();
            if (!projectTypePrefix.isEmpty()) {
                optionalProjectType = Optional.of(ProjectType.valueOf(projectTypePrefix.toUpperCase()));
            }
            List<BuildPhaseDTO> phases = buildPhasesTemplateService.getBuildPlanPhasesFor(language, optionalProjectType, staticAnalysis, sequentialRuns);
            if (phases == null) {
                return ResponseEntity.notFound().build();
            }
            final String image = buildPhasesTemplateService.getDefaultDockerImageFor(language, optionalProjectType);

            return ResponseEntity.ok(new BuildPlanPhasesDTO(phases, image));
        }
        catch (IOException ex) {
            log.warn("Error when retrieving aeolus template file", ex);
            return ResponseEntity.notFound().build();
        }
    }
}
