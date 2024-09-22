package de.tum.cit.aet.artemis.programming.web.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.service.localvc.LocalVCService.getDefaultBranchOfRepository;

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

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.dto.FileMove;
import de.tum.cit.aet.artemis.programming.dto.RepositoryStatusDTO;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCServletService;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

/**
 * Executes requested actions on the auxiliary repository of a programming exercise. Only available to TAs, Instructors and Admins.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class AuxiliaryRepositoryResource extends RepositoryResource {

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    public AuxiliaryRepositoryResource(ProfileService profileService, UserRepository userRepository, AuthorizationCheckService authCheckService, GitService gitService,
            RepositoryService repositoryService, Optional<VersionControlService> versionControlService, ProgrammingExerciseRepository programmingExerciseRepository,
            RepositoryAccessService repositoryAccessService, Optional<LocalVCServletService> localVCServletService, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository) {
        super(profileService, userRepository, authCheckService, gitService, repositoryService, versionControlService, programmingExerciseRepository, repositoryAccessService,
                localVCServletService);
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
    }

    @Override
    Repository getRepository(Long auxiliaryRepositoryId, RepositoryActionType repositoryActionType, boolean pullOnGet) throws GitAPIException {
        final var auxiliaryRepository = auxiliaryRepositoryRepository.findByIdElseThrow(auxiliaryRepositoryId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(false, auxiliaryRepository.getExercise(), user, "test");
        final var repoUri = auxiliaryRepository.getVcsRepositoryUri();
        return gitService.getOrCheckoutRepository(repoUri, pullOnGet);
    }

    @Override
    VcsRepositoryUri getRepositoryUri(Long auxiliaryRepositoryId) {
        var auxRepo = auxiliaryRepositoryRepository.findByIdElseThrow(auxiliaryRepositoryId);
        return auxRepo.getVcsRepositoryUri();
    }

    @Override
    boolean canAccessRepository(Long auxiliaryRepositoryId) {
        try {
            repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(false, auxiliaryRepositoryRepository.findByIdElseThrow(auxiliaryRepositoryId).getExercise(),
                    userRepository.getUserWithGroupsAndAuthorities(), "auxiliary");
        }
        catch (AccessForbiddenException e) {
            return false;
        }
        return true;
    }

    @Override
    String getOrRetrieveBranchOfDomainObject(Long auxiliaryRepositoryId) {
        AuxiliaryRepository auxiliaryRep = auxiliaryRepositoryRepository.findByIdElseThrow(auxiliaryRepositoryId);
        LocalVCRepositoryUri localVCRepositoryUri = new LocalVCRepositoryUri(auxiliaryRep.getRepositoryUri());
        return getDefaultBranchOfRepository(localVCRepositoryUri);
    }

    @Override
    @GetMapping(value = "auxiliary-repository/{auxiliaryRepositoryId}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<Map<String, FileType>> getFiles(@PathVariable Long auxiliaryRepositoryId) {
        return super.getFiles(auxiliaryRepositoryId);
    }

    @Override
    @GetMapping(value = "auxiliary-repository/{auxiliaryRepositoryId}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<byte[]> getFile(@PathVariable Long auxiliaryRepositoryId, @RequestParam("file") String filename) {
        return super.getFile(auxiliaryRepositoryId, filename);
    }

    @Override
    @PostMapping(value = "auxiliary-repository/{auxiliaryRepositoryId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> createFile(@PathVariable Long auxiliaryRepositoryId, @RequestParam("file") String filePath, HttpServletRequest request) {
        return super.createFile(auxiliaryRepositoryId, filePath, request);
    }

    @Override
    @PostMapping(value = "auxiliary-repository/{auxiliaryRepositoryId}/folder", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> createFolder(@PathVariable Long auxiliaryRepositoryId, @RequestParam("folder") String folderPath, HttpServletRequest request) {
        return super.createFolder(auxiliaryRepositoryId, folderPath, request);
    }

    @Override
    @PostMapping(value = "auxiliary-repository/{auxiliaryRepositoryId}/rename-file", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> renameFile(@PathVariable Long auxiliaryRepositoryId, @RequestBody FileMove fileMove) {
        return super.renameFile(auxiliaryRepositoryId, fileMove);
    }

    @Override
    @DeleteMapping(value = "auxiliary-repository/{auxiliaryRepositoryId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> deleteFile(@PathVariable Long auxiliaryRepositoryId, @RequestParam("file") String filename) {
        return super.deleteFile(auxiliaryRepositoryId, filename);
    }

    @Override
    @GetMapping(value = "auxiliary-repository/{auxiliaryRepositoryId}/pull", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<Void> pullChanges(@PathVariable Long auxiliaryRepositoryId) {
        return super.pullChanges(auxiliaryRepositoryId);
    }

    @Override
    @PostMapping(value = "auxiliary-repository/{auxiliaryRepositoryId}/commit", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> commitChanges(@PathVariable Long auxiliaryRepositoryId) {
        return super.commitChanges(auxiliaryRepositoryId);
    }

    @Override
    @PostMapping(value = "auxiliary-repository/{auxiliaryRepositoryId}/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Void> resetToLastCommit(@PathVariable Long auxiliaryRepositoryId) {
        return super.resetToLastCommit(auxiliaryRepositoryId);
    }

    @Override
    @GetMapping(value = "auxiliary-repository/{auxiliaryRepositoryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<RepositoryStatusDTO> getStatus(@PathVariable Long auxiliaryRepositoryId) throws GitAPIException {
        return super.getStatus(auxiliaryRepositoryId);
    }

    /**
     * Update a list of files in an auxiliary repository based on the submission's content.
     *
     * @param auxiliaryRepositoryId of exercise to which the files belong
     * @param submissions           information about the file updates
     * @param commit                whether to commit after updating the files
     * @param principal             used to check if the user can update the files
     * @return {Map<String, String>} file submissions or the appropriate http error
     */
    @PutMapping("auxiliary-repository/{auxiliaryRepositoryId}/files")
    @EnforceAtLeastTutor
    public ResponseEntity<Map<String, String>> updateTestFiles(@PathVariable("auxiliaryRepositoryId") Long auxiliaryRepositoryId, @RequestBody List<FileSubmission> submissions,
            @RequestParam Boolean commit, Principal principal) {

        if (versionControlService.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VCSNotPresent");
        }

        ProgrammingExercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(auxiliaryRepositoryId);

        Repository repository;
        try {
            repositoryAccessService.checkAccessTestOrAuxRepositoryElseThrow(true, exercise, userRepository.getUserWithGroupsAndAuthorities(principal.getName()), "test");
            repository = gitService.getOrCheckoutRepository(exercise.getVcsTestRepositoryUri(), true);
        }
        catch (AccessForbiddenException e) {
            FileSubmissionError error = new FileSubmissionError(auxiliaryRepositoryId, "noPermissions");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        }
        catch (CheckoutConflictException | WrongRepositoryStateException ex) {
            FileSubmissionError error = new FileSubmissionError(auxiliaryRepositoryId, "checkoutConflict");
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
        catch (GitAPIException ex) {
            FileSubmissionError error = new FileSubmissionError(auxiliaryRepositoryId, "checkoutFailed");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage(), error);
        }
        return saveFilesAndCommitChanges(auxiliaryRepositoryId, submissions, commit, repository);
    }
}
