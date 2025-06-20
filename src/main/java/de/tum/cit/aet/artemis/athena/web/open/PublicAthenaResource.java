package de.tum.cit.aet.artemis.athena.web.open;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;
import static de.tum.cit.aet.artemis.programming.service.localvc.ssh.HashUtils.hashSha256;

import java.io.IOException;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.athena.service.AthenaRepositoryExportService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceNothing;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.util.ResponseUtil;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * REST controller for providing Athena access to the repositories of programming exercises.
 */
@Profile(PROFILE_ATHENA)
@Lazy
@RestController
@RequestMapping("api/athena/public/")
public class PublicAthenaResource {

    private static final Logger log = LoggerFactory.getLogger(PublicAthenaResource.class);

    private final AthenaRepositoryExportService athenaRepositoryExportService;

    private final byte[] athenaSecretHash;

    /**
     * The PublicAthenaResource provides endpoints for Athena to get the repositories from Artemis.
     */
    public PublicAthenaResource(AthenaRepositoryExportService athenaRepositoryExportService, @Value("${artemis.athena.secret}") String athenaSecret) {
        this.athenaRepositoryExportService = athenaRepositoryExportService;
        this.athenaSecretHash = hashSha256(athenaSecret);
    }

    /**
     * Check if the given auth header is valid for Athena, otherwise throw an exception.
     *
     * @param incomingSecret the auth header value to check
     */
    private void checkAthenaSecret(String incomingSecret) {
        if (!MessageDigest.isEqual(athenaSecretHash, hashSha256(incomingSecret))) {
            log.error("Athena secret does not match");
            throw new AccessForbiddenException("Athena secret does not match");
        }
    }

    /**
     * GET public/programming-exercises/:exerciseId/submissions/:submissionId/repository : Get the repository as a zip file download
     *
     * @param exerciseId   the id of the exercise the submission belongs to
     * @param submissionId the id of the submission to get the repository for
     * @param auth         the auth header value to check
     * @return 200 Ok with the zip file as body if successful
     */
    @GetMapping("programming-exercises/{exerciseId}/submissions/{submissionId}/repository")
    @EnforceNothing // We check the Athena secret instead
    @ManualConfig
    public ResponseEntity<Resource> getRepository(@PathVariable long exerciseId, @PathVariable long submissionId, @RequestHeader("Authorization") String auth) throws IOException {
        log.debug("REST call to get student repository for exercise {}, submission {}", exerciseId, submissionId);
        checkAthenaSecret(auth);
        return ResponseUtil.ok(athenaRepositoryExportService.exportRepository(exerciseId, submissionId, null));
    }

    /**
     * GET public/programming-exercises/:exerciseId/repository/template : Get the template repository as a zip file download
     *
     * @param exerciseId the id of the exercise
     * @param auth       the auth header value to check
     * @return 200 Ok with the zip file as body if successful
     */
    @GetMapping("programming-exercises/{exerciseId}/repository/template")
    @EnforceNothing // We check the Athena secret instead
    @ManualConfig
    public ResponseEntity<Resource> getTemplateRepository(@PathVariable long exerciseId, @RequestHeader("Authorization") String auth) throws IOException {
        log.debug("REST call to get template repository for exercise {}", exerciseId);
        checkAthenaSecret(auth);
        return ResponseUtil.ok(athenaRepositoryExportService.exportRepository(exerciseId, null, RepositoryType.TEMPLATE));
    }

    /**
     * GET public/programming-exercises/:exerciseId/repository/solution : Get the solution repository as a zip file download
     *
     * @param exerciseId the id of the exercise
     * @param auth       the auth header value to check
     * @return 200 Ok with the zip file as body if successful
     */
    @GetMapping("programming-exercises/{exerciseId}/repository/solution")
    @EnforceNothing // We check the Athena secret instead
    @ManualConfig
    public ResponseEntity<Resource> getSolutionRepository(@PathVariable long exerciseId, @RequestHeader("Authorization") String auth) throws IOException {
        log.debug("REST call to get solution repository for exercise {}", exerciseId);
        checkAthenaSecret(auth);
        return ResponseUtil.ok(athenaRepositoryExportService.exportRepository(exerciseId, null, RepositoryType.SOLUTION));
    }

    /**
     * GET public/programming-exercises/:exerciseId/repository/tests : Get the test repository as a zip file download
     *
     * @param exerciseId the id of the exercise
     * @param auth       the auth header value to check
     * @return 200 Ok with the zip file as body if successful
     */
    @GetMapping("programming-exercises/{exerciseId}/repository/tests")
    @EnforceNothing // We check the Athena secret instead
    @ManualConfig
    public ResponseEntity<Resource> getTestRepository(@PathVariable long exerciseId, @RequestHeader("Authorization") String auth) throws IOException {
        log.debug("REST call to get test repository for exercise {}", exerciseId);
        checkAthenaSecret(auth);
        return ResponseUtil.ok(athenaRepositoryExportService.exportRepository(exerciseId, null, RepositoryType.TESTS));
    }
}
