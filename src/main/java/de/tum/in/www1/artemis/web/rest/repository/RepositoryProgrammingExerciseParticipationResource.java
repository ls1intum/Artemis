package de.tum.in.www1.artemis.web.rest.repository;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;

/**
 * Executes repository actions on repositories related to the participation id transmitted. Available to the owner of the participation, TAs/Instructors of the exercise and Admins.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
public class RepositoryProgrammingExerciseParticipationResource extends RepositoryResource {

    private final ProgrammingExerciseParticipationService participationService;

    private final ProgrammingExerciseService programmingExerciseService;

    public RepositoryProgrammingExerciseParticipationResource(UserService userService, AuthorizationCheckService authCheckService, GitService gitService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, RepositoryService repositoryService, ProgrammingExerciseParticipationService participationService,
            ProgrammingExerciseService programmingExerciseService) {
        super(userService, authCheckService, gitService, continuousIntegrationService, repositoryService);
        this.participationService = participationService;
        this.programmingExerciseService = programmingExerciseService;
    }

    @Override
    Repository getRepository(Long participationId, RepositoryActionType repositoryAction, boolean pullOnGet) throws InterruptedException, IllegalAccessException, GitAPIException {
        Participation participation = participationService.findParticipation(participationId);
        // Error case 1: The participation is not from a programming exercise.
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new IllegalArgumentException();
        }
        // Error case 2: The user does not have permissions to push into the repository.
        boolean hasPermissions = participationService.canAccessParticipation((ProgrammingExerciseParticipation) participation);
        if (!hasPermissions) {
            throw new IllegalAccessException();
        }
        // Error case 3: The user's participation repository is locked.
        if (repositoryAction == RepositoryActionType.WRITE && programmingExerciseService.isParticipationRepositoryLocked((ProgrammingExerciseParticipation) participation)) {
            throw new IllegalAccessException();
        }
        URL repositoryUrl = ((ProgrammingExerciseParticipation) participation).getRepositoryUrlAsUrl();
        return gitService.getOrCheckoutRepository(repositoryUrl, pullOnGet);
    }

    @Override
    URL getRepositoryUrl(Long participationId) throws IllegalArgumentException {
        Participation participation = participationService.findParticipation(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new IllegalArgumentException();
        }
        return ((ProgrammingExerciseParticipation) participation).getRepositoryUrlAsUrl();
    }

    @Override
    boolean canAccessRepository(Long participationId) throws IllegalArgumentException {
        Participation participation = participationService.findParticipation(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new IllegalArgumentException();
        }
        return participationService.canAccessParticipation((ProgrammingExerciseParticipation) participation);
    }

    @Override
    @GetMapping(value = "/repository/{participationId}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HashMap<String, FileType>> getFiles(@PathVariable Long participationId) {
        return super.getFiles(participationId);
    }

    @Override
    @GetMapping(value = "/repository/{participationId}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getFile(@PathVariable Long participationId, @RequestParam("file") String filename) {
        return super.getFile(participationId, filename);
    }

    @Override
    @PostMapping(value = "/repository/{participationId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> createFile(@PathVariable Long participationId, @RequestParam("file") String filename, HttpServletRequest request) {
        return super.createFile(participationId, filename, request);
    }

    @Override
    @PostMapping(value = "/repository/{participationId}/folder", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> createFolder(@PathVariable Long participationId, @RequestParam("folder") String folderName, HttpServletRequest request) {
        return super.createFolder(participationId, folderName, request);
    }

    @Override
    @PostMapping(value = "/repository/{participationId}/rename-file", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> renameFile(@PathVariable Long participationId, @RequestBody FileMove fileMove) {
        return super.renameFile(participationId, fileMove);
    }

    @Override
    @DeleteMapping(value = "/repository/{participationId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteFile(@PathVariable Long participationId, @RequestParam("file") String filename) {
        return super.deleteFile(participationId, filename);
    }

    @Override
    @GetMapping(value = "/repository/{participationId}/pull", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> pullChanges(@PathVariable Long participationId) {
        return super.pullChanges(participationId);
    }

    /**
     * Commit and push the changes to the remote VCS repo.
     * Won't allow a commit if the repository is locked!
     *
     * @param participationId identifier for the repository.
     * @return ok (200) if the push was successful, notFound (404) if the participation does not exist and forbidden (403) if the user does not have permissions to access the participation OR the buildAndTestAfterDueDate is set and the repository is now locked.
     */
    @Override
    @PostMapping(value = "/repository/{participationId}/commit", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> commitChanges(@PathVariable Long participationId) {
        return super.commitChanges(participationId);
    }

    @Override
    @PostMapping(value = "/repository/{participationId}/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> resetToLastCommit(@PathVariable Long participationId) {
        return super.resetToLastCommit(participationId);
    }

    @Override
    @GetMapping(value = "/repository/{participationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RepositoryStatusDTO> getStatus(@PathVariable Long participationId) throws IOException, GitAPIException, InterruptedException {
        return super.getStatus(participationId);
    }

    /**
     * GET /repository/:participationId/buildlogs : get the build log from Bamboo for the "participationId" repository.
     *
     * @param participationId to identify the repository with.
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @GetMapping(value = "/repository/{participationId}/buildlogs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getResultDetails(@PathVariable Long participationId) {
        log.debug("REST request to get build log : {}", participationId);

        ProgrammingExerciseParticipation participation = participationService.findProgrammingExerciseParticipationWithLatestResultAndFeedbacks(participationId);

        if (!participationService.canAccessParticipation(participation)) {
            return forbidden();
        }

        Optional<Result> latestResult = participation.getResults().stream().findFirst();
        // We don't try to fetch build logs for manual results (they were not created through the build but manually by an assessor)!
        if (latestResult.isPresent() && latestResult.get().getAssessmentType().equals(AssessmentType.MANUAL)) {
            // Don't throw an error here, just return an empty list.
            return ResponseEntity.ok(new ArrayList<>());
        }

        List<BuildLogEntry> logs = continuousIntegrationService.get().getLatestBuildLogs(participation.getProgrammingExercise().getProjectKey(), participation.getBuildPlanId());

        return new ResponseEntity<>(logs, HttpStatus.OK);
    }
}
