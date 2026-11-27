package de.tum.cit.aet.artemis.sandbox.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.sandbox.config.SandboxEnabled;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxConfigurationDTO;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxFrameworkVersionsDTO;
import de.tum.cit.aet.artemis.sandbox.service.SandboxConfigurationService;

/**
 * REST controller for the sandbox of a Java programming exercise, i.e. the Ares security policy that restricts what student code may do while the tests run.
 * <p>
 * Reads are open to editors of the exercise's course. The configuration is read from the exercise-tests repository, which is its single source of truth.
 */
@Conditional(SandboxEnabled.class)
@Lazy
@FeatureUsage(UserFeature.PROGRAMMING_SECURITY_FRAMEWORK)
@RestController
@RequestMapping("api/sandbox/")
public class SandboxResource {

    private static final Logger log = LoggerFactory.getLogger(SandboxResource.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final SandboxConfigurationService configurationService;

    public SandboxResource(ProgrammingExerciseRepository programmingExerciseRepository, SandboxConfigurationService configurationService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.configurationService = configurationService;
    }

    /**
     * GET programming-exercises/:exerciseId/configuration : the sandbox configuration of an exercise, read from its exercise-tests repository.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the configuration in the body
     */
    @GetMapping("programming-exercises/{exerciseId}/configuration")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<SandboxConfigurationDTO> getConfiguration(@PathVariable long exerciseId) {
        log.debug("REST request to get the sandbox configuration of programming exercise {}", exerciseId);
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        return ResponseEntity.ok(configurationService.getConfiguration(exercise));
    }

    /**
     * GET programming-exercises/:exerciseId/framework-versions : the Ares versions an instructor can pin for an exercise and the version each template
     * starts with. They are the same for every exercise, but only those who can edit an exercise need them.
     *
     * @param exerciseId of the programming exercise
     * @return 200 (OK) with the versions in the body
     */
    @GetMapping("programming-exercises/{exerciseId}/framework-versions")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<SandboxFrameworkVersionsDTO> getFrameworkVersions(@PathVariable long exerciseId) {
        log.debug("REST request to get the sandbox framework versions for programming exercise {}", exerciseId);
        return ResponseEntity.ok(configurationService.getFrameworkVersions());
    }

}
