package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.SHORT_NAME_PATTERN;
import static de.tum.in.www1.artemis.config.Constants.TITLE_NAME_PATTERN;
import static de.tum.in.www1.artemis.service.util.TimeLogUtil.formatDurationFrom;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import jplag.ExitException;

import org.apache.http.HttpException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.programming.*;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.RepositoryExportOptionsDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.artemis.web.websocket.dto.ProgrammingExerciseTestCaseStateDTO;

/**
 * REST controller for managing ProgrammingExercise.
 */
@RestController
@RequestMapping(ProgrammingExerciseResource.Endpoints.ROOT)
public class ProgrammingExerciseResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseResource.class);

    private static final String ENTITY_NAME = "programmingExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final UserRepository userRepository;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<VersionControlService> versionControlService;

    private final ExerciseService exerciseService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingExerciseRetrievalService programmingExerciseRetrievalService;

    private final ProgrammingExerciseImportService programmingExerciseImportService;

    private final ProgrammingExerciseExportService programmingExerciseExportService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseGroupService exerciseGroupService;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final GradingCriterionService gradingCriterionService;

    private final ProgrammingLanguageFeatureService programmingLanguageFeatureService;

    private final TemplateUpgradePolicy templateUpgradePolicy;

    /**
     * Java package name Regex according to Java 14 JLS (https://docs.oracle.com/javase/specs/jls/se14/html/jls-7.html#jls-7.4.1),
     * with the restriction to a-z,A-Z,_ as "Java letter" and 0-9 as digits due to JavaScript/Browser Unicode character class limitations
     */
    private static final String packageNameRegex = "^(?!.*(?:\\.|^)(?:abstract|continue|for|new|switch|assert|default|if|package|synchronized|boolean|do|goto|private|this|break|double|implements|protected|throw|byte|else|import|public|throws|case|enum|instanceof|return|transient|catch|extends|int|short|try|char|final|interface|static|void|class|finally|long|strictfp|volatile|const|float|native|super|while|_|true|false|null)(?:\\.|$))[A-Z_a-z][0-9A-Z_a-z]*(?:\\.[A-Z_a-z][0-9A-Z_a-z]*)*$";

    /**
     * Swift package name Regex derived from (https://docs.swift.org/swift-book/ReferenceManual/LexicalStructure.html#ID412),
     * with the restriction to a-z,A-Z as "Swift letter" and 0-9 as digits where no separators are allowed
     */
    private static final String packageNameRegexForSwift = "^(?!(?:associatedtype|class|deinit|enum|extension|fileprivate|func|import|init|inout|internal|let|open|operator|private|protocol|public|rethrows|static|struct|subscript|typealias|var|break|case|continue|default|defer|do|else|fallthrough|for|guard|if|in|repeat|return|switch|where|while|as|Any|catch|false|is|nil|super|self|Self|throw|throws|true|try|_)$)[A-Za-z][0-9A-Za-z]*$";

    private final Pattern packageNamePattern = Pattern.compile(packageNameRegex);

    private final Pattern packageNamePatternForSwift = Pattern.compile(packageNameRegexForSwift);

    public ProgrammingExerciseResource(ProgrammingExerciseRepository programmingExerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            CourseService courseService, Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService,
            ExerciseService exerciseService, ProgrammingExerciseService programmingExerciseService, StudentParticipationRepository studentParticipationRepository,
            ProgrammingExerciseImportService programmingExerciseImportService, ProgrammingExerciseExportService programmingExerciseExportService,
            ExerciseGroupService exerciseGroupService, StaticCodeAnalysisService staticCodeAnalysisService, GradingCriterionService gradingCriterionService,
            ProgrammingLanguageFeatureService programmingLanguageFeatureService, TemplateUpgradePolicy templateUpgradePolicy,
            ProgrammingExerciseRetrievalService programmingExerciseRetrievalService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.userRepository = userRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.exerciseService = exerciseService;
        this.programmingExerciseService = programmingExerciseService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseImportService = programmingExerciseImportService;
        this.programmingExerciseExportService = programmingExerciseExportService;
        this.exerciseGroupService = exerciseGroupService;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.gradingCriterionService = gradingCriterionService;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.templateUpgradePolicy = templateUpgradePolicy;
        this.programmingExerciseRetrievalService = programmingExerciseRetrievalService;
    }

    /**
     * @param exercise the exercise object we want to check for errors
     * @return the error message as response or null if everything is fine
     */
    private ResponseEntity<ProgrammingExercise> checkProgrammingExerciseForError(ProgrammingExercise exercise) {
        if (!continuousIntegrationService.get().checkIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId())) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, true, "exercise", ErrorKeys.INVALID_TEMPLATE_BUILD_PLAN_ID, "The Template Build Plan ID seems to be invalid."))
                    .body(null);
        }
        if (exercise.getVcsTemplateRepositoryUrl() == null || !versionControlService.get().repositoryUrlIsValid(exercise.getVcsTemplateRepositoryUrl())) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, true, "exercise", ErrorKeys.INVALID_TEMPLATE_REPOSITORY_URL, "The Template Repository URL seems to be invalid."))
                    .body(null);
        }
        if (exercise.getSolutionBuildPlanId() != null && !continuousIntegrationService.get().checkIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId())) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, true, "exercise", ErrorKeys.INVALID_SOLUTION_BUILD_PLAN_ID, "The Solution Build Plan ID seems to be invalid."))
                    .body(null);
        }
        var solutionRepositoryUrl = exercise.getVcsSolutionRepositoryUrl();
        if (solutionRepositoryUrl != null && !versionControlService.get().repositoryUrlIsValid(solutionRepositoryUrl)) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, true, "exercise", ErrorKeys.INVALID_SOLUTION_REPOSITORY_URL, "The Solution Repository URL seems to be invalid."))
                    .body(null);
        }
        return null;
    }

    /**
     * Validates the course and programming exercise short name.
     * 1. Check presence and length of exercise short name
     * 2. Check presence and length of course short name
     * 3. Find forbidden patterns in exercise short name
     * 4. Check that the short name doesn't already exist withing course or exam exercises
     *
     * @param programmingExercise Programming exercise to be validated
     * @param course              Course of the programming exercise
     * @return Optional validation error response
     */
    private Optional<ResponseEntity<ProgrammingExercise>> validateCourseAndExerciseShortName(ProgrammingExercise programmingExercise, Course course) {
        // Check if exercise shortname is set
        if (programmingExercise.getShortName() == null || programmingExercise.getShortName().length() < 3) {
            return Optional.of(ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "The shortname of the programming exercise is not set or too short", "programmingExerciseShortnameInvalid"))
                    .body(null));
        }

        // Check if course shortname is set
        if (course.getShortName() == null || course.getShortName().length() < 3) {
            return Optional.of(ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "The shortname of the course is not set or too short", "courseShortnameInvalid")).body(null));
        }

        // Check if exercise shortname matches regex
        Matcher shortNameMatcher = SHORT_NAME_PATTERN.matcher(programmingExercise.getShortName());
        if (!shortNameMatcher.matches()) {
            return Optional.of(ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The shortname is invalid", "shortnameInvalid")).body(null));
        }

        // NOTE: we have to cover two cases here: exercises directly stored in the course and exercises indirectly stored in the course (exercise -> exerciseGroup -> exam ->
        // course)
        long numberOfProgrammingExercisesWithSameShortName = programmingExerciseRepository.countByShortNameAndCourse(programmingExercise.getShortName(), course)
                + programmingExerciseRepository.countByShortNameAndExerciseGroupExamCourse(programmingExercise.getShortName(), course);
        if (numberOfProgrammingExercisesWithSameShortName > 0) {
            return Optional.of(ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName,
                    "A programming exercise with the same short name already exists. Please choose a different short name.", "shortnameAlreadyExists")).body(null));
        }
        return Optional.empty();
    }

    /**
     * Validate the general course settings.
     * 1. Validate the title
     * 2. Validate the course and programming exercise short name.
     *
     * @param programmingExercise Programming exercise to be validated
     * @param course              Course of the programming exercise
     * @return Optional validation error response
     */
    private Optional<ResponseEntity<ProgrammingExercise>> validateCourseSettings(ProgrammingExercise programmingExercise, Course course) {
        // Validate exercise title
        Optional<ResponseEntity<ProgrammingExercise>> optionalTitleValidationError = validateTitle(programmingExercise, course);
        if (optionalTitleValidationError.isPresent()) {
            return optionalTitleValidationError;
        }

        // Validate course and exercise short name
        Optional<ResponseEntity<ProgrammingExercise>> optionalShortNameValidationError = validateCourseAndExerciseShortName(programmingExercise, course);
        if (optionalShortNameValidationError.isPresent()) {
            return optionalShortNameValidationError;
        }
        return Optional.empty();
    }

    /**
     * Validate the programming exercise title.
     * 1. Check presence and length of exercise title
     * 2. Find forbidden patterns in exercise title
     *
     * @param programmingExercise Programming exercise to be validated
     * @param course              Course of the programming exercise
     * @return Optional validation error response
     */
    private Optional<ResponseEntity<ProgrammingExercise>> validateTitle(ProgrammingExercise programmingExercise, Course course) {
        // Check if exercise title is set
        if (programmingExercise.getTitle() == null || programmingExercise.getTitle().length() < 3) {
            return Optional.of(ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "The title of the programming exercise is too short", "programmingExerciseTitleInvalid")).body(null));
        }

        // Check if the exercise title matches regex
        Matcher titleMatcher = TITLE_NAME_PATTERN.matcher(programmingExercise.getTitle());
        if (!titleMatcher.matches()) {
            return Optional.of(ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The title is invalid", "titleInvalid")).body(null));
        }

        // Check that the exercise title is unique among all programming exercises in the course, otherwise the corresponding project in the VCS system cannot be generated
        long numberOfProgrammingExercisesWithSameTitle = programmingExerciseRepository.countByTitleAndCourse(programmingExercise.getTitle(), course)
                + programmingExerciseRepository.countByTitleAndExerciseGroupExamCourse(programmingExercise.getTitle(), course);
        if (numberOfProgrammingExercisesWithSameTitle > 0) {
            return Optional.of(ResponseEntity.badRequest().headers(
                    HeaderUtil.createAlert(applicationName, "A programming exercise with the same title already exists. Please choose a different title.", "titleAlreadyExists"))
                    .body(null));
        }

        return Optional.empty();
    }

    /**
     * Validates general programming exercise settings
     * 1. Validate score settings
     * 2. Validates the participation mode
     * 3. Validates the programming language
     *
     * @param programmingExercise exercise to validate
     * @return Optional validation error response
     */
    private Optional<ResponseEntity<ProgrammingExercise>> validateGeneralSettings(ProgrammingExercise programmingExercise) {
        // Validate score settings
        Optional<ResponseEntity<ProgrammingExercise>> optionalScoreSettingsError = exerciseService.validateScoreSettings(programmingExercise);
        if (optionalScoreSettingsError.isPresent()) {
            return optionalScoreSettingsError;
        }

        // Check if a participation mode was selected
        if (!Boolean.TRUE.equals(programmingExercise.isAllowOnlineEditor()) && !Boolean.TRUE.equals(programmingExercise.isAllowOfflineIde())) {
            return Optional.of(ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName,
                    "You need to allow at least one participation mode, the online editor or the offline IDE", "noParticipationModeAllowed")).body(null));
        }

        // Check if programming language is set
        if (programmingExercise.getProgrammingLanguage() == null) {
            return Optional.of(
                    ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "No programming language was specified", "programmingLanguageNotSet")).body(null));
        }
        return Optional.empty();
    }

    /**
     * Validates static code analysis settings
     * 1. The flag staticCodeAnalysisEnabled must not be null
     * 2. Static code analysis and sequential test runs can't be active at the same time
     * 3. Static code analysis can only be enabled for supported programming languages
     * 4. Static code analysis max penalty must only be set if static code analysis is enabled
     * 5. Static code analysis max penalty must be positive
     *
     * @param programmingExercise exercise to validate
     * @return Optional validation error response
     */
    private Optional<ResponseEntity<ProgrammingExercise>> validateStaticCodeAnalysisSettings(ProgrammingExercise programmingExercise) {
        ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.getProgrammingLanguageFeatures(programmingExercise.getProgrammingLanguage());

        // Check if the static code analysis flag was set
        if (programmingExercise.isStaticCodeAnalysisEnabled() == null) {
            return Optional.of(ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "The static code analysis flag must be set to true or false", "staticCodeAnalysisFlagNotSet")).body(null));
        }

        // Check that programming exercise doesn't have sequential test runs and static code analysis enabled
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && programmingExercise.hasSequentialTestRuns()) {
            return Optional.of(ResponseEntity.badRequest().headers(
                    HeaderUtil.createAlert(applicationName, "The static code analysis with sequential test runs is not supported at the moment", "staticCodeAnalysisAndSequential"))
                    .body(null));
        }

        // Check if the programming language supports static code analysis
        if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && !programmingLanguageFeature.isStaticCodeAnalysis()) {
            return Optional.of(ResponseEntity.badRequest().headers(
                    HeaderUtil.createAlert(applicationName, "The static code analysis is not supported for this programming language", "staticCodeAnalysisNotSupportedForLanguage"))
                    .body(null));
        }

        // Static code analysis max penalty must only be set if static code analysis is enabled
        if (Boolean.FALSE.equals(programmingExercise.isStaticCodeAnalysisEnabled()) && programmingExercise.getMaxStaticCodeAnalysisPenalty() != null) {
            return Optional.of(ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName,
                    "Max static code analysis penalty must only be set if static code analysis is enabled", "staticCodeAnalysisDisabledButPenaltySet")).body(null));
        }

        // Static code analysis max penalty must be positive
        if (programmingExercise.getMaxStaticCodeAnalysisPenalty() != null && programmingExercise.getMaxStaticCodeAnalysisPenalty() < 0) {
            return Optional.of(ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "Max static code analysis penalty must be positive", "staticCodeAnalysisMaxPenaltyNegative")).body(null));
        }
        return Optional.empty();
    }

    /**
     * POST /programming-exercises/setup : Setup a new programmingExercise (with all needed repositories etc.)
     *
     * @param programmingExercise the programmingExercise to setup
     * @return the ResponseEntity with status 201 (Created) and with body the new programmingExercise, or with status 400 (Bad Request) if the parameters are invalid
     */
    @PostMapping(Endpoints.SETUP)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<ProgrammingExercise> createProgrammingExercise(@RequestBody ProgrammingExercise programmingExercise) {
        log.debug("REST request to setup ProgrammingExercise : {}", programmingExercise);
        if (programmingExercise.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "A new programmingExercise cannot already have an ID", "idexists")).body(null);
        }

        // Valid exercises have set either a course or an exerciseGroup
        exerciseService.checkCourseAndExerciseGroupExclusivity(programmingExercise, ENTITY_NAME);

        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(programmingExercise);

        // Check authorization
        if (!authCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }

        // Validate general programming exercise settings
        Optional<ResponseEntity<ProgrammingExercise>> optionalGeneralError = validateGeneralSettings(programmingExercise);
        if (optionalGeneralError.isPresent()) {
            return optionalGeneralError.get();
        }

        ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.getProgrammingLanguageFeatures(programmingExercise.getProgrammingLanguage());

        // Check if package name is set
        if (programmingLanguageFeature.isPackageNameRequired()) {
            if (programmingExercise.getPackageName() == null) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The package name is invalid", "packagenameInvalid")).body(null);
            }

            // Check if package name matches regex
            Matcher packageNameMatcher;
            if (programmingExercise.getProgrammingLanguage() == ProgrammingLanguage.SWIFT) {
                packageNameMatcher = packageNamePatternForSwift.matcher(programmingExercise.getPackageName());
            }
            else {
                packageNameMatcher = packageNamePattern.matcher(programmingExercise.getPackageName());
            }
            if (!packageNameMatcher.matches()) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The package name is invalid", "packagenameInvalid")).body(null);
            }
        }

        // Check if project type is selected
        if (programmingLanguageFeature.getProjectTypes().size() > 0) {
            if (programmingExercise.getProjectType() == null) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The project type is not set", "projectTypeNotSet")).body(null);
            }
            if (!programmingLanguageFeature.getProjectTypes().contains(programmingExercise.getProjectType())) {
                return ResponseEntity.badRequest()
                        .headers(HeaderUtil.createAlert(applicationName, "The project type is not supported for this programming language", "projectTypeNotSupported")).body(null);
            }
        }
        else {
            if (programmingExercise.getProjectType() != null) {
                return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The project type is set but not supported", "projectTypeSet")).body(null);
            }
        }

        // Check if checkout solution repository is enabled
        if (programmingExercise.getCheckoutSolutionRepository() && !programmingLanguageFeature.isCheckoutSolutionRepositoryAllowed()) {
            return ResponseEntity.badRequest()
                    .headers(
                            HeaderUtil.createAlert(applicationName, "Checking out the solution repository is only supported for Haskell exercises", "checkoutSolutionNotSupported"))
                    .body(null);
        }

        // Validate course settings
        Optional<ResponseEntity<ProgrammingExercise>> optionalCourseError = validateCourseSettings(programmingExercise, course);
        if (optionalCourseError.isPresent()) {
            return optionalCourseError.get();
        }

        // Validate static code analysis settings
        Optional<ResponseEntity<ProgrammingExercise>> optionalStaticCodeAnalysisError = validateStaticCodeAnalysisSettings(programmingExercise);
        if (optionalStaticCodeAnalysisError.isPresent()) {
            return optionalStaticCodeAnalysisError.get();
        }

        programmingExercise.generateAndSetProjectKey();
        Optional<ResponseEntity<ProgrammingExercise>> projectExistsError = checkIfProjectExists(programmingExercise);
        if (projectExistsError.isPresent()) {
            return projectExistsError.get();
        }

        try {
            // Setup all repositories etc
            ProgrammingExercise newProgrammingExercise = programmingExerciseService.createProgrammingExercise(programmingExercise);
            // Create default static code analysis categories
            if (Boolean.TRUE.equals(programmingExercise.isStaticCodeAnalysisEnabled())) {
                staticCodeAnalysisService.createDefaultCategories(newProgrammingExercise);
            }
            return ResponseEntity.created(new URI("/api/programming-exercises" + newProgrammingExercise.getId()))
                    .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, newProgrammingExercise.getTitle())).body(newProgrammingExercise);
        }
        catch (IOException | URISyntaxException | InterruptedException | GitAPIException e) {
            log.error("Error while setting up programming exercise", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .headers(HeaderUtil.createAlert(applicationName, "An error occurred while setting up the exercise: " + e.getMessage(), "errorProgrammingExercise")).body(null);
        }
    }

    /**
     * Checks if the project for the given programming exercise already exists in the version control system (VCS) and in the continuous integration system (CIS).
     * The check is done based on the project key (course short name + exercise short name) and the project name (course short name + exercise title).
     * This prevents errors then the actual projects will be generated later on.
     * An error response is returned in case the project does already exist. This will then e.g. stop the generation (or import) of the programming exercise.
     *
     * @param programmingExercise a typically new programming exercise for which the corresponding VCS and CIS projects should not yet exist.
     * @return an error response in case the project already exists or an empty optional in case it does not exist yet (which means the setup can continue as usual)
     */
    public Optional<ResponseEntity<ProgrammingExercise>> checkIfProjectExists(ProgrammingExercise programmingExercise) {
        String projectKey = programmingExercise.getProjectKey();
        String projectName = programmingExercise.getProjectName();
        boolean projectExists = versionControlService.get().checkIfProjectExists(projectKey, projectName);
        if (projectExists) {
            return Optional.of(ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName,
                            "Project already exists on the Version Control Server: " + projectName + ". Please choose a different title and short name!", "vcsProjectExists"))
                    .body(null));
        }

        String errorMessageCI = continuousIntegrationService.get().checkIfProjectExists(projectKey, projectName);
        if (errorMessageCI != null) {
            return Optional.of(ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, errorMessageCI, "ciProjectExists")).body(null));
        }
        // means the project does not exist in version control server and does not exist in continuous integration server
        return Optional.empty();
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
    @PostMapping(Endpoints.IMPORT)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<ProgrammingExercise> importProgrammingExercise(@PathVariable long sourceExerciseId, @RequestBody ProgrammingExercise newExercise,
            @RequestParam(defaultValue = "false") boolean recreateBuildPlans, @RequestParam(defaultValue = "false") boolean updateTemplate) {
        if (sourceExerciseId < 0) {
            return badRequest();
        }

        // Valid exercises have set either a course or an exerciseGroup
        exerciseService.checkCourseAndExerciseGroupExclusivity(newExercise, ENTITY_NAME);

        log.debug("REST request to import programming exercise {} into course {}", sourceExerciseId, newExercise.getCourseViaExerciseGroupOrCourseMember().getId());

        // Validate general programming exercise settings
        Optional<ResponseEntity<ProgrammingExercise>> optionalGeneralError = validateGeneralSettings(newExercise);
        if (optionalGeneralError.isPresent()) {
            return optionalGeneralError.get();
        }

        // Validate static code analysis settings
        Optional<ResponseEntity<ProgrammingExercise>> optionalStaticCodeAnalysisError = validateStaticCodeAnalysisSettings(newExercise);
        if (optionalStaticCodeAnalysisError.isPresent()) {
            return optionalStaticCodeAnalysisError.get();
        }

        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(newExercise);
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            log.debug("User {} is not allowed to import exercises for course {}", user.getId(), course.getId());
            return forbidden();
        }

        // Validate course settings
        Optional<ResponseEntity<ProgrammingExercise>> optionalCourseError = validateCourseSettings(newExercise, course);
        if (optionalCourseError.isPresent()) {
            return optionalCourseError.get();
        }

        final var optionalOriginalProgrammingExercise = programmingExerciseRepository
                .findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipations(sourceExerciseId);
        if (optionalOriginalProgrammingExercise.isEmpty()) {
            return notFound();
        }
        final var originalProgrammingExercise = optionalOriginalProgrammingExercise.get();

        // The static code analysis flag can only change, if the build plans are recreated and the template is upgraded
        if (newExercise.isStaticCodeAnalysisEnabled() != originalProgrammingExercise.isStaticCodeAnalysisEnabled() && !(recreateBuildPlans && updateTemplate)) {
            throw new BadRequestAlertException("Static code analysis can only change, if the recreation of build plans and update of template files is activated", ENTITY_NAME,
                    "staticCodeAnalysisCannotChange");
        }

        // Check if the user has the rights to access the original programming exercise
        Course originalCourse = courseService.retrieveCourseOverExerciseGroupOrCourseId(originalProgrammingExercise);
        if (!authCheckService.isAtLeastInstructorInCourse(originalCourse, user)) {
            log.debug("User {} is not authorized to import the original exercise in course {}", user.getId(), originalCourse.getId());
            return forbidden();
        }

        newExercise.generateAndSetProjectKey();
        Optional<ResponseEntity<ProgrammingExercise>> projectExistsError = checkIfProjectExists(newExercise);
        if (projectExistsError.isPresent()) {
            return projectExistsError.get();
        }

        final var importedProgrammingExercise = programmingExerciseImportService.importProgrammingExerciseBasis(originalProgrammingExercise, newExercise);
        HttpHeaders responseHeaders;
        programmingExerciseImportService.importRepositories(originalProgrammingExercise, importedProgrammingExercise);

        // Update the template files
        if (updateTemplate) {
            TemplateUpgradeService upgradeService = templateUpgradePolicy.getUpgradeService(importedProgrammingExercise.getProgrammingLanguage());
            upgradeService.upgradeTemplate(importedProgrammingExercise);
        }

        // Copy or recreate the build plans
        try {
            if (recreateBuildPlans) {
                // Create completely new build plans for the exercise
                programmingExerciseService.setupBuildPlansForNewExercise(importedProgrammingExercise);
            }
            else {
                // We have removed the automatic build trigger from test to base for new programming exercises.
                // We also remove this build trigger in the case of an import as the source exercise might still have this trigger.
                // The importBuildPlans method includes this process
                programmingExerciseImportService.importBuildPlans(originalProgrammingExercise, importedProgrammingExercise);
            }
            responseHeaders = HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, importedProgrammingExercise.getTitle());
        }
        catch (HttpException e) {
            responseHeaders = HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "importExerciseTriggerPlanFail", "Unable to trigger imported build plans");
        }

        programmingExerciseService.scheduleOperations(importedProgrammingExercise.getId());

        // Remove unnecessary fields
        importedProgrammingExercise.setTestCases(null);
        importedProgrammingExercise.setStaticCodeAnalysisCategories(null);
        importedProgrammingExercise.setTemplateParticipation(null);
        importedProgrammingExercise.setSolutionParticipation(null);
        importedProgrammingExercise.setExerciseHints(null);

        return ResponseEntity.ok().headers(responseHeaders).body(importedProgrammingExercise);
    }

    /**
     * PUT /programming-exercises : Updates an existing updatedProgrammingExercise.
     *
     * @param updatedProgrammingExercise the programmingExercise that has been updated on the client
     * @param notificationText           to notify the student group about the update on the programming exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated ProgrammingExercise, or with status 400 (Bad Request) if the updated ProgrammingExercise
     * is not valid, or with status 500 (Internal Server Error) if the updated ProgrammingExercise couldn't be saved to the database
     */
    @PutMapping(Endpoints.PROGRAMMING_EXERCISES)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<ProgrammingExercise> updateProgrammingExercise(@RequestBody ProgrammingExercise updatedProgrammingExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update ProgrammingExercise : {}", updatedProgrammingExercise);
        if (updatedProgrammingExercise.getId() == null) {
            return badRequest();
        }

        // Valid exercises have set either a course or an exerciseGroup
        exerciseService.checkCourseAndExerciseGroupExclusivity(updatedProgrammingExercise, ENTITY_NAME);

        // Validate static code analysis settings
        Optional<ResponseEntity<ProgrammingExercise>> optionalStaticCodeAnalysisError = validateStaticCodeAnalysisSettings(updatedProgrammingExercise);
        if (optionalStaticCodeAnalysisError.isPresent()) {
            return optionalStaticCodeAnalysisError.get();
        }

        // fetch course from database to make sure client didn't change groups
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(updatedProgrammingExercise);

        // Check authorization
        if (!authCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }

        ResponseEntity<ProgrammingExercise> errorResponse = checkProgrammingExerciseForError(updatedProgrammingExercise);
        if (errorResponse != null) {
            return errorResponse;
        }

        var existingProgrammingExercise = programmingExerciseRepository.findById(updatedProgrammingExercise.getId());
        if (existingProgrammingExercise.isEmpty()) {
            throw new EntityNotFoundException("Cannot update a programming exercise that does not exist in the database");
        }
        if (!Objects.equals(existingProgrammingExercise.get().getShortName(), updatedProgrammingExercise.getShortName())) {
            throw new BadRequestAlertException("The programming exercise short name cannot be changed", ENTITY_NAME, "shortNameCannotChange");
        }
        if (existingProgrammingExercise.get().isStaticCodeAnalysisEnabled() != updatedProgrammingExercise.isStaticCodeAnalysisEnabled()) {
            throw new BadRequestAlertException("Static code analysis enabled flag must not be changed", ENTITY_NAME, "staticCodeAnalysisCannotChange");
        }
        if (!Boolean.TRUE.equals(updatedProgrammingExercise.isAllowOnlineEditor()) && !Boolean.TRUE.equals(updatedProgrammingExercise.isAllowOfflineIde())) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName,
                    "You need to allow at least one participation mode, the online editor or the offline IDE", "noParticipationModeAllowed")).body(null);
        }

        if (updatedProgrammingExercise.getBonusPoints() == null) {
            // make sure the default value is set properly
            updatedProgrammingExercise.setBonusPoints(0.0);
        }

        // TODO: if isAllowOfflineIde changes, we might want to change access for all existing student participations
        // false --> true: add access for students to all existing student participations
        // true --> false: remove access for students from all existing student participations

        // Forbid conversion between normal course exercise and exam exercise
        exerciseService.checkForConversionBetweenExamAndCourseExercise(updatedProgrammingExercise, existingProgrammingExercise.get(), ENTITY_NAME);

        // Only save after checking for errors
        ProgrammingExercise savedProgrammingExercise = programmingExerciseService.updateProgrammingExercise(updatedProgrammingExercise, notificationText);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedProgrammingExercise.getTitle()))
                .body(savedProgrammingExercise);
    }

    /**
     * PUT /programming-exercises/timeline : Updates the timeline attributes of a given exercise
     * @param updatedProgrammingExercise containing the changes that have to be saved
     * @param notificationText an optional text to notify the student group about the update on the programming exercise
     * @return the ResponseEntity with status 200 (OK) with the updated ProgrammingExercise, or with status 403 (Forbidden)
     * if the user is not allowed to update the exercise or with 404 (Not Found) if the updated ProgrammingExercise couldn't be found in the database
     */
    @PutMapping(Endpoints.TIMELINE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<ProgrammingExercise> updateProgrammingExerciseTimeline(@RequestBody ProgrammingExercise updatedProgrammingExercise,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update the timeline of ProgrammingExercise : {}", updatedProgrammingExercise);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = updatedProgrammingExercise.getCourseViaExerciseGroupOrCourseMember();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }

        updatedProgrammingExercise = programmingExerciseService.updateTimeline(updatedProgrammingExercise, notificationText);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedProgrammingExercise.getTitle()))
                .body(updatedProgrammingExercise);

    }

    /**
     * PATCH /programming-exercises-problem: Updates the problem statement of the exercise.
     *
     * @param exerciseId              The ID of the exercise for which to change the problem statement
     * @param updatedProblemStatement The new problemStatement
     * @param notificationText        to notify the student group about the updated problemStatement on the programming exercise
     * @return the ResponseEntity with status 200 (OK) and with body the updated problemStatement, with status 404 if the programmingExercise could not be found, or with 403 if the user does not have permissions to access the programming exercise.
     */
    @PatchMapping(value = Endpoints.PROBLEM)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExercise> updateProblemStatement(@PathVariable long exerciseId, @RequestBody String updatedProblemStatement,
            @RequestParam(value = "notificationText", required = false) String notificationText) {
        log.debug("REST request to update ProgrammingExercise with new problem statement: {}", updatedProblemStatement);
        ProgrammingExercise updatedProgrammingExercise;
        try {
            updatedProgrammingExercise = programmingExerciseService.updateProblemStatement(exerciseId, updatedProblemStatement, notificationText);
        }
        catch (IllegalAccessException ex) {
            return forbidden();
        }
        catch (EntityNotFoundException ex) {
            return notFound();
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updatedProgrammingExercise.getTitle()))
                .body(updatedProgrammingExercise);
    }

    /**
     * GET /courses/:courseId/programming-exercises : get all the programming exercises.
     *
     * @param courseId of the course for which the exercise should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExercises in body
     */
    @GetMapping(Endpoints.GET_FOR_COURSE)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ProgrammingExercise>> getProgrammingExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseService.findOne(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return forbidden();
        }
        List<ProgrammingExercise> exercises = programmingExerciseRepository.findByCourseIdWithLatestResultForTemplateSolutionParticipations(courseId);
        for (ProgrammingExercise exercise : exercises) {
            // not required in the returned json body
            exercise.setStudentParticipations(null);
            exercise.setCourse(null);
        }
        return ResponseEntity.ok().body(exercises);
    }

    /**
     * GET /programming-exercises/:exerciseId : get the "exerciseId" programmingExercise.
     *
     * @param exerciseId the id of the programmingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the programmingExercise, or with status 404 (Not Found)
     */
    @GetMapping(Endpoints.PROGRAMMING_EXERCISE)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExercise> getProgrammingExercise(@PathVariable long exerciseId) {
        // TODO: Split this route in two: One for normal and one for exam exercises
        log.debug("REST request to get ProgrammingExercise : {}", exerciseId);
        Optional<ProgrammingExercise> optionalProgrammingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exerciseId);

        if (optionalProgrammingExercise.isEmpty()) {
            return notFound();
        }
        ProgrammingExercise programmingExercise = optionalProgrammingExercise.get();

        // Fetch grading criterion into exercise of participation
        List<GradingCriterion> gradingCriteria = gradingCriterionService.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId());
        programmingExercise.setGradingCriteria(gradingCriteria);

        // If the exercise belongs to an exam, only instructors and admins are allowed to access it, otherwise also TA have access
        if (programmingExercise.isExamExercise()) {
            // Get the course over the exercise group
            ExerciseGroup exerciseGroup = exerciseGroupService.findOneWithExam(programmingExercise.getExerciseGroup().getId());
            Course course = exerciseGroup.getExam().getCourse();

            if (!authCheckService.isAtLeastInstructorInCourse(course, null)) {
                return forbidden();
            }
            // Set the exerciseGroup, exam and course so that the client can work with those ids
            programmingExercise.setExerciseGroup(exerciseGroup);
        }
        else if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise)) {
            return forbidden();
        }
        return ResponseEntity.ok().body(programmingExercise);
    }

    /**
     * GET /programming-exercises/:exerciseId/with-participations/ : get the "exerciseId" programmingExercise.
     *
     * @param exerciseId the id of the programmingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the programmingExercise, or with status 404 (Not Found)
     */
    @GetMapping(Endpoints.PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExercise> getProgrammingExerciseWithSetupParticipations(@PathVariable long exerciseId) {
        log.debug("REST request to get ProgrammingExercise : {}", exerciseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Optional<ProgrammingExercise> programmingExerciseOpt = programmingExerciseRepository.findWithTemplateAndSolutionParticipationLatestResultById(exerciseId);
        if (programmingExerciseOpt.isPresent()) {
            ProgrammingExercise programmingExercise = programmingExerciseOpt.get();
            Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
            if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
                return forbidden();
            }
            Optional<StudentParticipation> assignmentParticipation = studentParticipationRepository.findByExerciseIdAndStudentIdWithLatestResult(programmingExercise.getId(),
                    user.getId());
            Set<StudentParticipation> participations = new HashSet<>();
            assignmentParticipation.ifPresent(participations::add);
            programmingExercise.setStudentParticipations(participations);
            return ResponseEntity.ok(programmingExercise);
        }
        else {
            return notFound();
        }
    }

    /**
     * GET /programming-exercises/:exerciseId/with-template-and-solution-participation
     *
     * @param exerciseId the id of the programmingExercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and the programming exercise with template and solution participation, or with status 404 (Not Found)
     */
    @GetMapping(Endpoints.PROGRAMMING_EXERCISE_WITH_TEMPLATE_AND_SOLUTION_PARTICIPATION)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExercise> getProgrammingExerciseWithTemplateAndSolutionParticipation(@PathVariable long exerciseId) {
        log.debug("REST request to get programming exercise with template and solution participation : {}", exerciseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Optional<ProgrammingExercise> programmingExerciseOpt = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exerciseId);
        if (programmingExerciseOpt.isPresent()) {
            ProgrammingExercise programmingExercise = programmingExerciseOpt.get();
            Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
            if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
                return forbidden();
            }
            return ResponseEntity.ok(programmingExercise);
        }
        else {
            return notFound();
        }
    }

    /**
     * DELETE /programming-exercises/:id : delete the "id" programmingExercise.
     *
     * @param exerciseId                   the id of the programmingExercise to delete
     * @param deleteStudentReposBuildPlans boolean which states whether the corresponding build plan should be deleted as well
     * @param deleteBaseReposBuildPlans    the ResponseEntity with status 200 (OK)
     * @return the ResponseEntity with status 200 (OK) when programming exercise has been successfully deleted or with status 404 (Not Found)
     */
    @DeleteMapping(Endpoints.PROGRAMMING_EXERCISE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> deleteProgrammingExercise(@PathVariable long exerciseId, @RequestParam(defaultValue = "false") boolean deleteStudentReposBuildPlans,
            @RequestParam(defaultValue = "false") boolean deleteBaseReposBuildPlans) {
        log.info("REST request to delete ProgrammingExercise : {}", exerciseId);
        Optional<ProgrammingExercise> optionalProgrammingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exerciseId);
        if (optionalProgrammingExercise.isEmpty()) {
            return notFound();
        }
        ProgrammingExercise programmingExercise = optionalProgrammingExercise.get();

        // If the exercise belongs to an exam, the course must be retrieved over the exerciseGroup
        Course course;
        if (programmingExercise.isExamExercise()) {
            course = exerciseGroupService.retrieveCourseOverExerciseGroup(programmingExercise.getExerciseGroup().getId());
        }
        else {
            course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            return forbidden();
        }
        exerciseService.logDeletion(programmingExercise, course, user);
        exerciseService.delete(exerciseId, deleteStudentReposBuildPlans, deleteBaseReposBuildPlans);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, programmingExercise.getTitle())).build();
    }

    /**
     * Combine all commits into one in the template repository of a given exercise.
     *
     * @param exerciseId of the exercise
     * @return the ResponseEntity with status
     * 200 (OK) if combine has been successfully executed
     * 403 (Forbidden) if the user is not admin and course instructor or
     * 500 (Internal Server Error)
     */
    @PutMapping(value = Endpoints.COMBINE_COMMITS, produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> combineTemplateRepositoryCommits(@PathVariable long exerciseId) {
        log.debug("REST request to combine the commits of the template repository of ProgrammingExercise with id: {}", exerciseId);

        Optional<ProgrammingExercise> programmingExerciseOptional = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exerciseId);
        if (programmingExerciseOptional.isEmpty()) {
            return notFound();
        }
        ProgrammingExercise programmingExercise = programmingExerciseOptional.get();

        Course course = courseService.findOne(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin(user)) {
            return forbidden();
        }

        try {
            var exerciseRepoURL = programmingExercise.getVcsTemplateRepositoryUrl();
            programmingExerciseService.combineAllCommitsOfRepositoryIntoOne(exerciseRepoURL);
            return new ResponseEntity<>(HttpStatus.OK);
        }
        catch (IllegalStateException | InterruptedException | GitAPIException ex) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * POST /programming-exercises/:exerciseId/export-instructor-respository/:repositoryType : sends a test, solution or template repository as a zip file
     * @param exerciseId The id of the programming exercise
     * @param repositoryType The type of repository to zip and send
     * @return ResponseEntity with status
     * @throws IOException if something during the zip process went wrong
     */
    @GetMapping(Endpoints.EXPORT_INSTRUCTOR_REPOSITORY)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Resource> exportInstructorRepositoryForProgrammingExercise(@PathVariable long exerciseId, @PathVariable RepositoryType repositoryType)
            throws IOException {
        ProgrammingExercise programmingExercise = programmingExerciseRetrievalService.findOne(exerciseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise, user)) {
            return forbidden();
        }

        long start = System.nanoTime();
        File zipFile = programmingExerciseExportService.exportInstructorRepositoryForExercise(programmingExercise.getId(), repositoryType, new ArrayList<>());
        if (zipFile == null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError",
                    "There was an error on the server and the zip file could not be created.")).body(null);
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));

        log.info("Export of the repository of type {} programming exercise {} with title '{}' was successful in {}.", repositoryType.getName(), programmingExercise.getId(),
                programmingExercise.getTitle(), formatDurationFrom(start));

        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
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
    @PostMapping(Endpoints.EXPORT_SUBMISSIONS_BY_PARTICIPANTS)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Resource> exportSubmissionsByStudentLogins(@PathVariable long exerciseId, @PathVariable String participantIdentifiers,
            @RequestBody RepositoryExportOptionsDTO repositoryExportOptions) throws IOException {
        ProgrammingExercise programmingExercise = programmingExerciseRetrievalService.findByIdWithEagerStudentParticipationsAndSubmissions(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise, user)) {
            return forbidden();
        }

        if (repositoryExportOptions.isExportAllParticipants()) {
            // only instructors are allowed to download all repos
            if (!authCheckService.isAtLeastInstructorForExercise(programmingExercise, user)) {
                return forbidden();
            }
        }

        if (repositoryExportOptions.getFilterLateSubmissionsDate() == null) {
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
     * @param participationIds        the participationIds seperated via semicolon to get their submissions (used for double blind assessment)
     * @param repositoryExportOptions the options that should be used for the export. Export all students is not supported here!
     * @return ResponseEntity with status
     * @throws IOException if submissions can't be zippedRequestBody
     */
    @PostMapping(Endpoints.EXPORT_SUBMISSIONS_BY_PARTICIPATIONS)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Resource> exportSubmissionsByParticipationIds(@PathVariable long exerciseId, @PathVariable String participationIds,
            @RequestBody RepositoryExportOptionsDTO repositoryExportOptions) throws IOException {
        ProgrammingExercise programmingExercise = programmingExerciseRetrievalService.findByIdWithEagerStudentParticipationsAndSubmissions(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise)) {
            return forbidden();
        }

        if (repositoryExportOptions.getFilterLateSubmissionsDate() == null) {
            repositoryExportOptions.setFilterLateSubmissionsDate(programmingExercise.getDueDate());
        }

        Set<Long> participationIdSet = new ArrayList<>(Arrays.asList(participationIds.split(","))).stream().map(String::trim).map(Long::parseLong).collect(Collectors.toSet());

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
     * PUT /programming-exercises/{exerciseId}/generate-tests : Makes a call to StructureOracleGenerator to generate the structure oracle aka the test.json file
     *
     * @param exerciseId The ID of the programming exercise for which the structure oracle should get generated
     * @return The ResponseEntity with status 201 (Created) or with status 400 (Bad Request) if the parameters are invalid
     */
    @PutMapping(value = Endpoints.GENERATE_TESTS, produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<String> generateStructureOracleForExercise(@PathVariable long exerciseId) {
        log.debug("REST request to generate the structure oracle for ProgrammingExercise with id: {}", exerciseId);

        Optional<ProgrammingExercise> programmingExerciseOptional = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exerciseId);
        if (programmingExerciseOptional.isEmpty()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "programmingExerciseNotFound", "The programming exercise does not exist"))
                    .body(null);
        }

        ProgrammingExercise programmingExercise = programmingExerciseOptional.get();
        Course course = courseService.findOne(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isInstructorInCourse(course, user) && !authCheckService.isAdmin(user)) {
            return forbidden();
        }
        if (programmingExercise.getPackageName() == null || programmingExercise.getPackageName().length() < 3) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName,
                    "This is a linked exercise and generating the structure oracle for this exercise is not possible.", "couldNotGenerateStructureOracle")).body(null);
        }

        var solutionRepoURL = programmingExercise.getVcsSolutionRepositoryUrl();
        var exerciseRepoURL = programmingExercise.getVcsTemplateRepositoryUrl();
        var testRepoURL = programmingExercise.getVcsTestRepositoryUrl();

        try {
            String testsPath = Paths.get("test", programmingExercise.getPackageFolderName()).toString();
            // Atm we only have one folder that can have structural tests, but this could change.
            testsPath = programmingExercise.hasSequentialTestRuns() ? Paths.get("structural", testsPath).toString() : testsPath;
            boolean didGenerateOracle = programmingExerciseService.generateStructureOracleFile(solutionRepoURL, exerciseRepoURL, testRepoURL, testsPath, user);

            if (didGenerateOracle) {
                HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.setContentType(MediaType.TEXT_PLAIN);
                return new ResponseEntity<>("Successfully generated the structure oracle for the exercise " + programmingExercise.getProjectName(), responseHeaders, HttpStatus.OK);
            }
            else {
                return ResponseEntity.badRequest().headers(
                        HeaderUtil.createAlert(applicationName, "Did not update the oracle because there have not been any changes to it.", "didNotGenerateStructureOracle"))
                        .body(null);
            }
        }
        catch (Exception e) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName,
                            "An error occurred while generating the structure oracle for the exercise " + programmingExercise.getProjectName() + ": \n" + e.getMessage(),
                            "errorStructureOracleGeneration"))
                    .body(null);
        }
    }

    /**
     * GET /programming-exercises/:exerciseId/test-case-state : Returns a DTO that offers information on the test case state of the programming exercise.
     *
     * @param exerciseId the id of a ProgrammingExercise
     * @return the ResponseEntity with status 200 (OK) and ProgrammingExerciseTestCaseStateDTO. Returns 404 (notFound) if the exercise does not exist.
     */
    @GetMapping(Endpoints.TEST_CASE_STATE)
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingExerciseTestCaseStateDTO> hasAtLeastOneStudentResult(@PathVariable long exerciseId) {
        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(exerciseId);
        if (programmingExercise.isEmpty()) {
            return notFound();
        }
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise)) {
            return forbidden();
        }
        boolean hasAtLeastOneStudentResult = programmingExerciseService.hasAtLeastOneStudentResult(programmingExercise.get());
        boolean isReleased = programmingExercise.get().isReleased();
        ProgrammingExerciseTestCaseStateDTO testCaseDTO = new ProgrammingExerciseTestCaseStateDTO().released(isReleased).studentResult(hasAtLeastOneStudentResult)
                .testCasesChanged(programmingExercise.get().getTestCasesChanged())
                .buildAndTestStudentSubmissionsAfterDueDate(programmingExercise.get().getBuildAndTestStudentSubmissionsAfterDueDate());
        return ResponseEntity.ok(testCaseDTO);
    }

    /**
     * Search for all programming exercises by title and course title. The result is pageable since there might be hundreds
     * of exercises in the DB.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping(Endpoints.PROGRAMMING_EXERCISES)
    @PreAuthorize("hasAnyRole('INSTRUCTOR, ADMIN')")
    public ResponseEntity<SearchResultPageDTO<ProgrammingExercise>> getAllExercisesOnPage(PageableSearchDTO<String> search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(programmingExerciseService.getAllOnPageWithSize(search, user));
    }

    /**
     * GET /programming-exercises/{exerciseId}/plagiarism-check : Uses JPlag to check for plagiarism
     * and returns the generated output as zip file
     *
     * @param exerciseId          The ID of the programming exercise for which the plagiarism check should be
     *                            executed
     * @param similarityThreshold ignore comparisons whose similarity is below this threshold (%)
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @return The ResponseEntity with status 201 (Created) or with status 400 (Bad Request) if the
     * parameters are invalid
     * @throws ExitException is thrown if JPlag exits unexpectedly
     * @throws IOException   is thrown for file handling errors
     */
    @GetMapping(Endpoints.CHECK_PLAGIARISM)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<TextPlagiarismResult> checkPlagiarism(@PathVariable long exerciseId, @RequestParam float similarityThreshold, @RequestParam int minimumScore)
            throws ExitException, IOException {
        log.debug("REST request to check plagiarism for ProgrammingExercise with id: {}", exerciseId);
        long start = System.nanoTime();

        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findById(exerciseId);
        if (programmingExercise.isEmpty()) {
            return notFound();
        }
        if (!authCheckService.isAtLeastInstructorForExercise(programmingExercise.get())) {
            return forbidden();
        }

        var language = programmingExercise.get().getProgrammingLanguage();
        ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.getProgrammingLanguageFeatures(language);

        if (!programmingLanguageFeature.isPlagiarismCheckSupported()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "programmingLanguageNotSupported",
                    "Artemis does not support plagiarism checks for the programming language " + language)).body(null);
        }

        TextPlagiarismResult result = programmingExerciseExportService.checkPlagiarism(exerciseId, similarityThreshold, minimumScore);

        log.info("Check plagiarism of programming exercise {} with title '{}' was successful in {}.", programmingExercise.get().getId(), programmingExercise.get().getTitle(),
                formatDurationFrom(start));

        return ResponseEntity.ok(result);
    }

    /**
     * GET /programming-exercises/{exerciseId}/plagiarism-check : Uses JPlag to check for plagiarism
     * and returns the generated output as zip file
     *
     * @param exerciseId          The ID of the programming exercise for which the plagiarism check should be
     *                            executed
     * @param similarityThreshold ignore comparisons whose similarity is below this threshold (%)
     * @param minimumScore        consider only submissions whose score is greater or equal to this value
     * @return The ResponseEntity with status 201 (Created) or with status 400 (Bad Request) if the
     * parameters are invalid
     * @throws ExitException is thrown if JPlag exits unexpectedly
     * @throws IOException   is thrown for file handling errors
     */
    @GetMapping(value = Endpoints.CHECK_PLAGIARISM_JPLAG_REPORT, produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Resource> checkPlagiarismWithJPlagReport(@PathVariable long exerciseId, @RequestParam float similarityThreshold, @RequestParam int minimumScore)
            throws ExitException, IOException {
        log.debug("REST request to check plagiarism for ProgrammingExercise with id: {}", exerciseId);
        long start = System.nanoTime();

        Optional<ProgrammingExercise> programmingExercise = programmingExerciseRepository.findById(exerciseId);

        if (programmingExercise.isEmpty()) {
            return notFound();
        }

        if (!authCheckService.isAtLeastInstructorForExercise(programmingExercise.get())) {
            return forbidden();
        }

        var language = programmingExercise.get().getProgrammingLanguage();
        ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.getProgrammingLanguageFeatures(language);

        if (!programmingLanguageFeature.isPlagiarismCheckSupported()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "programmingLanguageNotSupported",
                    "Artemis does not support plagiarism checks for the programming language " + language)).body(null);
        }

        File zipFile = programmingExerciseExportService.checkPlagiarismWithJPlagReport(exerciseId, similarityThreshold, minimumScore);

        if (zipFile == null) {
            return ResponseEntity.badRequest().headers(
                    HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "internalServerError", "Insufficient amount of comparisons available for comparison."))
                    .body(null);
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));

        log.info("Check plagiarism of programming exercise {} with title '{}' was successful in {}.", programmingExercise.get().getId(), programmingExercise.get().getTitle(),
                formatDurationFrom(start));

        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
    }

    /**
     * Unlock all repositories of the given programming exercise.
     *
     * @param exerciseId of the exercise
     * @return The ResponseEntity with status 200 (OK) or with status 404 (Not Found) if the exerciseId is invalid
     */
    @PutMapping(value = Endpoints.UNLOCK_ALL_REPOSITORIES)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> unlockAllRepositories(@PathVariable Long exerciseId) {
        log.info("REST request to unlock all repositories of programming exercise {}", exerciseId);

        Optional<ProgrammingExercise> programmingExerciseOptional = programmingExerciseRepository.findById(exerciseId);
        if (programmingExerciseOptional.isEmpty()) {
            return notFound();
        }
        ProgrammingExercise programmingExercise = programmingExerciseOptional.get();
        if (!authCheckService.isAtLeastInstructorForExercise(programmingExercise)) {
            return forbidden();
        }

        programmingExerciseService.unlockAllRepositories(exerciseId);
        log.info("Unlocked all repositories of programming exercise {} upon manual request", exerciseId);
        return ResponseEntity.ok().build();
    }

    /**
     * Lock all repositories of the given programming exercise.
     *
     * @param exerciseId of the exercise
     * @return The ResponseEntity with status 200 (OK) or with status 404 (Not Found) if the exerciseId is invalid
     */
    @PutMapping(value = Endpoints.LOCK_ALL_REPOSITORIES)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> lockAllRepositories(@PathVariable Long exerciseId) {
        log.info("REST request to lock all repositories of programming exercise {}", exerciseId);

        Optional<ProgrammingExercise> programmingExerciseOptional = programmingExerciseRepository.findById(exerciseId);
        if (programmingExerciseOptional.isEmpty()) {
            return notFound();
        }
        ProgrammingExercise programmingExercise = programmingExerciseOptional.get();
        if (!authCheckService.isAtLeastInstructorForExercise(programmingExercise)) {
            return forbidden();
        }

        programmingExerciseService.lockAllRepositories(exerciseId);
        log.info("Locked all repositories of programming exercise {} upon manual request", exerciseId);
        return ResponseEntity.ok().build();
    }

    public static final class Endpoints {

        public static final String ROOT = "/api";

        public static final String PROGRAMMING_EXERCISES = "/programming-exercises";

        public static final String SETUP = PROGRAMMING_EXERCISES + "/setup";

        public static final String GET_FOR_COURSE = "/courses/{courseId}/programming-exercises";

        public static final String IMPORT = PROGRAMMING_EXERCISES + "/import/{sourceExerciseId}";

        public static final String PROGRAMMING_EXERCISE = PROGRAMMING_EXERCISES + "/{exerciseId}";

        public static final String PROBLEM = PROGRAMMING_EXERCISE + "/problem-statement";

        public static final String TIMELINE = PROGRAMMING_EXERCISES + "/timeline";

        public static final String PROGRAMMING_EXERCISE_WITH_PARTICIPATIONS = PROGRAMMING_EXERCISE + "/with-participations";

        public static final String PROGRAMMING_EXERCISE_WITH_TEMPLATE_AND_SOLUTION_PARTICIPATION = PROGRAMMING_EXERCISE + "/with-template-and-solution-participation";

        public static final String COMBINE_COMMITS = PROGRAMMING_EXERCISE + "/combine-template-commits";

        public static final String EXPORT_SUBMISSIONS_BY_PARTICIPANTS = PROGRAMMING_EXERCISE + "/export-repos-by-participant-identifiers/{participantIdentifiers}";

        public static final String EXPORT_SUBMISSIONS_BY_PARTICIPATIONS = PROGRAMMING_EXERCISE + "/export-repos-by-participation-ids/{participationIds}";

        public static final String EXPORT_INSTRUCTOR_REPOSITORY = PROGRAMMING_EXERCISE + "/export-instructor-repository/{repositoryType}";

        public static final String GENERATE_TESTS = PROGRAMMING_EXERCISE + "/generate-tests";

        public static final String CHECK_PLAGIARISM = PROGRAMMING_EXERCISE + "/check-plagiarism";

        public static final String CHECK_PLAGIARISM_JPLAG_REPORT = PROGRAMMING_EXERCISE + "/check-plagiarism-jplag-report";

        public static final String TEST_CASE_STATE = PROGRAMMING_EXERCISE + "/test-case-state";

        public static final String UNLOCK_ALL_REPOSITORIES = PROGRAMMING_EXERCISE + "/unlock-all-repositories";

        public static final String LOCK_ALL_REPOSITORIES = PROGRAMMING_EXERCISE + "/lock-all-repositories";

        private Endpoints() {
        }
    }

    public static final class ErrorKeys {

        public static final String INVALID_TEMPLATE_REPOSITORY_URL = "invalid.template.repository.url";

        public static final String INVALID_SOLUTION_REPOSITORY_URL = "invalid.solution.repository.url";

        public static final String INVALID_TEMPLATE_BUILD_PLAN_ID = "invalid.template.build.plan.id";

        public static final String INVALID_SOLUTION_BUILD_PLAN_ID = "invalid.solution.build.plan.id";

        private ErrorKeys() {
        }
    }
}
