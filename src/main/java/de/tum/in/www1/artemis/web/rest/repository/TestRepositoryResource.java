package de.tum.in.www1.artemis.web.rest.repository;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.FileType;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;

/**
 * Executes requested actions on the test repository of a programming exercise. Only available to TAs, Instructors and Admins.
 */
@RestController
@RequestMapping({ "/api", "/api_basic" })
@PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
public class TestRepositoryResource extends RepositoryResource {

    private final ExerciseService exerciseService;

    private final Optional<VersionControlService> versionControlService;

    public TestRepositoryResource(UserService userService, AuthorizationCheckService authCheckService, Optional<GitService> gitService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, RepositoryService repositoryService, ExerciseService exerciseService,
            Optional<VersionControlService> versionControlService) {
        super(userService, authCheckService, gitService, continuousIntegrationService, repositoryService);
        this.exerciseService = exerciseService;
        this.versionControlService = versionControlService;
    }

    @Override
    Repository getRepository(Long exerciseId, RepositoryActionType repositoryActionType, boolean pullOnGet)
            throws IOException, IllegalAccessException, InterruptedException, GitAPIException {
        final var exercise = (ProgrammingExercise) exerciseService.findOneWithAdditionalElements(exerciseId);
        final var repoUrl = exercise.getTestRepositoryUrlAsUrl();

        return repositoryService.checkoutRepositoryByName(exercise, repoUrl, pullOnGet);
    }

    @Override
    URL getRepositoryUrl(Long exerciseId) {
        ProgrammingExercise exercise = (ProgrammingExercise) exerciseService.findOneWithAdditionalElements(exerciseId);
        return exercise.getTestRepositoryUrlAsUrl();
    }

    @Override
    boolean canAccessRepository(Long exerciseId) {
        ProgrammingExercise exercise = (ProgrammingExercise) exerciseService.findOneWithAdditionalElements(exerciseId);
        return authCheckService.isAtLeastInstructorInCourse(exercise.getCourse(), userService.getUserWithGroupsAndAuthorities());
    }

    @Override
    @GetMapping(value = "/test-repository/{exerciseId}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HashMap<String, FileType>> getFiles(@PathVariable Long exerciseId) {
        return super.getFiles(exerciseId);
    }

    @Override
    @GetMapping(value = "/test-repository/{exerciseId}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getFile(@PathVariable Long exerciseId, @RequestParam("file") String filename) {
        return super.getFile(exerciseId, filename);
    }

    @Override
    @PostMapping(value = "/test-repository/{exerciseId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> createFile(@PathVariable Long exerciseId, @RequestParam("file") String filename, HttpServletRequest request) {
        return super.createFile(exerciseId, filename, request);
    }

    @Override
    @PostMapping(value = "/test-repository/{exerciseId}/folder", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> createFolder(@PathVariable Long exerciseId, @RequestParam("folder") String folderName, HttpServletRequest request) {
        return super.createFolder(exerciseId, folderName, request);
    }

    @Override
    @PostMapping(value = "/test-repository/{exerciseId}/rename-file", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> renameFile(@PathVariable Long exerciseId, @RequestBody FileMove fileMove) {
        return super.renameFile(exerciseId, fileMove);
    }

    @Override
    @DeleteMapping(value = "/test-repository/{exerciseId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> deleteFile(@PathVariable Long exerciseId, @RequestParam("file") String filename) {
        return super.deleteFile(exerciseId, filename);
    }

    @Override
    @GetMapping(value = "/test-repository/{exerciseId}/pull", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> pullChanges(@PathVariable Long exerciseId) {
        return super.pullChanges(exerciseId);
    }

    @Override
    @PostMapping(value = "/test-repository/{exerciseId}/commit", produces = MediaType.APPLICATION_JSON_VALUE)
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> commitChanges(@PathVariable Long exerciseId) {
        return super.commitChanges(exerciseId);
    }

    @Override
    @GetMapping(value = "/test-repository/{exerciseId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RepositoryStatusDTO> getStatus(@PathVariable Long exerciseId) throws IOException, GitAPIException, InterruptedException {
        return super.getStatus(exerciseId);
    }
}
