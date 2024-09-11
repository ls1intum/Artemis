package de.tum.cit.aet.artemis.web.rest.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.connectors.GitService;
import de.tum.cit.aet.artemis.core.service.connectors.localvc.LocalVCServletService;
import de.tum.cit.aet.artemis.core.service.connectors.vcs.VersionControlService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.FileType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.service.RepositoryParticipationService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;
import de.tum.cit.aet.artemis.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.web.rest.dto.FileMove;
import de.tum.cit.aet.artemis.web.rest.dto.RepositoryStatusDTO;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Executes repository actions on repositories related to the participation id transmitted. Available to the owner of the participation, TAs/Instructors of the exercise and Admins.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class RepositoryProgrammingExerciseParticipationResource extends RepositoryResource {

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

    private final ProgrammingExerciseParticipationService participationService;

    private final BuildLogEntryService buildLogService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ParticipationRepository participationRepository;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    private final RepositoryParticipationService repositoryParticipationService;

    public RepositoryProgrammingExerciseParticipationResource(ProfileService profileService, UserRepository userRepository, AuthorizationCheckService authCheckService,
            ParticipationAuthorizationCheckService participationAuthCheckService, GitService gitService, Optional<VersionControlService> versionControlService,
            RepositoryService repositoryService, ProgrammingExerciseParticipationService participationService, ProgrammingExerciseRepository programmingExerciseRepository,
            ParticipationRepository participationRepository, BuildLogEntryService buildLogService, ProgrammingSubmissionRepository programmingSubmissionRepository,
            SubmissionPolicyRepository submissionPolicyRepository, RepositoryAccessService repositoryAccessService, Optional<LocalVCServletService> localVCServletService,
            RepositoryParticipationService repositoryParticipationService) {
        super(profileService, userRepository, authCheckService, gitService, repositoryService, versionControlService, programmingExerciseRepository, repositoryAccessService,
                localVCServletService);
        this.participationAuthCheckService = participationAuthCheckService;
        this.participationService = participationService;
        this.buildLogService = buildLogService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.participationRepository = participationRepository;
        this.submissionPolicyRepository = submissionPolicyRepository;
        this.repositoryParticipationService = repositoryParticipationService;
    }

    /**
     * Get the repository for the given participation.
     *
     * @param participationId      that serves as an abstract identifier for retrieving the repository.
     * @param repositoryActionType the type of action that the user wants to perform on the repository (i.e. WRITE, READ or RESET).
     * @param pullOnGet            whether to pull the repository before returning it
     * @return the repository for the given participation
     * @throws GitAPIException          if the repository could not be accessed
     * @throws AccessForbiddenException if the user does not have access to the repository
     */
    @Override
    Repository getRepository(Long participationId, RepositoryActionType repositoryActionType, boolean pullOnGet) throws GitAPIException, AccessForbiddenException {
        Participation participation = participationRepository.findByIdElseThrow(participationId);

        if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
            throw new IllegalArgumentException();
        }

        ProgrammingExercise programmingExercise = programmingExerciseRepository.getProgrammingExerciseFromParticipation(programmingParticipation);
        if (programmingExercise == null) {
            throw new IllegalArgumentException();
        }

        // Add submission policy to the programming exercise.
        programmingExercise.setSubmissionPolicy(submissionPolicyRepository.findByProgrammingExerciseId(programmingExercise.getId()));

        repositoryAccessService.checkAccessRepositoryElseThrow(programmingParticipation, userRepository.getUserWithGroupsAndAuthorities(), programmingExercise,
                repositoryActionType);

        return repositoryParticipationService.getRepositoryFromGitService(pullOnGet, programmingParticipation);
    }

    @Override
    VcsRepositoryUri getRepositoryUri(Long participationId) throws IllegalArgumentException {
        return getProgrammingExerciseParticipation(participationId).getVcsRepositoryUri();
    }

    /**
     * Gets a programming exercise participation with the given id from the database.
     *
     * @param participationId the id of the participation to retrieve
     * @return a programming exercise participation with the given id
     * @throws IllegalArgumentException if the participation is not a programming exercise participation
     */
    private ProgrammingExerciseParticipation getProgrammingExerciseParticipation(long participationId) {
        Participation participation = participationRepository.findByIdElseThrow(participationId);

        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new IllegalArgumentException();
        }
        return (ProgrammingExerciseParticipation) participation;
    }

    @Override
    boolean canAccessRepository(Long participationId) throws IllegalArgumentException {
        Participation participation = participationRepository.findByIdElseThrow(participationId);
        if (participation instanceof ProgrammingExerciseParticipation programmingExerciseParticipation) {
            return participationAuthCheckService.canAccessParticipation(programmingExerciseParticipation);
        }
        else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    String getOrRetrieveBranchOfDomainObject(Long participationID) {
        Participation participation = participationRepository.findByIdElseThrow(participationID);
        if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
            throw new IllegalArgumentException();
        }
        else if (participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
            return versionControlService.orElseThrow().getOrRetrieveBranchOfStudentParticipation(studentParticipation);
        }
        else {
            ProgrammingExercise programmingExercise = programmingExerciseRepository.getProgrammingExerciseFromParticipation(programmingParticipation);
            return versionControlService.orElseThrow().getOrRetrieveBranchOfExercise(programmingExercise);
        }
    }

    @Override
    @GetMapping(value = "repository/{participationId}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, FileType>> getFiles(@PathVariable Long participationId) {
        return super.getFiles(participationId);
    }

    /**
     * GET /repository/{participationId}/files-plagiarism-view : Gets the files of the repository with the given participationId for the plagiarism view.
     *
     * @param participationId the participationId of the repository we want to get the files from
     * @return a map with the file path as key and the file type as value
     */
    @GetMapping(value = "repository/{participationId}/files-plagiarism-view", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, FileType>> getFilesForPlagiarismView(@PathVariable Long participationId) {
        log.debug("REST request to files for plagiarism view for domainId : {}", participationId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = repositoryParticipationService.getRepositoryForPlagiarismView(participationId);
            Map<String, FileType> fileList = repositoryService.getFiles(repository);
            return new ResponseEntity<>(fileList, HttpStatus.OK);
        });
    }

    /**
     * GET /repository/{participationId}/files/{commitId} : Gets the files of the repository with the given participationId at the given commitId.
     * This enforces at least instructor access rights.
     *
     * @param participationId the participationId of the repository we want to get the files from
     * @param commitId        the commitId of the repository we want to get the files from
     * @param repositoryType  the type of the repository (template, solution, tests)
     * @return a map with the file path as key and the file content as value
     */
    @GetMapping(value = "repository-files-content/{commitId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, String>> getFilesAtCommit(@PathVariable String commitId, @RequestParam(required = false) Long participationId,
            @RequestParam(required = false) RepositoryType repositoryType) {
        log.debug("REST request to files for domainId {} at commitId {}", participationId, commitId);
        var participation = getProgrammingExerciseParticipation(participationId);
        var programmingExercise = programmingExerciseRepository.getProgrammingExerciseFromParticipationElseThrow(participation);
        repositoryAccessService.checkAccessRepositoryElseThrow(participation, userRepository.getUserWithGroupsAndAuthorities(), programmingExercise, RepositoryActionType.READ);

        return executeAndCheckForExceptions(() -> ResponseEntity.ok(repositoryService.getFilesContentAtCommit(programmingExercise, commitId, repositoryType, participation)));
    }

    /**
     * GET /repository/{participationId}/files-change
     * <p>
     * Gets the files of the repository and checks whether they were changed during a student participation with respect to the initial template
     *
     * @param participationId participation of the student
     * @return the ResponseEntity with status 200 (OK) and a map of files with the information if they were changed/are new.
     */
    @GetMapping(value = "repository/{participationId}/files-change", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<Map<String, Boolean>> getFilesWithInformationAboutChange(@PathVariable Long participationId) {
        return super.executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(participationId, RepositoryActionType.READ, true);
            var participation = participationRepository.findByIdElseThrow(participationId);
            var exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(participation.getExercise().getId());

            Repository templateRepository = getRepository(exercise.getTemplateParticipation().getId(), RepositoryActionType.READ, true);
            var filesWithInformationAboutChange = super.repositoryService.getFilesWithInformationAboutChange(repository, templateRepository);
            return new ResponseEntity<>(filesWithInformationAboutChange, HttpStatus.OK);
        });
    }

    @Override
    @GetMapping(value = "repository/{participationId}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getFile(@PathVariable Long participationId, @RequestParam("file") String filename) {
        return super.getFile(participationId, filename);
    }

    /**
     * GET /repository/{participationId}/file-plagiarism-view : Gets the file of the repository with the given participationId for the plagiarism view.
     *
     * @param participationId the participationId of the repository we want to get the file from
     * @param filename        the name of the file to retrieve
     * @return the file with the given filename
     */
    @GetMapping(value = "repository/{participationId}/file-plagiarism-view", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> getFileForPlagiarismView(@PathVariable Long participationId, @RequestParam("file") String filename) {
        log.debug("REST request to file {} for plagiarism view for domainId : {}", filename, participationId);

        return executeAndCheckForExceptions(() -> {
            Repository repository = repositoryParticipationService.getRepositoryForPlagiarismView(participationId);
            return repositoryService.getFileFromRepository(filename, repository);
        });
    }

    /**
     * GET /repository/{participationId}/files-content
     * <p>
     * Gets the files of the repository with content
     *
     * @param participationId participation of the student/template/solution
     * @return the ResponseEntity with status 200 (OK) and a map of files with their content
     */
    @GetMapping(value = "repository/{participationId}/files-content", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<Map<String, String>> getFilesWithContent(@PathVariable Long participationId) {
        return super.executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(participationId, RepositoryActionType.READ, true);
            var filesWithContent = super.repositoryService.getFilesContentFromWorkingCopy(repository);
            return new ResponseEntity<>(filesWithContent, HttpStatus.OK);
        });
    }

    /**
     * GET /repository/{participationId}/file-names: Gets the file names of the repository
     *
     * @param participationId participation of the student/template/solution
     * @return the ResponseEntity with status 200 (OK) and a set of file names
     */
    @GetMapping(value = "repository/{participationId}/file-names", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastTutor
    public ResponseEntity<Set<String>> getFileNames(@PathVariable Long participationId) {
        return super.executeAndCheckForExceptions(() -> {
            Repository repository = getRepository(participationId, RepositoryActionType.READ, true);
            var nonFolderFileNames = super.repositoryService.getFiles(repository).entrySet().stream().filter(mapEntry -> mapEntry.getValue().equals(FileType.FILE))
                    .map(Map.Entry::getKey).collect(Collectors.toSet());

            return new ResponseEntity<>(nonFolderFileNames, HttpStatus.OK);
        });
    }

    @Override
    @PostMapping(value = "repository/{participationId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.ProgrammingExercises)
    @EnforceAtLeastStudent
    public ResponseEntity<Void> createFile(@PathVariable Long participationId, @RequestParam("file") String filePath, HttpServletRequest request) {
        return super.createFile(participationId, filePath, request);
    }

    @Override
    @PostMapping(value = "repository/{participationId}/folder", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.ProgrammingExercises)
    @EnforceAtLeastStudent
    public ResponseEntity<Void> createFolder(@PathVariable Long participationId, @RequestParam("folder") String folderPath, HttpServletRequest request) {
        return super.createFolder(participationId, folderPath, request);
    }

    @Override
    @PostMapping(value = "repository/{participationId}/rename-file", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.ProgrammingExercises)
    @EnforceAtLeastStudent
    public ResponseEntity<Void> renameFile(@PathVariable Long participationId, @RequestBody FileMove fileMove) {
        return super.renameFile(participationId, fileMove);
    }

    @Override
    @DeleteMapping(value = "repository/{participationId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteFile(@PathVariable Long participationId, @RequestParam("file") String filename) {
        return super.deleteFile(participationId, filename);
    }

    @Override
    @GetMapping(value = "repository/{participationId}/pull", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    public ResponseEntity<Void> pullChanges(@PathVariable Long participationId) {
        return super.pullChanges(participationId);
    }

    /**
     * Update a list of files based on the submission's content.
     *
     * @param participationId id of participation to which the files belong
     * @param submissions     information about the file updates
     * @param commit          whether to commit after updating the files
     * @return {Map<String, String>} file submissions or the appropriate http error
     */
    @PutMapping("repository/{participationId}/files")
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, String>> updateParticipationFiles(@PathVariable("participationId") Long participationId, @RequestBody List<FileSubmission> submissions,
            @RequestParam(defaultValue = "false") boolean commit) {

        // Git repository must be available to update a file
        Repository repository;
        try {
            // Get the repository and also conduct access checks.
            repository = getRepository(participationId, RepositoryActionType.WRITE, true);
        }
        catch (EntityNotFoundException e) {
            // Participation was not found.
            FileSubmissionError error = new FileSubmissionError(participationId, "participationNotFound");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, error.getMessage(), error);
        }
        catch (IllegalArgumentException e) {
            // Participation is not instance of ProgrammingExerciseParticipation.
            FileSubmissionError error = new FileSubmissionError(participationId, "notAProgrammingExerciseParticipation");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error);
        }
        catch (CheckoutConflictException | WrongRepositoryStateException ex) {
            FileSubmissionError error = new FileSubmissionError(participationId, "checkoutConflict");
            throw new ResponseStatusException(HttpStatus.CONFLICT, error.getMessage(), error);
        }
        catch (GitAPIException ex) {
            FileSubmissionError error = new FileSubmissionError(participationId, "checkoutFailed");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, error.getMessage(), error);
        }
        catch (AccessForbiddenException e) {
            FileSubmissionError error = new FileSubmissionError(participationId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, error.getMessage(), error);
        }

        return saveFilesAndCommitChanges(participationId, submissions, commit, repository);
    }

    /**
     * Commit and push the changes to the remote VCS repo.
     * Won't allow a commit if the repository is locked!
     *
     * @param participationId identifier for the repository.
     * @return ok (200) if the push was successful, notFound (404) if the participation does not exist and forbidden (403) if the user does not have permissions to access the
     *         participation OR the buildAndTestAfterDueDate is set and the repository is now locked.
     */
    @Override
    @PostMapping(value = "repository/{participationId}/commit", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.ProgrammingExercises)
    @EnforceAtLeastStudent
    public ResponseEntity<Void> commitChanges(@PathVariable Long participationId) {
        return super.commitChanges(participationId);
    }

    @Override
    @PostMapping(value = "repository/{participationId}/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.ProgrammingExercises)
    @EnforceAtLeastStudent
    public ResponseEntity<Void> resetToLastCommit(@PathVariable Long participationId) {
        return super.resetToLastCommit(participationId);
    }

    @Override
    @GetMapping(value = "repository/{participationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    public ResponseEntity<RepositoryStatusDTO> getStatus(@PathVariable Long participationId) throws GitAPIException {
        return super.getStatus(participationId);
    }

    /**
     * GET /repository/:participationId/buildlogs : get the build log for the "participationId" repository.
     *
     * @param participationId to identify the repository with.
     * @param resultId        an optional result ID to get the build logs for the submission that the result belongs to. If the result ID is not specified, the latest submission is
     *                            used.
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    // TODO: rename to participation/{participationId}/buildlogs
    @GetMapping(value = "repository/{participationId}/buildlogs", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    public ResponseEntity<List<BuildLogEntry>> getBuildLogs(@PathVariable Long participationId, @RequestParam(name = "resultId") Optional<Long> resultId) {
        log.debug("REST request to get build log : {}", participationId);

        ProgrammingExerciseParticipation participation = participationService.findProgrammingExerciseParticipationWithLatestSubmissionAndResult(participationId);
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);

        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) participation.getSubmissions().stream().findFirst().orElse(null);
        // If a resultId is specified and the ID does not belong to the latest result, find the corresponding submission. Otherwise use the latest submission.
        if (resultId.isPresent() && (programmingSubmission == null || programmingSubmission.getResults().stream().noneMatch(r -> resultId.get().equals(r.getId())))) {
            // Note: if the submission was null, this will fail with a good message.
            programmingSubmission = programmingSubmissionRepository.findByResultIdElseThrow(resultId.get());
            if (!Objects.equals(participation.getId(), programmingSubmission.getParticipation().getId())) {
                // The result of the given ID must belong to the participation
                log.warn("Participation ID {} tried to access the build logs of another participation's submission with ID {}.", participation.getId(),
                        programmingSubmission.getId());
                throw new AccessForbiddenException("No permission to access the build log of another participation's submission");
            }
        }
        else if (programmingSubmission == null) {
            // Can't return build logs if a submission doesn't exist yet
            return ResponseEntity.ok(new ArrayList<>());
        }

        // Do not return build logs if the build hasn't failed
        if (!programmingSubmission.isBuildFailed()) {
            throw new AccessForbiddenException("Build logs cannot be retrieved when the build hasn't failed!");
        }

        // Load the logs from the database
        List<BuildLogEntry> buildLogs = buildLogService.getLatestBuildLogs(programmingSubmission);
        return new ResponseEntity<>(buildLogs, HttpStatus.OK);
    }
}
