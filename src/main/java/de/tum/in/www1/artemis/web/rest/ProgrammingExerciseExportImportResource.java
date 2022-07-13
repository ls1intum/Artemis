package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.service.util.TimeLogUtil.formatDurationFrom;
import static de.tum.in.www1.artemis.web.rest.ProgrammingExerciseResourceEndpoints.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.SubmissionPolicyService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.programming.*;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing ProgrammingExercise.
 */
@RestController
@RequestMapping(ROOT)
public class ProgrammingExerciseExportImportResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseExportImportResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingExerciseImportService programmingExerciseImportService;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService;

    private final TemplateUpgradePolicy templateUpgradePolicy;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final SubmissionPolicyService submissionPolicyService;

    public ProgrammingExerciseExportImportResource(ProgrammingExerciseRepository programmingExerciseRepository, UserRepository userRepository,
            AuthorizationCheckService authCheckService, CourseService courseService, ProgrammingExerciseService programmingExerciseService,
            ProgrammingExerciseImportService programmingExerciseImportService, ProgrammingExerciseExportService programmingExerciseExportService,
            Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService, TemplateUpgradePolicy templateUpgradePolicy,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, SubmissionPolicyService submissionPolicyService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingExerciseImportService = programmingExerciseImportService;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.templateUpgradePolicy = templateUpgradePolicy;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.submissionPolicyService = submissionPolicyService;
    }

    /**
     * Validates static code analysis settings
     *
     * @param programmingExercise exercise to validate
     */
    private void validateStaticCodeAnalysisSettings(ProgrammingExercise programmingExercise) {
        ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.get()
                .getProgrammingLanguageFeatures(programmingExercise.getProgrammingLanguage());
        programmingExercise.validateStaticCodeAnalysisSettings(programmingLanguageFeature);
    }

    /**
     * POST /programming-exercises/import: Imports an existing programming exercise into an existing course
     * <p>
     * This will import the whole exercise, including all base build plans (template, solution) and repositories
     * (template, solution, test). Referenced entities, s.a. the test cases or the hints will get cloned and assigned
     * a new id. For a concrete list of what gets copied and what not have a look
     * at {@link ProgrammingExerciseImportService#importProgrammingExerciseBasis(ProgrammingExercise, ProgrammingExercise)}
     *
     * @param sourceExerciseId   The ID of the original exercise which should get imported
     * @param newExercise        The new exercise containing values that should get overwritten in the imported exercise, s.a. the title or difficulty
     * @param recreateBuildPlans Option determining whether the build plans should be copied or re-created from scratch
     * @param updateTemplate     Option determining whether the template files should be updated with the most recent template version
     * @return The imported exercise (200), a not found error (404) if the template does not exist, or a forbidden error
     * (403) if the user is not at least an instructor in the target course.
     * @see ProgrammingExerciseImportService#importProgrammingExerciseBasis(ProgrammingExercise, ProgrammingExercise)
     * @see ProgrammingExerciseImportService#importBuildPlans(ProgrammingExercise, ProgrammingExercise)
     * @see ProgrammingExerciseImportService#importRepositories(ProgrammingExercise, ProgrammingExercise)
     */
    @PostMapping(IMPORT)
    @PreAuthorize("hasRole('EDITOR')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExercise> importProgrammingExercise(@PathVariable long sourceExerciseId, @RequestBody ProgrammingExercise newExercise,
            @RequestParam(defaultValue = "false") boolean recreateBuildPlans, @RequestParam(defaultValue = "false") boolean updateTemplate) {
        if (sourceExerciseId < 0) {
            throw new BadRequestAlertException("Invalid source id when importing programming exercises", ENTITY_NAME, "invalidSourceExerciseId");
        }

        // Valid exercises have set either a course or an exerciseGroup
        newExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        log.debug("REST request to import programming exercise {} into course {}", sourceExerciseId, newExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        newExercise.validateGeneralSettings();
        newExercise.validateProgrammingSettings();
        validateStaticCodeAnalysisSettings(newExercise);

        final var user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(newExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        // Validate course settings
        programmingExerciseRepository.validateCourseSettings(newExercise, course);

        final var originalProgrammingExercise = programmingExerciseRepository
                .findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxReposAndTasksWithTestCases(sourceExerciseId)
                .orElseThrow(() -> new EntityNotFoundException("ProgrammingExercise", sourceExerciseId));

        // The static code analysis flag can only change, if the build plans are recreated and the template is upgraded
        if (newExercise.isStaticCodeAnalysisEnabled() != originalProgrammingExercise.isStaticCodeAnalysisEnabled() && !(recreateBuildPlans && updateTemplate)) {
            throw new BadRequestAlertException("Static code analysis can only change, if the recreation of build plans and update of template files is activated", ENTITY_NAME,
                    "staticCodeAnalysisCannotChange");
        }

        // If the new exercise has a submission policy, it must be validated.
        if (newExercise.getSubmissionPolicy() != null) {
            submissionPolicyService.validateSubmissionPolicy(newExercise.getSubmissionPolicy());
        }

        // Check if the user has the rights to access the original programming exercise
        Course originalCourse = courseService.retrieveCourseOverExerciseGroupOrCourseId(originalProgrammingExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, originalCourse, user);

        final ProgrammingExercise importedProgrammingExercise = programmingExerciseImportService.importProgrammingExercise(originalProgrammingExercise, newExercise,
                updateTemplate);

        HttpHeaders responseHeaders;
        if (programmingExerciseImportService.importBuildPlansForProgrammingExercise(originalProgrammingExercise, importedProgrammingExercise, recreateBuildPlans)) {
            responseHeaders = HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, importedProgrammingExercise.getTitle());
        }
        else {
            responseHeaders = HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "importExerciseTriggerPlanFail", "Unable to trigger imported build plans");
        }

        importedProgrammingExercise.setTestCases(null);
        importedProgrammingExercise.setStaticCodeAnalysisCategories(null);
        importedProgrammingExercise.setTemplateParticipation(null);
        importedProgrammingExercise.setSolutionParticipation(null);
        importedProgrammingExercise.setExerciseHints(null);
        importedProgrammingExercise.setTasks(null);

        return ResponseEntity.ok().headers(responseHeaders).body(importedProgrammingExercise);
    }

    /**
     * GET /programming-exercises/:exerciseId/export-instructor-exercise
     *
     * @param exerciseId The id of the programming exercise
     * @return ResponseEntity with status
     * @throws IOException if something during the zip process went wrong
     */
    @GetMapping(EXPORT_INSTRUCTOR_EXERCISE)
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Resource> exportInstructorExercise(@PathVariable long exerciseId) throws IOException {
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, null);

        long start = System.nanoTime();
        var path = programmingExerciseExportService.exportProgrammingExerciseInstructorMaterial(programmingExercise, new ArrayList<>());
        if (path == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError",
                    "There was an error on the server and the zip file could not be created.")).body(null);
        }
        var finalZipFile = path.toFile();

        InputStreamResource resource = new InputStreamResource(new FileInputStream(finalZipFile));

        log.info("Export of the programming exercise {} with title '{}' was successful in {}.", programmingExercise.getId(), programmingExercise.getTitle(),
                formatDurationFrom(start));

        return ResponseEntity.ok().contentLength(finalZipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", finalZipFile.getName()).body(resource);
    }

    /**
     * GET /programming-exercises/:exerciseId/export-instructor-repository/:repositoryType : sends a test, solution or template repository as a zip file
     *
     * @param exerciseId     The id of the programming exercise
     * @param repositoryType The type of repository to zip and send
     * @return ResponseEntity with status
     * @throws IOException if something during the zip process went wrong
     */
    @GetMapping(EXPORT_INSTRUCTOR_REPOSITORY)
    @PreAuthorize("hasRole('TA')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Resource> exportInstructorRepository(@PathVariable long exerciseId, @PathVariable RepositoryType repositoryType) throws IOException {
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);

        long start = System.nanoTime();
        Optional<File> zipFile = programmingExerciseExportService.exportInstructorRepositoryForExercise(programmingExercise.getId(), repositoryType, new ArrayList<>());

        return returnZipFileForRepositoryExport(zipFile, repositoryType.getName(), programmingExercise, start);
    }

    /**
     * GET /programming-exercises/:exerciseId/export-instructor-auxiliary-repository/:repositoryType : sends an auxiliary repository as a zip file
     *
     * @param exerciseId   The id of the programming exercise
     * @param repositoryId The id of the auxiliary repository
     * @return ResponseEntity with status
     * @throws IOException if something during the zip process went wrong
     */
    @GetMapping(EXPORT_INSTRUCTOR_AUXILIARY_REPOSITORY)
    @PreAuthorize("hasRole('TA')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Resource> exportInstructorAuxiliaryRepository(@PathVariable long exerciseId, @PathVariable long repositoryId) throws IOException {
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);

        Optional<AuxiliaryRepository> optionalAuxiliaryRepository = auxiliaryRepositoryRepository.findById(repositoryId);

        if (optionalAuxiliaryRepository.isEmpty()) {
            return ResponseEntity.notFound().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError",
                    "There was an error on the server and the URL of the auxiliary couldn't be retrieved.")).build();
        }

        AuxiliaryRepository auxiliaryRepository = optionalAuxiliaryRepository.get();

        long start = System.nanoTime();
        Optional<File> zipFile = programmingExerciseExportService.exportInstructorAuxiliaryRepositoryForExercise(programmingExercise.getId(), auxiliaryRepository,
                new ArrayList<>());
        return returnZipFileForRepositoryExport(zipFile, auxiliaryRepository.getName(), programmingExercise, start);
    }

    private ResponseEntity<Resource> returnZipFileForRepositoryExport(Optional<File> zipFile, String repositoryName, ProgrammingExercise exercise, long startTime)
            throws IOException {
        if (zipFile.isEmpty()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError",
                    "There was an error on the server and the zip file could not be created.")).body(null);
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile.get()));

        log.info("Export of the repository of type {} programming exercise {} with title '{}' was successful in {}.", repositoryName, exercise.getId(), exercise.getTitle(),
                formatDurationFrom(startTime));

        return ResponseEntity.ok().contentLength(zipFile.get().length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.get().getName()).body(resource);
    }

    /**
     * POST /programming-exercises/:exerciseId/export-repos-by-participant-identifiers/:participantIdentifiers : sends all submissions from participantIdentifiers as zip
     *
     * @param exerciseId              the id of the exercise to get the repos from
     * @param participantIdentifiers  the identifiers of the participants (student logins or team short names) for whom to zip the submissions, separated by commas
     * @param repositoryExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     * @throws IOException if something during the zip process went wrong
     */
    @PostMapping(EXPORT_SUBMISSIONS_BY_PARTICIPANTS)
    @PreAuthorize("hasRole('TA')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Resource> exportSubmissionsByStudentLogins(@PathVariable long exerciseId, @PathVariable String participantIdentifiers,
            @RequestBody RepositoryExportOptionsDTO repositoryExportOptions) throws IOException {
        var programmingExercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, user);
        if (repositoryExportOptions.isExportAllParticipants()) {
            // only instructors are allowed to download all repos
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, user);
        }

        if (repositoryExportOptions.getFilterLateSubmissionsDate() == null) {
            repositoryExportOptions.setFilterLateSubmissionsIndividualDueDate(true);
            repositoryExportOptions.setFilterLateSubmissionsDate(programmingExercise.getDueDate());
        }

        List<String> participantIdentifierList = new ArrayList<>();
        if (!repositoryExportOptions.isExportAllParticipants()) {
            participantIdentifiers = participantIdentifiers.replaceAll("\\s+", "");
            participantIdentifierList = Arrays.asList(participantIdentifiers.split(","));
        }

        // Select the participations that should be exported
        List<ProgrammingExerciseStudentParticipation> exportedStudentParticipations = new ArrayList<>();
        for (StudentParticipation studentParticipation : programmingExercise.getStudentParticipations()) {
            ProgrammingExerciseStudentParticipation programmingStudentParticipation = (ProgrammingExerciseStudentParticipation) studentParticipation;
            if (repositoryExportOptions.isExportAllParticipants() || (programmingStudentParticipation.getRepositoryUrl() != null && studentParticipation.getParticipant() != null
                    && participantIdentifierList.contains(studentParticipation.getParticipantIdentifier()))) {
                exportedStudentParticipations.add(programmingStudentParticipation);
            }
        }
        return provideZipForParticipations(exportedStudentParticipations, programmingExercise, repositoryExportOptions);
    }

    /**
     * POST /programming-exercises/:exerciseId/export-repos-by-participation-ids/:participationIds : sends all submissions from participation ids as zip
     *
     * @param exerciseId              the id of the exercise to get the repos from
     * @param participationIds        the participationIds seperated via semicolon to get their submissions (used for double-blind assessment)
     * @param repositoryExportOptions the options that should be used for the export. Export all students is not supported here!
     * @return ResponseEntity with status
     * @throws IOException if submissions can't be zippedRequestBody
     */
    @PostMapping(EXPORT_SUBMISSIONS_BY_PARTICIPATIONS)
    @PreAuthorize("hasRole('TA')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Resource> exportSubmissionsByParticipationIds(@PathVariable long exerciseId, @PathVariable String participationIds,
            @RequestBody RepositoryExportOptionsDTO repositoryExportOptions) throws IOException {
        var programmingExercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);

        // Only instructors or higher may override the anonymization setting
        if (!authCheckService.isAtLeastInstructorForExercise(programmingExercise, null)) {
            repositoryExportOptions.setAnonymizeStudentCommits(true);
        }

        if (repositoryExportOptions.getFilterLateSubmissionsDate() == null) {
            repositoryExportOptions.setFilterLateSubmissionsIndividualDueDate(true);
            repositoryExportOptions.setFilterLateSubmissionsDate(programmingExercise.getDueDate());
        }

        var participationIdSet = new ArrayList<>(Arrays.asList(participationIds.split(","))).stream().map(String::trim).map(Long::parseLong).collect(Collectors.toSet());

        // Select the participations that should be exported
        List<ProgrammingExerciseStudentParticipation> exportedStudentParticipations = programmingExercise.getStudentParticipations().stream()
                .filter(participation -> participationIdSet.contains(participation.getId())).map(participation -> (ProgrammingExerciseStudentParticipation) participation)
                .collect(Collectors.toList());
        return provideZipForParticipations(exportedStudentParticipations, programmingExercise, repositoryExportOptions);
    }

    private ResponseEntity<Resource> provideZipForParticipations(@NotNull List<ProgrammingExerciseStudentParticipation> exportedStudentParticipations,
            ProgrammingExercise programmingExercise, RepositoryExportOptionsDTO repositoryExportOptions) throws IOException {

        long start = System.nanoTime();

        // TODO: in case we do not find participations for the given ids, we should inform the user in the client, that the student did not participate in the exercise.
        if (exportedStudentParticipations.isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, false, ENTITY_NAME, "noparticipations", "No existing user was specified or no submission exists."))
                    .body(null);
        }

        File zipFile = programmingExerciseExportService.exportStudentRepositoriesToZipFile(programmingExercise.getId(), exportedStudentParticipations, repositoryExportOptions);
        if (zipFile == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError",
                    "There was an error on the server and the zip file could not be created.")).body(null);
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));

        log.info("Export {} student repositories of programming exercise {} with title '{}' was successful in {}.", exportedStudentParticipations.size(),
                programmingExercise.getId(), programmingExercise.getTitle(), formatDurationFrom(start));

        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
    }

    /**
     * GET /programming-exercises/:exerciseId/export-solution-repository : sends a solution repository as a zip file without .git directory.
     *
     * @param exerciseId The id of the programming exercise
     * @return ResponseEntity with status
     * @throws IOException if something during the zip process went wrong
     */
    @GetMapping(EXPORT_SOLUTION_REPOSITORY)
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Resource> exportSolutionRepository(@PathVariable long exerciseId) throws IOException {
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        Role atLeastRole = programmingExercise.isExampleSolutionPublished() ? Role.STUDENT : Role.TEACHING_ASSISTANT;
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(atLeastRole, programmingExercise, null);
        long start = System.nanoTime();
        Optional<File> zipFile = programmingExerciseExportService.exportSolutionRepositoryForExercise(programmingExercise.getId(), new ArrayList<>());

        return returnZipFileForRepositoryExport(zipFile, RepositoryType.SOLUTION.getName(), programmingExercise, start);
    }
}
