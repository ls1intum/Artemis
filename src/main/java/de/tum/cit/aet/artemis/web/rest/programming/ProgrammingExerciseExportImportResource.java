package de.tum.cit.aet.artemis.web.rest.programming;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.service.util.TimeLogUtil.formatDurationFrom;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.RepositoryType;
import de.tum.cit.aet.artemis.domain.enumeration.Visibility;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ConsistencyCheckService;
import de.tum.cit.aet.artemis.service.CourseService;
import de.tum.cit.aet.artemis.service.SubmissionPolicyService;
import de.tum.cit.aet.artemis.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.service.connectors.athena.AthenaModuleService;
import de.tum.cit.aet.artemis.service.exam.ExamAccessService;
import de.tum.cit.aet.artemis.service.export.ProgrammingExerciseExportService;
import de.tum.cit.aet.artemis.service.feature.Feature;
import de.tum.cit.aet.artemis.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.service.programming.ProgrammingExerciseImportFromFileService;
import de.tum.cit.aet.artemis.service.programming.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.cit.aet.artemis.service.programming.ProgrammingLanguageFeatureService;
import de.tum.cit.aet.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.ConflictException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.cit.aet.artemis.web.rest.errors.HttpStatusException;
import de.tum.cit.aet.artemis.web.rest.errors.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.InternalServerErrorException;
import de.tum.cit.aet.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing ProgrammingExercise.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ProgrammingExerciseExportImportResource {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseExportImportResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    private final CompetencyProgressService competencyProgressService;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseImportService programmingExerciseImportService;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final SubmissionPolicyService submissionPolicyService;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ExamAccessService examAccessService;

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseImportFromFileService programmingExerciseImportFromFileService;

    private final ConsistencyCheckService consistencyCheckService;

    private final Optional<AthenaModuleService> athenaModuleService;

    public ProgrammingExerciseExportImportResource(ProgrammingExerciseRepository programmingExerciseRepository, UserRepository userRepository,
            AuthorizationCheckService authCheckService, CourseService courseService, ProgrammingExerciseImportService programmingExerciseImportService,
            ProgrammingExerciseExportService programmingExerciseExportService, Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, SubmissionPolicyService submissionPolicyService,
            ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, ExamAccessService examAccessService, CourseRepository courseRepository,
            ProgrammingExerciseImportFromFileService programmingExerciseImportFromFileService, ConsistencyCheckService consistencyCheckService,
            Optional<AthenaModuleService> athenaModuleService, CompetencyProgressService competencyProgressService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.programmingExerciseImportService = programmingExerciseImportService;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.submissionPolicyService = submissionPolicyService;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.examAccessService = examAccessService;
        this.courseRepository = courseRepository;
        this.programmingExerciseImportFromFileService = programmingExerciseImportFromFileService;
        this.consistencyCheckService = consistencyCheckService;
        this.athenaModuleService = athenaModuleService;
        this.competencyProgressService = competencyProgressService;
    }

    /**
     * Validates static code analysis settings
     *
     * @param programmingExercise exercise to validate
     */
    private void validateStaticCodeAnalysisSettings(ProgrammingExercise programmingExercise) {
        ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.orElseThrow()
                .getProgrammingLanguageFeatures(programmingExercise.getProgrammingLanguage());
        programmingExercise.validateStaticCodeAnalysisSettings(programmingLanguageFeature);
    }

    /**
     * POST /programming-exercises/import: Imports an existing programming exercise into an existing course
     * <p>
     * This will import the whole exercise, including all base build plans (template, solution) and repositories
     * (template, solution, test). Referenced entities, s.a. the test cases or the hints will get cloned and assigned
     * a new id. For a concrete list of what gets copied and what not have a look
     * at {@link ProgrammingExerciseImportService#importProgrammingExercise(ProgrammingExercise, ProgrammingExercise, boolean, boolean, boolean)}
     *
     * @param sourceExerciseId                    The ID of the original exercise which should get imported
     * @param newExercise                         The new exercise containing values that should get overwritten in the imported exercise, s.a. the title or difficulty
     * @param recreateBuildPlans                  Option determining whether the build plans should be copied or re-created from scratch
     * @param updateTemplate                      Option determining whether the template files should be updated with the most recent template version
     * @param setTestCaseVisibilityToAfterDueDate Option determining whether the test case visibility should be set to {@link Visibility#AFTER_DUE_DATE}
     * @return The imported exercise (200), a not found error (404) if the template does not exist, or a forbidden error
     *         (403) if the user is not at least an instructor in the target course.
     * @see ProgrammingExerciseImportService#importProgrammingExercise(ProgrammingExercise, ProgrammingExercise, boolean, boolean, boolean)
     */
    @PostMapping("programming-exercises/import/{sourceExerciseId}")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExercise> importProgrammingExercise(@PathVariable long sourceExerciseId, @RequestBody ProgrammingExercise newExercise,
            @RequestParam(defaultValue = "false") boolean recreateBuildPlans, @RequestParam(defaultValue = "false") boolean updateTemplate,
            @RequestParam(defaultValue = "false") boolean setTestCaseVisibilityToAfterDueDate) throws JsonProcessingException {
        if (sourceExerciseId < 0) {
            throw new BadRequestAlertException("Invalid source id when importing programming exercises", ENTITY_NAME, "invalidSourceExerciseId");
        }

        // Valid exercises have set either a course or an exerciseGroup
        newExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        log.debug("REST request to import programming exercise {} into course {}", sourceExerciseId, newExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        newExercise.validateGeneralSettings();
        newExercise.validateProgrammingSettings();
        newExercise.validateSettingsForFeedbackRequest();
        validateStaticCodeAnalysisSettings(newExercise);

        final User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(newExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);

        // Validate course settings
        programmingExerciseRepository.validateCourseSettings(newExercise, course);

        final var originalProgrammingExercise = programmingExerciseRepository
                .findByIdWithEagerBuildConfigTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxRepos(sourceExerciseId)
                .orElseThrow(() -> new EntityNotFoundException("ProgrammingExercise", sourceExerciseId));

        var consistencyErrors = consistencyCheckService.checkConsistencyOfProgrammingExercise(originalProgrammingExercise);
        if (!consistencyErrors.isEmpty()) {
            throw new ConflictException("The source exercise is inconsistent", ENTITY_NAME, "sourceExerciseInconsistent");
        }

        // Fetching the tasks separately, as putting it in the query above leads to Hibernate duplicating the tasks.
        var templateTasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(originalProgrammingExercise.getId());
        originalProgrammingExercise.setTasks(new ArrayList<>(templateTasks));

        // The static code analysis flag can only change, if the build plans are recreated and the template is upgraded
        if (newExercise.isStaticCodeAnalysisEnabled() != originalProgrammingExercise.isStaticCodeAnalysisEnabled() && !(recreateBuildPlans && updateTemplate)) {
            throw new BadRequestAlertException("Static code analysis can only change, if the recreation of build plans and update of template files is activated", ENTITY_NAME,
                    "staticCodeAnalysisCannotChange");
        }

        // If the new exercise has a submission policy, it must be validated.
        if (newExercise.getSubmissionPolicy() != null) {
            newExercise.getSubmissionPolicy().setActive(true);
            submissionPolicyService.validateSubmissionPolicy(newExercise.getSubmissionPolicy());
        }

        // Check if the user has the rights to access the original programming exercise
        Course originalCourse = courseService.retrieveCourseOverExerciseGroupOrCourseId(originalProgrammingExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, originalCourse, user);

        // Athena: Check that only allowed athena modules are used, if not we catch the exception and disable feedback suggestions for the imported exercise
        // If Athena is disabled and the service is not present, we also disable feedback suggestions
        try {
            athenaModuleService.ifPresentOrElse(ams -> ams.checkHasAccessToAthenaModule(newExercise, course, ENTITY_NAME), () -> newExercise.setFeedbackSuggestionModule(null));
        }
        catch (BadRequestAlertException e) {
            newExercise.setFeedbackSuggestionModule(null);
        }

        try {
            ProgrammingExercise importedProgrammingExercise = programmingExerciseImportService.importProgrammingExercise(originalProgrammingExercise, newExercise, updateTemplate,
                    recreateBuildPlans, setTestCaseVisibilityToAfterDueDate);

            // remove certain properties which are not relevant for the client to keep the response small
            importedProgrammingExercise.setTestCases(null);
            importedProgrammingExercise.setStaticCodeAnalysisCategories(null);
            importedProgrammingExercise.setTemplateParticipation(null);
            importedProgrammingExercise.setSolutionParticipation(null);
            importedProgrammingExercise.setExerciseHints(null);
            importedProgrammingExercise.setTasks(null);

            competencyProgressService.updateProgressByLearningObjectAsync(importedProgrammingExercise);

            return ResponseEntity.ok().headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, importedProgrammingExercise.getTitle()))
                    .body(importedProgrammingExercise);

        }
        catch (Exception exception) {
            log.error(exception.getMessage(), exception);

            boolean isExceptionWithTranslationKeys = exception instanceof HttpStatusException;
            if (isExceptionWithTranslationKeys) {
                throw exception;
            }

            throw new InternalServerErrorAlertException("Unable to import programming exercise: " + exception.getMessage(), ENTITY_NAME, "unableToImportProgrammingExercise");
        }
    }

    /**
     * POST /programming-exercises/import-from-file: Imports an existing programming exercise from an uploaded zip file into an existing course
     * <p>
     * This will create the whole exercise, including all base build plans (template, solution) and repositories (template, solution, test) and copy
     * the content from the repositories of the zip file into the newly created repositories.
     *
     * @param programmingExercise The exercise that should be imported
     * @param zipFile             The zip file containing the template, solution and test repositories plus a json file with the exercise configuration
     * @param courseId            The id of the course the exercise should be imported into
     * @return The imported exercise (200)
     *         (403) if the user is not at least an editor in the target course.
     */
    @PostMapping("courses/{courseId}/programming-exercises/import-from-file")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExercise> importProgrammingExerciseFromFile(@PathVariable long courseId,
            @RequestPart("programmingExercise") ProgrammingExercise programmingExercise, @RequestPart("file") MultipartFile zipFile) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        // Valid exercises have set either a course or an exerciseGroup
        programmingExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        final var course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, user);
        try {
            return ResponseEntity.ok(programmingExerciseImportFromFileService.importProgrammingExerciseFromFile(programmingExercise, zipFile, course, user));
        }
        catch (IOException | URISyntaxException | GitAPIException e) {
            log.error(e.getMessage(), e);
            throw new InternalServerErrorException("Error while importing programming exercise from file: " + e.getMessage());
        }

    }

    /**
     * GET /programming-exercises/:exerciseId/export-instructor-exercise
     *
     * @param exerciseId The id of the programming exercise
     * @return ResponseEntity with status
     * @throws IOException if something during the zip process went wrong
     */
    @GetMapping("programming-exercises/{exerciseId}/export-instructor-exercise")
    @EnforceAtLeastInstructor
    @FeatureToggle({ Feature.ProgrammingExercises, Feature.Exports })
    public ResponseEntity<Resource> exportInstructorExercise(@PathVariable long exerciseId) throws IOException {
        var programmingExercise = programmingExerciseRepository.findByIdWithPlagiarismDetectionConfigTeamConfigAndBuildConfigElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, programmingExercise, null);

        long start = System.nanoTime();
        Path path;
        try {
            path = programmingExerciseExportService.exportProgrammingExerciseForDownload(programmingExercise, Collections.synchronizedList(new ArrayList<>()));
        }
        catch (Exception e) {
            log.error("Error while exporting programming exercise with id {} for instructor", exerciseId, e);
            throw new InternalServerErrorException("Error while exporting programming exercise with id " + exerciseId + " for instructor");
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
    @GetMapping("programming-exercises/{exerciseId}/export-instructor-repository/{repositoryType}")
    @EnforceAtLeastTutor
    @FeatureToggle({ Feature.ProgrammingExercises, Feature.Exports })
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
    @GetMapping("programming-exercises/{exerciseId}/export-instructor-auxiliary-repository/{repositoryId}")
    @EnforceAtLeastTutor
    @FeatureToggle({ Feature.ProgrammingExercises, Feature.Exports })
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
    @PostMapping("programming-exercises/{exerciseId}/export-repos-by-participant-identifiers/{participantIdentifiers}")
    @EnforceAtLeastTutor
    @FeatureToggle({ Feature.ProgrammingExercises, Feature.Exports })
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

        Set<String> participantIdentifierList = new HashSet<>();
        if (!repositoryExportOptions.isExportAllParticipants()) {
            participantIdentifiers = participantIdentifiers.replaceAll("\\s+", "");
            participantIdentifierList.addAll(List.of(participantIdentifiers.split(",")));
        }

        // Select the participations that should be exported
        List<ProgrammingExerciseStudentParticipation> exportedStudentParticipations = new ArrayList<>();
        for (StudentParticipation studentParticipation : programmingExercise.getStudentParticipations()) {
            ProgrammingExerciseStudentParticipation programmingStudentParticipation = (ProgrammingExerciseStudentParticipation) studentParticipation;
            if (repositoryExportOptions.isExportAllParticipants() || (programmingStudentParticipation.getRepositoryUri() != null && studentParticipation.getParticipant() != null
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
    @PostMapping("programming-exercises/{exerciseId}/export-repos-by-participation-ids/{participationIds}")
    @EnforceAtLeastTutor
    @FeatureToggle({ Feature.ProgrammingExercises, Feature.Exports })
    public ResponseEntity<Resource> exportSubmissionsByParticipationIds(@PathVariable long exerciseId, @PathVariable String participationIds,
            @RequestBody RepositoryExportOptionsDTO repositoryExportOptions) throws IOException {
        var programmingExercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);

        // Only instructors or higher may override the anonymization setting
        if (!authCheckService.isAtLeastInstructorForExercise(programmingExercise, null)) {
            repositoryExportOptions.setAnonymizeRepository(true);
        }

        if (repositoryExportOptions.getFilterLateSubmissionsDate() == null) {
            repositoryExportOptions.setFilterLateSubmissionsIndividualDueDate(true);
            repositoryExportOptions.setFilterLateSubmissionsDate(programmingExercise.getDueDate());
        }

        var participationIdSet = new ArrayList<>(Arrays.asList(participationIds.split(","))).stream().map(String::trim).map(Long::parseLong).collect(Collectors.toSet());

        // Select the participations that should be exported
        List<ProgrammingExerciseStudentParticipation> exportedStudentParticipations = programmingExercise.getStudentParticipations().stream()
                .filter(participation -> participationIdSet.contains(participation.getId())).map(participation -> (ProgrammingExerciseStudentParticipation) participation).toList();
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
     * GET /programming-exercises/:exerciseId/export-student-requested-repository : sends a solution repository as a zip file without .git directory.
     *
     * @param exerciseId   The id of the programming exercise
     * @param includeTests flag that indicates whether the tests should also be exported
     * @return ResponseEntity with status
     * @throws IOException if something during the zip process went wrong
     */
    @GetMapping("programming-exercises/{exerciseId}/export-student-requested-repository")
    @EnforceAtLeastStudent
    @FeatureToggle({ Feature.ProgrammingExercises, Feature.Exports })
    public ResponseEntity<Resource> exportStudentRequestedRepository(@PathVariable long exerciseId, @RequestParam() boolean includeTests) throws IOException {
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        if (programmingExercise.isExamExercise()) {
            examAccessService.checkExamExerciseForExampleSolutionAccessElseThrow(programmingExercise);
        }
        Role atLeastRole = programmingExercise.isExampleSolutionPublished() ? Role.STUDENT : Role.TEACHING_ASSISTANT;
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(atLeastRole, programmingExercise, null);
        if (includeTests && !programmingExercise.isReleaseTestsWithExampleSolution()) {
            throw new AccessForbiddenException(RepositoryType.SOLUTION.getName(), programmingExercise.getId());
        }
        long start = System.nanoTime();
        Optional<File> zipFile = programmingExerciseExportService.exportStudentRequestedRepository(programmingExercise.getId(), includeTests, new ArrayList<>());

        return returnZipFileForRepositoryExport(zipFile, RepositoryType.SOLUTION.getName(), programmingExercise, start);
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
    @EnforceAtLeastStudent
    @FeatureToggle({ Feature.ProgrammingExercises, Feature.Exports })
    public ResponseEntity<Resource> exportStudentRepository(@PathVariable long exerciseId, @PathVariable long participationId) throws IOException {
        var programmingExercise = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(exerciseId);
        var studentParticipation = programmingExercise.getStudentParticipations().stream().filter(p -> p.getId().equals(participationId))
                .map(p -> (ProgrammingExerciseStudentParticipation) p).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("No student participation with id " + participationId + " was found for programming exercise " + exerciseId));
        if (!authCheckService.isOwnerOfParticipation(studentParticipation)) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, programmingExercise, null);
        }
        List<String> exportErrors = new ArrayList<>();
        long start = System.nanoTime();
        Optional<File> zipFile = programmingExerciseExportService.exportStudentRepository(exerciseId, studentParticipation, exportErrors);
        if (zipFile.isEmpty()) {
            throw new InternalServerErrorException("Could not export the student repository of participation " + participationId + ". Logged errors: " + exportErrors);
        }
        return returnZipFileForRepositoryExport(zipFile, RepositoryType.USER.getName(), programmingExercise, start);
    }
}
