package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.TimeLogUtil.formatDurationFrom;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exam.api.ExamAccessApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitRepositoryExportService;

/**
 * REST resource exposing repository export endpoints for programming exercises.
 *
 * <p>
 * Provides instructor exports (template/solution/tests and auxiliary repositories),
 * student-requested solution/tests exports (without .git), and student repository exports.
 */
@Profile(PROFILE_CORE)
@FeatureToggle(Feature.ProgrammingExercises)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class ProgrammingExerciseRepositoryExportResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseRepositoryExportResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final Optional<ExamAccessApi> examAccessApi;

    private final GitRepositoryExportService gitRepositoryExportService;

    public ProgrammingExerciseRepositoryExportResource(ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, Optional<ExamAccessApi> examAccessApi, GitRepositoryExportService gitRepositoryExportService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.examAccessApi = examAccessApi;
        this.gitRepositoryExportService = gitRepositoryExportService;
    }

    /**
     * GET /programming-exercises/:exerciseId/export-instructor-repository/:repositoryType : sends a test, solution or template repository as a zip file
     *
     * @param exerciseId     The id of the programming exercise
     * @param repositoryType The type of repository to zip and send
     * @return ResponseEntity with status
     * @throws IOException if something during the zip process went wrong
     */
    @GetMapping("programming-exercises/{exerciseId}/export-instructor-repository/{repositoryType}")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "exerciseId")
    @FeatureToggle(Feature.Exports)
    public ResponseEntity<Resource> exportInstructorRepository(@PathVariable long exerciseId, @PathVariable RepositoryType repositoryType) throws IOException {
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise", exerciseId));
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);

        long start = System.nanoTime();

        InputStreamResource resource = gitRepositoryExportService.exportInstructorRepositoryForExerciseInMemory(programmingExercise, repositoryType);

        log.info("Export of the repository of type {} programming exercise {} with title '{}' was successful in {}.", repositoryType, programmingExercise.getId(),
                programmingExercise.getTitle(), formatDurationFrom(start));

        return ResponseEntity.ok().contentLength(resource.contentLength()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", resource.getFilename())
                .body(resource);
    }

    /**
     * GET /programming-exercises/:exerciseId/export-instructor-auxiliary-repository/:repositoryType : sends an auxiliary repository as a zip file
     *
     * @param exerciseId   The id of the programming exercise
     * @param repositoryId The id of the auxiliary repository
     * @return ResponseEntity with status
     * @throws IOException if something during the zip process went wrong
     */
    @GetMapping("programming-exercises/{exerciseId}/export-instructor-auxiliary-repository/{repositoryId}")
    @EnforceAtLeastTutorInExercise(resourceIdFieldName = "exerciseId")
    @FeatureToggle(Feature.Exports)
    public ResponseEntity<Resource> exportInstructorAuxiliaryRepository(@PathVariable long exerciseId, @PathVariable long repositoryId) throws IOException {
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);

        AuxiliaryRepository auxiliaryRepository = auxiliaryRepositoryRepository.findByIdElseThrow(repositoryId);

        long start = System.nanoTime();

        if (auxiliaryRepository.getVcsRepositoryUri() == null) {
            return ResponseEntity.unprocessableEntity()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "repositoryNotConfigured", "The auxiliary repository is not configured correctly."))
                    .body(null);
        }

        InputStreamResource resource = gitRepositoryExportService.exportInstructorAuxiliaryRepositoryForExerciseInMemory(programmingExercise, auxiliaryRepository);

        log.info("Export of auxiliary repository {} for programming exercise {} with title '{}' was successful in {}.", auxiliaryRepository.getName(), programmingExercise.getId(),
                programmingExercise.getTitle(), formatDurationFrom(start));

        return ResponseEntity.ok().contentLength(resource.contentLength()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", resource.getFilename())
                .body(resource);
    }

    /**
     * GET /programming-exercises/:exerciseId/export-student-requested-repository : sends a solution repository as a zip file without .git directory.
     *
     * @param exerciseId   The id of the programming exercise
     * @param includeTests flag that indicates whether the tests should also be exported
     * @return ResponseEntity with status
     * @throws IOException if something during the zip process went wrong
     */
    @GetMapping("programming-exercises/{exerciseId}/export-student-requested-repository")
    @EnforceAtLeastStudentInExercise(resourceIdFieldName = "exerciseId")
    @FeatureToggle(Feature.Exports)
    public ResponseEntity<Resource> exportStudentRequestedRepository(@PathVariable long exerciseId, @RequestParam() boolean includeTests) throws IOException {
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise", exerciseId));
        if (programmingExercise.isExamExercise()) {
            ExamAccessApi api = examAccessApi.orElseThrow(() -> new ExamApiNotPresentException(ExamAccessApi.class));
            api.checkExamExerciseForExampleSolutionAccessElseThrow(programmingExercise);
        }
        Role atLeastRole = programmingExercise.isExampleSolutionPublished() ? Role.STUDENT : Role.TEACHING_ASSISTANT;
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(atLeastRole, programmingExercise, null);
        if (includeTests && !programmingExercise.isReleaseTestsWithExampleSolution()) {
            throw new AccessForbiddenException(RepositoryType.TESTS.getName(), programmingExercise.getId());
        }
        long start = System.nanoTime();

        RepositoryType repositoryType = includeTests ? RepositoryType.TESTS : RepositoryType.SOLUTION;
        VcsRepositoryUri repositoryUri = programmingExercise.getRepositoryURI(repositoryType);
        if (repositoryUri == null) {
            return ResponseEntity.internalServerError().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError",
                    "Failed to export repository because the repository URI is not defined.")).body(null);
        }

        try {
            String zippedRepoName = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getShortName() + "-" + programmingExercise.getTitle() + "-"
                    + repositoryType.getName();
            zippedRepoName = FileUtil.sanitizeFilename(zippedRepoName);

            InputStreamResource zipResource = gitRepositoryExportService.exportRepositorySnapshot(repositoryUri, zippedRepoName);

            log.info("Successfully exported repository for programming exercise {} with title {} in {} ms", programmingExercise.getId(), programmingExercise.getTitle(),
                    (System.nanoTime() - start) / 1000000);

            return ResponseEntity.ok().contentLength(zipResource.contentLength()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipResource.getFilename())
                    .body(zipResource);
        }
        catch (GitAPIException e) {
            log.error("Failed to export repository: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError", "Failed to export repository: " + e.getMessage())).body(null);
        }
    }

    /**
     * GET /programming-exercises/:exerciseId/export-student-repository/:participationId : Exports the repository belonging to a participation as a zip file.
     *
     * @param exerciseId      The id of the programming exercise
     * @param participationId The id of the student participation for which to export the repository.
     * @return A ResponseEntity containing the zipped repository.
     * @throws IOException If the repository could not be zipped.
     */
    @GetMapping("programming-exercises/{exerciseId}/export-student-repository/{participationId}")
    @EnforceAtLeastStudentInExercise(resourceIdFieldName = "exerciseId")
    @FeatureToggle(Feature.Exports)
    public ResponseEntity<Resource> exportStudentRepository(@PathVariable long exerciseId, @PathVariable long participationId) throws IOException {
        var programmingExercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(exerciseId);
        var studentParticipation = programmingExercise.getStudentParticipations().stream().filter(p -> p.getId().equals(participationId))
                .map(p -> (ProgrammingExerciseStudentParticipation) p).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("No student participation with id " + participationId + " was found for programming exercise " + exerciseId));
        if (!authCheckService.isOwnerOfParticipation(studentParticipation)) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);
        }
        var exportErrors = new ArrayList<String>();
        long start = System.nanoTime();

        InputStreamResource resource = gitRepositoryExportService.exportStudentRepositoryInMemory(programmingExercise, studentParticipation, exportErrors);

        if (resource == null) {
            throw new de.tum.cit.aet.artemis.core.exception.InternalServerErrorException(
                    "Could not export the student repository of participation " + participationId + ". Logged errors: " + exportErrors);
        }

        log.info("Export of student repository for participation {} in programming exercise {} with title '{}' was successful in {}.", participationId, programmingExercise.getId(),
                programmingExercise.getTitle(), formatDurationFrom(start));

        return ResponseEntity.ok().contentLength(resource.contentLength()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", resource.getFilename())
                .body(resource);
    }
}
