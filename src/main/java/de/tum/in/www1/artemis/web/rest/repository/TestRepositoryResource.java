package de.tum.in.www1.artemis.web.rest.repository;

import java.io.IOException;
import java.net.URL;
import java.util.*;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.web.rest.FileMove;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryStatusDTO;

@RestController
@RequestMapping({ "/api", "/api_basic" })
@PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
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
    Repository getRepository(Long exerciseId) throws IOException, IllegalAccessException, InterruptedException {
        ProgrammingExercise exercise = (ProgrammingExercise) exerciseService.findOne(exerciseId);
        String testRepoName = exercise.getProjectKey().toLowerCase() + "-tests";
        URL testsRepoUrl = versionControlService.get().getCloneURL(exercise.getProjectKey(), testRepoName);
        return repositoryService.checkoutRepositoryByName(exercise, testsRepoUrl);
    }

    /**
     * GET /repository/{participationId}/files: Map of all file and folders of the repository. Each entry states if it is a file or a folder.
     *
     * @param participationId Participation ID
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/test-repository/{participationId}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HashMap<String, FileType>> getFiles(@PathVariable Long participationId) throws IOException, InterruptedException {
        return super.getFiles(participationId);
    }

    /**
     * GET /repository/{participationId}/file: Get the content of a file
     *
     * @param participationId Participation ID
     * @param filename
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/test-repository/{participationId}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<String> getFile(@PathVariable Long participationId, @RequestParam("file") String filename) throws IOException, InterruptedException {
        return super.getFile(participationId, filename);
    }

    /**
     * POST /repository/{participationId}/file: Create new file
     *
     * @param participationId Participation ID
     * @param filename
     * @param request
     * @return
     * @throws IOException
     */
    @PostMapping(value = "/test-repository/{participationId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createFile(@PathVariable Long participationId, @RequestParam("file") String filename, HttpServletRequest request)
            throws IOException, InterruptedException {
        return super.createFile(participationId, filename, request);
    }

    /**
     * POST /repository/{participationId}/folder: Create new folder
     *
     * @param participationId Participation ID
     * @param folderName
     * @param request
     * @return
     * @throws IOException
     */
    @PostMapping(value = "/test-repository/{participationId}/folder", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createFolder(@PathVariable Long participationId, @RequestParam("folder") String folderName, HttpServletRequest request)
            throws IOException, InterruptedException {
        return super.createFolder(participationId, folderName, request);
    }

    /**
     * Change the name of a file.
     *
     * @param participationId id of the participation the git repository belongs to.
     * @param fileMove        defines current and new path in git repository.
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    @PostMapping(value = "/test-repository/{participationId}/rename-file", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> renameFile(@PathVariable Long participationId, @RequestBody FileMove fileMove) throws IOException, InterruptedException {
        return super.renameFile(participationId, fileMove);
    }

    /**
     * DELETE /repository/{participationId}/file: Delete the file or the folder specified. If the path is a folder, all files in it will be deleted, too.
     *
     * @param participationId Participation ID
     * @param filename        path of file or folder to delete.
     * @return
     * @throws IOException
     */
    @DeleteMapping(value = "/test-repository/{participationId}/file", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deleteFile(@PathVariable Long participationId, @RequestParam("file") String filename) throws IOException, InterruptedException {
        return super.deleteFile(participationId, filename);
    }

    /**
     * GET /repository/{participationId}/pull: Pull into the participation repository
     *
     * @param participationId Participation ID
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/test-repository/{participationId}/pull", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> pullChanges(@PathVariable Long participationId) throws IOException, InterruptedException {
        return super.pullChanges(participationId);
    }

    /**
     * POST /repository/{participationId}/commit: Commit into the participation repository
     *
     * @param participationId Participation ID
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    @PostMapping(value = "/test-repository/{participationId}/commit", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> commitChanges(@PathVariable Long participationId) throws IOException, InterruptedException {
        return super.commitChanges(participationId);
    }

    /**
     * GET /repository/{participationId}: Get the "clean" status of the repository. Clean = No uncommitted changes.
     *
     * @param participationId Participation ID
     * @return
     * @throws IOException
     * @throws GitAPIException
     */
    @GetMapping(value = "/test-repository/{participationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RepositoryStatusDTO> getStatus(@PathVariable Long participationId) throws IOException, GitAPIException, InterruptedException {
        return super.getStatus(participationId);
    }
}
