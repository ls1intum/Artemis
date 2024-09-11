package de.tum.cit.aet.artemis.web.rest.repository;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.domain.FileType;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.Repository;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.service.connectors.GitService;
import de.tum.cit.aet.artemis.service.connectors.localvc.LocalVCServletService;
import de.tum.cit.aet.artemis.service.connectors.vcs.VersionControlService;
import de.tum.cit.aet.artemis.service.feature.Feature;
import de.tum.cit.aet.artemis.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.service.programming.RepositoryAccessService;
import de.tum.cit.aet.artemis.service.programming.RepositoryService;
import de.tum.cit.aet.artemis.web.rest.dto.FileMove;
import de.tum.cit.aet.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Executes requested actions on the test repository of a programming exercise. Only available to TAs, Instructors and Admins.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class TestRepositoryResource extends RepositoryResource {

    public TestRepositoryResource(ProfileService profileService, UserRepository userRepository, AuthorizationCheckService authCheckService, GitService gitService,
            RepositoryService repositoryService, Optional<VersionControlService> versionControlService, ProgrammingExerciseRepository programmingExerciseRepository,
            RepositoryAccessService repositoryAccessService, Optional<LocalVCServletService> localVCServletService) {
        super(profileService, userRepository, authCheckService, gitService, repositoryService, versionControlService, programmingExerciseRepository, repositoryAccessService,
                localVCServletService);
    }

    @Override
    Repository getRepository(Long exerciseId, RepositoryActionType repositoryActionType, boolean pullOnGet) throws GitAPIException {
        final var exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(false, exercise, user, "test");
        final var repoUri = exercise.getVcsTestRepositoryUri();
        return gitService.getOrCheckoutRepository(repoUri, pullOnGet);
    }

    @Override
    VcsRepositoryUri getRepositoryUri(Long exerciseId) {
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        return exercise.getVcsTestRepositoryUri();
    }

    @Override
    boolean canAccessRepository(Long exerciseId) {
        try {
            repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(false, programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId),
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
    @GetMapping(value = "test-repository/{exerciseId}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<Map<String, FileType>> getFiles(@PathVariable Long exerciseId) {
        return super.getFiles(exerciseId);
    }

    @Override
    @GetMapping(value = "test-repository/{exerciseId}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<byte[]> getFile(@PathVariable Long exerciseId, @RequestParam("file") String filename) {
        return super.getFile(exerciseId, filename);
    }

    @Override
    @PostMapping(value = "test-repository/{exerciseId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> createFile(@PathVariable Long exerciseId, @RequestParam("file") String filePath, HttpServletRequest request) {
        return super.createFile(exerciseId, filePath, request);
    }

    @Override
    @PostMapping(value = "test-repository/{exerciseId}/folder", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> createFolder(@PathVariable Long exerciseId, @RequestParam("folder") String folderPath, HttpServletRequest request) {
        return super.createFolder(exerciseId, folderPath, request);
    }

    @Override
    @PostMapping(value = "test-repository/{exerciseId}/rename-file", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> renameFile(@PathVariable Long exerciseId, @RequestBody FileMove fileMove) {
        return super.renameFile(exerciseId, fileMove);
    }

    @Override
    @DeleteMapping(value = "test-repository/{exerciseId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> deleteFile(@PathVariable Long exerciseId, @RequestParam("file") String filename) {
        return super.deleteFile(exerciseId, filename);
    }

    @Override
    @GetMapping(value = "test-repository/{exerciseId}/pull", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<Void> pullChanges(@PathVariable Long exerciseId) {
        return super.pullChanges(exerciseId);
    }

    @Override
    @PostMapping(value = "test-repository/{exerciseId}/commit", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> commitChanges(@PathVariable Long exerciseId) {
        return super.commitChanges(exerciseId);
    }

    @Override
    @PostMapping(value = "test-repository/{exerciseId}/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> resetToLastCommit(@PathVariable Long exerciseId) {
        return super.resetToLastCommit(exerciseId);
    }

    @Override
    @GetMapping(value = "test-repository/{exerciseId}", produces = MediaType.APPLICATION_JSON_VALUE)
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
    @PutMapping("test-repository/{exerciseId}/files")
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
            repository = gitService.getOrCheckoutRepository(exercise.getVcsTestRepositoryUri(), true);
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
        return saveFilesAndCommitChanges(exerciseId, submissions, commit, repository);
    }
}
