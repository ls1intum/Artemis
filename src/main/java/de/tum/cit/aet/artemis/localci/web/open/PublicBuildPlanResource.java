package de.tum.cit.aet.artemis.localci.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_JENKINS;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.jspecify.annotations.Nullable;
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

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.localci.config.LocalCILegacyRestPaths;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlan;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;

@Profile(PROFILE_JENKINS)
@Lazy
@FeatureUsage("build-system/build-plans")
@RestController
@RequestMapping({ "api/localci/public/", LocalCILegacyRestPaths.PROGRAMMING_PUBLIC_PREFIX })
public class PublicBuildPlanResource {

    private static final Logger log = LoggerFactory.getLogger(PublicBuildPlanResource.class);

    private final BuildPlanRepository buildPlanRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    public PublicBuildPlanResource(BuildPlanRepository buildPlanRepository, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.buildPlanRepository = buildPlanRepository;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
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

        final BuildPlan buildPlan = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercisesElseThrow(exerciseId);
        // orElseThrow is safe here since the query above ensures that we find a build plan that is attached to that exercise
        final ProgrammingExercise programmingExercise = buildPlan.getProgrammingExerciseById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Could not find connected exercise for build plan."));
        // The configuration is a row of its own and is not loaded with the exercise, so it is read here.
        var buildConfig = programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(programmingExercise.getId());

        if (!buildConfig.hasBuildPlanAccessSecretSet() || !constantTimeEquals(secret, buildConfig.getBuildPlanAccessSecret())) {
            throw new AccessForbiddenException();
        }

        return ResponseEntity.ok().body(buildPlan.getBuildPlan());
    }

    /**
     * Compares two strings in constant time to mitigate timing attacks.
     * Returns false if either input is null.
     *
     * @param providedSecret the secret provided in the request
     * @param expectedSecret the secret stored in the system (e.g., database)
     * @return true if both secrets are non-null and equal, false otherwise
     */
    private boolean constantTimeEquals(@Nullable String providedSecret, @Nullable String expectedSecret) {
        if (providedSecret == null || expectedSecret == null) {
            return false;
        }

        byte[] providedBytes = providedSecret.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expectedSecret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(providedBytes, expectedBytes);
    }

}
