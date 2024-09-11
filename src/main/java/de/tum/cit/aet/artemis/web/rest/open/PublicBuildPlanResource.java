package de.tum.cit.aet.artemis.web.rest.open;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.domain.BuildPlan;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

@Profile("gitlabci | jenkins")
@RestController
// TODO: should we adapt the mapping based on the profile?
@RequestMapping("api/public/")
public class PublicBuildPlanResource {

    private static final Logger log = LoggerFactory.getLogger(PublicBuildPlanResource.class);

    private final BuildPlanRepository buildPlanRepository;

    public PublicBuildPlanResource(BuildPlanRepository buildPlanRepository) {
        this.buildPlanRepository = buildPlanRepository;
    }

    /**
     * Returns the build plan for a given programming exercise.
     *
     * @param exerciseId the exercise for which the build plan should be retrieved
     * @param secret     the secret to authenticate the request
     * @return the build plan stored in the database
     */
    @GetMapping("programming-exercises/{exerciseId}/build-plan")
    @EnforceNothing
    public ResponseEntity<String> getBuildPlan(@PathVariable Long exerciseId, @RequestParam("secret") String secret) {
        log.debug("REST request to get build plan for programming exercise with id {}", exerciseId);

        final BuildPlan buildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercisesWithBuildConfigElseThrow(exerciseId);
        // orElseThrow is safe here since the query above ensures that we find a build plan that is attached to that exercise
        final ProgrammingExercise programmingExercise = buildPlan.getProgrammingExerciseById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Could not find connected exercise for build plan."));

        if (!programmingExercise.getBuildConfig().hasBuildPlanAccessSecretSet() || !secret.equals(programmingExercise.getBuildConfig().getBuildPlanAccessSecret())) {
            throw new AccessForbiddenException();
        }

        return ResponseEntity.ok().body(buildPlan.getBuildPlan());
    }
}
