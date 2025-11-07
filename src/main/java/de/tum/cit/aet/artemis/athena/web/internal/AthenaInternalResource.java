package de.tum.cit.aet.artemis.athena.web.internal;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;
import static de.tum.cit.aet.artemis.programming.service.localvc.ssh.HashUtils.hashSha256;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.athena.service.AthenaRepositoryExportService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.annotations.Internal;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@Profile(PROFILE_ATHENA)
@Lazy
@RestController
@RequestMapping("api/athena/internal/")
public class AthenaInternalResource {

    private static final Logger log = LoggerFactory.getLogger(AthenaInternalResource.class);

    private final AthenaRepositoryExportService athenaRepositoryExportService;

    private final byte[] athenaSecretHash;

    public AthenaInternalResource(AthenaRepositoryExportService athenaRepositoryExportService, @Value("${artemis.athena.secret}") String athenaSecret) {
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
     * GET programming-exercises/:exerciseId/submissions/:submissionId/repository : Get the student repository as a file map based on the submission id
     *
     * @param exerciseId   the id of the exercise the submission belongs to
     * @param submissionId the id of the submission to get the repository for (refers to one student submission)
     * @param auth         the auth header value to check
     * @return 200 Ok with the file map as body if successful
     */
    @GetMapping("programming-exercises/{exerciseId}/submissions/{submissionId}/repository")
    @Internal
    public ResponseEntity<Map<String, String>> getRepository(@PathVariable long exerciseId, @PathVariable long submissionId, @RequestHeader(HttpHeaders.AUTHORIZATION) String auth)
            throws IOException {
        log.debug("REST call to get student repository for exercise {}, submission {}", exerciseId, submissionId);
        checkAthenaSecret(auth);
        return ResponseEntity.ok(athenaRepositoryExportService.getStudentRepositoryFilesContent(exerciseId, submissionId));
    }

    /**
     * GET programming-exercises/:exerciseId/repository/template : Get the template repository as a file map
     *
     * @param exerciseId the id of the exercise
     * @param auth       the auth header value to check
     * @return 200 Ok with the file map as body if successful
     */
    @GetMapping("programming-exercises/{exerciseId}/repository/template")
    @Internal
    public ResponseEntity<Map<String, String>> getTemplateRepository(@PathVariable long exerciseId, @RequestHeader(HttpHeaders.AUTHORIZATION) String auth) throws IOException {
        log.debug("REST call to get template repository for exercise {}", exerciseId);
        checkAthenaSecret(auth);
        return ResponseEntity.ok(athenaRepositoryExportService.getInstructorRepositoryFilesContent(exerciseId, RepositoryType.TEMPLATE));
    }

    /**
     * GET programming-exercises/:exerciseId/repository/solution : Get the solution repository as a file map
     *
     * @param exerciseId the id of the exercise
     * @param auth       the auth header value to check
     * @return 200 Ok with the file map as body if successful
     */
    @GetMapping("programming-exercises/{exerciseId}/repository/solution")
    @Internal
    public ResponseEntity<Map<String, String>> getSolutionRepository(@PathVariable long exerciseId, @RequestHeader(HttpHeaders.AUTHORIZATION) String auth) throws IOException {
        log.debug("REST call to get solution repository for exercise {}", exerciseId);
        checkAthenaSecret(auth);
        return ResponseEntity.ok(athenaRepositoryExportService.getInstructorRepositoryFilesContent(exerciseId, RepositoryType.SOLUTION));
    }

    /**
     * GET programming-exercises/:exerciseId/repository/tests : Get the test repository as a file map
     *
     * @param exerciseId the id of the exercise
     * @param auth       the auth header value to check
     * @return 200 Ok with the file map as body if successful
     */
    @GetMapping("programming-exercises/{exerciseId}/repository/tests")
    @Internal
    public ResponseEntity<Map<String, String>> getTestRepository(@PathVariable long exerciseId, @RequestHeader(HttpHeaders.AUTHORIZATION) String auth) throws IOException {
        log.debug("REST call to get test repository for exercise {}", exerciseId);
        checkAthenaSecret(auth);
        return ResponseEntity.ok(athenaRepositoryExportService.getInstructorRepositoryFilesContent(exerciseId, RepositoryType.TESTS));
    }
}
