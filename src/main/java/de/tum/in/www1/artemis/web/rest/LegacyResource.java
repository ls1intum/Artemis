package de.tum.in.www1.artemis.web.rest;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.SecurityConfiguration;
import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.security.annotations.ManualConfig;
import de.tum.in.www1.artemis.web.rest.open.PublicProgrammingSubmissionResource;
import de.tum.in.www1.artemis.web.rest.open.PublicResultResource;

/**
 * TODO: Remove this class in June 2024
 * Together with the lines from {@link SecurityConfiguration#configure(HttpSecurity)}
 */
@Profile("core")
@RestController
@RequestMapping("api/core/")
@Deprecated(forRemoval = true)
public class LegacyResource {

    private final PublicProgrammingSubmissionResource publicProgrammingSubmissionResource;

    private final PublicResultResource publicResultResource;

    public LegacyResource(PublicProgrammingSubmissionResource publicProgrammingSubmissionResource, PublicResultResource publicResultResource) {
        this.publicProgrammingSubmissionResource = publicProgrammingSubmissionResource;
        this.publicResultResource = publicResultResource;
    }

    /**
     * Receive a new push notification from the VCS server and save a submission in the database
     *
     * @param participationId the participationId of the participation the repository is linked to
     * @param requestBody     the body of the post request by the VCS.
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) if the latest commit was
     *         already notified about
     * @deprecated use {@link PublicProgrammingSubmissionResource#processNewProgrammingSubmission(Long, Object)} instead
     */
    @PostMapping("programming-submissions/{participationId}")
    @EnforceNothing
    @ManualConfig
    @Deprecated(forRemoval = true)
    public ResponseEntity<?> legacyProcessNewProgrammingSubmission(@PathVariable Long participationId, @RequestBody Object requestBody) {
        return publicProgrammingSubmissionResource.processNewProgrammingSubmission(participationId, requestBody);
    }

    /**
     * Receive a new push notification for a test repository from the VCS server, save a submission in the database
     * and trigger relevant build plans
     *
     * @param exerciseId  the id of the programmingExercise where the test cases got changed
     * @param requestBody the body of the post request by the VCS.
     * @return the ResponseEntity with status 200 (OK)
     * @deprecated use {@link PublicProgrammingSubmissionResource#testCaseChanged(Long, Object)} instead
     */
    @PostMapping("programming-exercises/test-cases-changed/{exerciseId}")
    @EnforceNothing
    @ManualConfig
    @Deprecated(forRemoval = true)
    public ResponseEntity<Void> legacyTestCaseChanged(@PathVariable Long exerciseId, @RequestBody Object requestBody) {
        return publicProgrammingSubmissionResource.testCaseChanged(exerciseId, requestBody);
    }

    /**
     * Receive a new result from the continuous integration server and save it in the database
     *
     * @param token       CI auth token
     * @param requestBody build result of CI system
     * @return a ResponseEntity to the CI system
     * @deprecated use {@link PublicResultResource#processNewProgrammingExerciseResult(String, Object)} instead
     */
    @PostMapping("programming-exercises/new-result")
    @EnforceNothing
    @ManualConfig
    @Deprecated(forRemoval = true)
    public ResponseEntity<?> legacyProcessNewProgrammingExerciseResult(@RequestHeader("Authorization") String token, @RequestBody Object requestBody) {
        return publicResultResource.processNewProgrammingExerciseResult(token, requestBody);
    }
}
