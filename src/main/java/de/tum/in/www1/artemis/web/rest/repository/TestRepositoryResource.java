package de.tum.in.www1.artemis.web.rest.repository;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ProfileService;
import de.tum.in.www1.artemis.service.RepositoryAccessService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.localci.LocalCIConnectorService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Executes requested actions on the test repository of a programming exercise. Only available to TAs, Instructors and Admins.
 */
@RestController
@RequestMapping("/api")
public class TestRepositoryResource extends RepositoryResource {

    public TestRepositoryResource(ProfileService profileService, UserRepository userRepository, AuthorizationCheckService authCheckService, GitService gitService,
            RepositoryService repositoryService, Optional<VersionControlService> versionControlService, ProgrammingExerciseRepository programmingExerciseRepository,
            RepositoryAccessService repositoryAccessService, Optional<LocalCIConnectorService> localCIConnectorService) {
        super(profileService, userRepository, authCheckService, gitService, repositoryService, versionControlService, programmingExerciseRepository, repositoryAccessService,
                localCIConnectorService);
    }

    @Override
    Repository getRepository(Long exerciseId, RepositoryActionType repositoryActionType, boolean pullOnGet) throws GitAPIException {
        final var exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(false, exercise, user, "test");
        final var repoUrl = exercise.getVcsTestRepositoryUrl();
        return gitService.getOrCheckoutRepository(repoUrl, pullOnGet);
    }

    @Override
    VcsRepositoryUrl getRepositoryUrl(Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        return exercise.getVcsTestRepositoryUrl();
    }

    @Override
    boolean canAccessRepository(Long exerciseId) {
        try {
            repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(true, programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId),
                    userRepository.getUserWithGroupsAndAuthorities(), "test");
        }
        catch (AccessForbiddenException e) {
            return false;
        }
        return true;
    }

    @Override
    String getOrRetrieveBranchOfDomainObject(Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        return versionControlService.orElseThrow().getOrRetrieveBranchOfExercise(exercise);
    }

    @Override
    @GetMapping(value = "/test-repository/{exerciseId}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<Map<String, FileType>> getFiles(@PathVariable Long exerciseId) {
        return super.getFiles(exerciseId);
    }

    @Override
    @GetMapping(value = "/test-repository/{exerciseId}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<byte[]> getFile(@PathVariable Long exerciseId, @RequestParam("file") String filename) {
        return super.getFile(exerciseId, filename);
    }

    @Override
    @PostMapping(value = "/test-repository/{exerciseId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> createFile(@PathVariable Long exerciseId, @RequestParam("file") String filePath, HttpServletRequest request) {
        return super.createFile(exerciseId, filePath, request);
    }

    @Override
    @PostMapping(value = "/test-repository/{exerciseId}/folder", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> createFolder(@PathVariable Long exerciseId, @RequestParam("folder") String folderPath, HttpServletRequest request) {
        return super.createFolder(exerciseId, folderPath, request);
    }

    @Override
    @PostMapping(value = "/test-repository/{exerciseId}/rename-file", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> renameFile(@PathVariable Long exerciseId, @RequestBody FileMove fileMove) {
        return super.renameFile(exerciseId, fileMove);
    }

    @Override
    @DeleteMapping(value = "/test-repository/{exerciseId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> deleteFile(@PathVariable Long exerciseId, @RequestParam("file") String filename) {
        return super.deleteFile(exerciseId, filename);
    }

    @Override
    @GetMapping(value = "/test-repository/{exerciseId}/pull", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<Void> pullChanges(@PathVariable Long exerciseId) {
        return super.pullChanges(exerciseId);
    }

    @Override
    @PostMapping(value = "/test-repository/{exerciseId}/commit", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> commitChanges(@PathVariable Long exerciseId) {
        return super.commitChanges(exerciseId);
    }

    @Override
    @PostMapping(value = "/test-repository/{exerciseId}/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> resetToLastCommit(@PathVariable Long exerciseId) {
        return super.resetToLastCommit(exerciseId);
    }

    @Override
    @GetMapping(value = "/test-repository/{exerciseId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<RepositoryStatusDTO> getStatus(@PathVariable Long exerciseId) throws GitAPIException {
        return super.getStatus(exerciseId);
    }

    /**
     * Update a list of files in a test repository based on the submission's content.
     *
     * @param exerciseId  of exercise to which the files belong
     * @param submissions information about the file updates
     * @param commit      whether to commit after updating the files
     * @param principal   used to check if the user can update the files
     * @return {Map<String, String>} file submissions or the appropriate http error
     */
    @PutMapping("/test-repository/{exerciseId}/files")
    @EnforceAtLeastTutor
    public ResponseEntity<Map<String, String>> updateTestFiles(@PathVariable("exerciseId") Long exerciseId, @RequestBody List<FileSubmission> submissions,
            @RequestParam Boolean commit, Principal principal) {

        if (versionControlService.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VCSNotPresent");
        }

        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);

        Repository repository;
        try {
            repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(true, exercise, userRepository.getUserWithGroupsAndAuthorities(principal.getName()), "test");
            repository = gitService.getOrCheckoutRepository(exercise.getVcsTestRepositoryUrl(), true);
        }
        catch (AccessForbiddenException e) {
            FileSubmissionError error = new FileSubmissionError(exerciseId, "noPermissions");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        }
        catch (CheckoutConflictException | WrongRepositoryStateException ex) {
            FileSubmissionError error = new FileSubmissionError(exerciseId, "checkoutConflict");
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
        catch (GitAPIException ex) {
            FileSubmissionError error = new FileSubmissionError(exerciseId, "checkoutFailed");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage(), error);
        }
        Map<String, String> fileSaveResult = saveFileSubmissions(submissions, repository);

        if (commit) {
            var response = super.commitChanges(exerciseId);
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new ResponseStatusException(response.getStatusCode());
            }
        }

        return ResponseEntity.ok(fileSaveResult);
    }
}
