package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.ALLOWED_CHECKOUT_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_ENVIRONMENT_VARIABLES_DOCKER_FLAG_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.buildagent.dto.DockerFlagsDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.exception.ProgrammingExerciseErrorKeys;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

@Service
@Lazy
@Profile(PROFILE_CORE)
public class ProgrammingExerciseValidationService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseValidationService.class);

    // The minimum memory that a Docker container can be assigned is 6MB. This is a Docker limitation.
    private static final int MIN_DOCKER_MEMORY_MB = 6;

    /**
     * Java package name Regex according to Java 14 JLS
     * (<a href="https://docs.oracle.com/javase/specs/jls/se14/html/jls-7.html#jls-7.4.1">https://docs.oracle.com/javase/specs/jls/se14/html/jls-7.html#jls-7.4.1</a>)
     * with the restriction to a-z,A-Z,_ as "Java letter" and 0-9 as digits due to JavaScript/Browser Unicode character class limitations
     */
    private static final String PACKAGE_NAME_REGEX_FOR_JAVA_KOTLIN = "^(?!.*(?:\\.|^)(?:abstract|continue|for|new|switch|assert|default|if|package|synchronized|boolean|do|goto|private|this|break|double|implements|protected|throw|byte|else|import|public|throws|case|enum|instanceof|return|transient|catch|extends|int|short|try|char|final|interface|static|void|class|finally|long|strictfp|volatile|const|float|native|super|while|_|true|false|null)(?:\\.|$))[A-Z_a-z]\\w*(?:\\.[A-Z_a-z]\\w*)*$";

    /**
     * Swift package name Regex derived from
     * (<a href="https://docs.swift.org/swift-book/ReferenceManual/LexicalStructure.html#ID412">https://docs.swift.org/swift-book/ReferenceManual/LexicalStructure.html#ID412</a>),
     * with the restriction to a-z,A-Z as "Swift letter" and 0-9 as digits where no separators are allowed
     */
    private static final String PACKAGE_NAME_REGEX_FOR_SWIFT = "^(?!(?:associatedtype|class|deinit|enum|extension|fileprivate|func|import|init|inout|internal|let|open|operator|private|protocol|public|rethrows|static|struct|subscript|typealias|var|break|case|continue|default|defer|do|else|fallthrough|for|guard|if|in|repeat|return|switch|where|while|as|Any|catch|false|is|nil|super|self|Self|throw|throws|true|try|_|[sS]wift)$)[A-Za-z][\\dA-Za-z]*$";

    /**
     * Go package name Regex derived from <a href="https://go.dev/ref/spec#Package_clause">The Go Programming Language Specification</a> limited to ASCII. Package names are
     * identifiers.
     * They allow letters, digits and underscore. They cannot start with a digit. The package name cannot be a keyword or "_".
     */
    private static final String PACKAGE_NAME_REGEX_FOR_GO = "^(?!(?:break|default|func|interface|select|case|defer|go|map|struct|chan|else|goto|package|switch|const|fallthrough|if|range|type|continue|for|import|return|var|_)$)[A-Za-z_][A-Za-z0-9_]*$";

    /**
     * Dart package name Regex derived from <a href="https://dart.dev/tools/pub/pubspec#name">the pubspec file reference</a>. The reserved words not usable as identifiers are
     * derived from <a href="https://spec.dart.dev/DartLangSpecDraft.pdf">the Dart Programming Language Specification</a>.
     * Package names are lowercase identifiers which are usable for variables. This excludes reserved words, await and yield. test and artemis_test are also disallowed.
     */
    private static final String PACKAGE_NAME_REGEX_FOR_DART = "^(?!(?:assert|await|break|case|catch|class|const|continue|default|do|else|enum|extends|false|final|finally|for|if|in|is|new|null|rethrow|return|super|switch|this|throw|true|try|var|void|while|with|yield|test|artemis_test)$)"
            + "[a-z_][a-z0-9_]*$";

    private static final Pattern PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN = Pattern.compile(PACKAGE_NAME_REGEX_FOR_JAVA_KOTLIN);

    private static final Pattern PACKAGE_NAME_PATTERN_FOR_SWIFT = Pattern.compile(PACKAGE_NAME_REGEX_FOR_SWIFT);

    private static final Pattern PACKAGE_NAME_PATTERN_FOR_GO = Pattern.compile(PACKAGE_NAME_REGEX_FOR_GO);

    private static final Pattern PACKAGE_NAME_PATTERN_FOR_DART = Pattern.compile(PACKAGE_NAME_REGEX_FOR_DART);

    private final AuxiliaryRepositoryService auxiliaryRepositoryService;

    private final SubmissionPolicyService submissionPolicyService;

    private final Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final ProgrammingExerciseBuildConfigService programmingExerciseBuildConfigService;

    private final Optional<VersionControlService> versionControlService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    public ProgrammingExerciseValidationService(AuxiliaryRepositoryService auxiliaryRepositoryService, ProgrammingExerciseRepository programmingExerciseRepository,
            SubmissionPolicyService submissionPolicyService, Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, ProgrammingExerciseBuildConfigService programmingExerciseBuildConfigService,
            Optional<VersionControlService> versionControlService1, ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository) {
        this.auxiliaryRepositoryService = auxiliaryRepositoryService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.submissionPolicyService = submissionPolicyService;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.programmingExerciseBuildConfigService = programmingExerciseBuildConfigService;
        this.versionControlService = versionControlService1;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
    }

    /**
     * validates the settings of a new programming exercise
     *
     * @param programmingExercise The programming exercise that should be validated
     * @param course              The course the programming exercise should be created in or imported to
     */
    public void validateNewProgrammingExerciseSettings(ProgrammingExercise programmingExercise, Course course) {
        if (programmingExercise.getId() != null) {
            throw new BadRequestAlertException("A new programmingExercise cannot already have an ID", "Exercise", "idexists");
        }

        programmingExercise.validateGeneralSettings();
        programmingExercise.validateProgrammingSettings();
        programmingExercise.validateSettingsForFeedbackRequest();
        validateCustomCheckoutPaths(programmingExercise);
        validateDockerFlags(programmingExercise);
        auxiliaryRepositoryService.validateAndAddAuxiliaryRepositoriesOfProgrammingExercise(programmingExercise, programmingExercise.getAuxiliaryRepositories());
        submissionPolicyService.validateSubmissionPolicyCreation(programmingExercise);

        ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.orElseThrow()
                .getProgrammingLanguageFeatures(programmingExercise.getProgrammingLanguage());

        validatePackageName(programmingExercise, programmingLanguageFeature);
        validateProjectType(programmingExercise, programmingLanguageFeature);

        ProgrammingExerciseBuildConfig buildConfig = programmingExercise.getBuildConfig();

        // Check if checkout solution repository is enabled
        if (buildConfig.getCheckoutSolutionRepository() && !programmingLanguageFeature.checkoutSolutionRepositoryAllowed()) {
            throw new BadRequestAlertException("Checkout solution repository is not supported for this programming language", "Exercise", "checkoutSolutionRepositoryNotSupported");
        }

        programmingExerciseRepository.validateCourseSettings(programmingExercise, course);
        validateStaticCodeAnalysisSettings(programmingExercise);

        programmingExercise.generateAndSetProjectKey();
        checkIfProjectExists(programmingExercise);

    }

    private void validateProjectType(ProgrammingExercise programmingExercise, ProgrammingLanguageFeature programmingLanguageFeature) {
        // Check if project type is selected
        if (!programmingLanguageFeature.projectTypes().isEmpty()) {
            if (programmingExercise.getProjectType() == null) {
                throw new BadRequestAlertException("The project type is not set", "Exercise", "projectTypeNotSet");
            }
            if (!programmingLanguageFeature.projectTypes().contains(programmingExercise.getProjectType())) {
                throw new BadRequestAlertException("The project type is not supported for this programming language", "Exercise", "projectTypeNotSupported");
            }
        }
        else if (programmingExercise.getProjectType() != null) {
            throw new BadRequestAlertException("The project type is set but not supported", "Exercise", "projectTypeSet");
        }
    }

    private void validatePackageName(ProgrammingExercise programmingExercise, ProgrammingLanguageFeature programmingLanguageFeature) {
        if (!programmingLanguageFeature.packageNameRequired()) {
            return;
        }
        // Check if package name is set
        if (programmingExercise.getPackageName() == null) {
            throw new BadRequestAlertException("The package name is invalid", "Exercise", "packagenameInvalid");
        }

        // Check if package name matches regex
        Matcher packageNameMatcher = switch (programmingExercise.getProgrammingLanguage()) {
            case JAVA, KOTLIN -> PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN.matcher(programmingExercise.getPackageName());
            case SWIFT -> PACKAGE_NAME_PATTERN_FOR_SWIFT.matcher(programmingExercise.getPackageName());
            case GO -> PACKAGE_NAME_PATTERN_FOR_GO.matcher(programmingExercise.getPackageName());
            case DART -> PACKAGE_NAME_PATTERN_FOR_DART.matcher(programmingExercise.getPackageName());
            default -> throw new IllegalArgumentException("Programming language not supported");
        };
        if (!packageNameMatcher.matches()) {
            throw new BadRequestAlertException("The package name is invalid", "Exercise", "packagenameInvalid");
        }
    }

    private void validateCustomCheckoutPaths(ProgrammingExercise programmingExercise) {
        var buildConfig = programmingExercise.getBuildConfig();

        boolean assignmentCheckoutPathIsValid = isValidCheckoutPath(buildConfig.getAssignmentCheckoutPath());
        boolean solutionCheckoutPathIsValid = isValidCheckoutPath(buildConfig.getSolutionCheckoutPath());
        boolean testCheckoutPathIsValid = isValidCheckoutPath(buildConfig.getTestCheckoutPath());

        if (!assignmentCheckoutPathIsValid || !solutionCheckoutPathIsValid || !testCheckoutPathIsValid) {
            throw new BadRequestAlertException("The custom checkout paths are invalid", "Exercise", "checkoutDirectoriesInvalid");
        }
    }

    private boolean isValidCheckoutPath(String checkoutPath) {
        // Checkout paths are optional for the assignment, solution, and test repositories. If not set, the default path is used.
        if (checkoutPath == null) {
            return true;
        }
        Matcher matcher = ALLOWED_CHECKOUT_DIRECTORY.matcher(checkoutPath);
        return matcher.matches();
    }

    /**
     * Validates static code analysis settings
     *
     * @param programmingExercise exercise to validate
     */
    public void validateStaticCodeAnalysisSettings(ProgrammingExercise programmingExercise) {
        ProgrammingLanguageFeature programmingLanguageFeature = programmingLanguageFeatureService.orElseThrow()
                .getProgrammingLanguageFeatures(programmingExercise.getProgrammingLanguage());
        programmingExercise.validateStaticCodeAnalysisSettings(programmingLanguageFeature);
    }

    /**
     * Validates the settings of an updated programming exercise. Checks if the custom checkout paths have changed.
     *
     * @param originalProgrammingExercise The original programming exercise
     * @param updatedProgrammingExercise  The updated programming exercise
     */
    public void validateCheckoutDirectoriesUnchanged(ProgrammingExercise originalProgrammingExercise, ProgrammingExercise updatedProgrammingExercise) {
        var originalBuildConfig = originalProgrammingExercise.getBuildConfig();
        var updatedBuildConfig = updatedProgrammingExercise.getBuildConfig();
        if (!Objects.equals(originalBuildConfig.getAssignmentCheckoutPath(), updatedBuildConfig.getAssignmentCheckoutPath())
                || !Objects.equals(originalBuildConfig.getSolutionCheckoutPath(), updatedBuildConfig.getSolutionCheckoutPath())
                || !Objects.equals(originalBuildConfig.getTestCheckoutPath(), updatedBuildConfig.getTestCheckoutPath())) {
            throw new BadRequestAlertException("The custom checkout paths cannot be changed!", "programmingExercise", "checkoutDirectoriesChanged");
        }
    }

    /**
     * Validates the network access feature for the given programming language.
     * Currently, SWIFT and HASKELL do not support disabling the network access feature.
     *
     * @param programmingExercise the programming exercise to validate
     */
    public void validateDockerFlags(ProgrammingExercise programmingExercise) {
        ProgrammingExerciseBuildConfig buildConfig = programmingExercise.getBuildConfig();
        DockerFlagsDTO dockerFlagsDTO;
        try {
            dockerFlagsDTO = programmingExerciseBuildConfigService.parseDockerFlags(buildConfig);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestAlertException("Error while parsing the docker flags", "Exercise", "dockerFlagsParsingError");
        }

        if (dockerFlagsDTO == null) {
            return;
        }

        if (dockerFlagsDTO.env() != null) {
            for (var entry : dockerFlagsDTO.env().entrySet()) {
                if (entry.getKey().length() > MAX_ENVIRONMENT_VARIABLES_DOCKER_FLAG_LENGTH || entry.getValue().length() > MAX_ENVIRONMENT_VARIABLES_DOCKER_FLAG_LENGTH) {
                    throw new BadRequestAlertException("The environment variables are too long. Max " + MAX_ENVIRONMENT_VARIABLES_DOCKER_FLAG_LENGTH + " chars", "Exercise",
                            "envVariablesTooLong");
                }
            }
        }

        if (dockerFlagsDTO.memory() < MIN_DOCKER_MEMORY_MB) {
            throw new BadRequestAlertException("The memory limit is invalid. The minimum memory limit is " + MIN_DOCKER_MEMORY_MB + "MB", "Exercise", "memoryLimitInvalid");
        }

        if (dockerFlagsDTO.cpuCount() <= 0) {
            throw new BadRequestAlertException("The cpu count is invalid. The minimum cpu count is 1", "Exercise", "cpuCountInvalid");
        }

        if (dockerFlagsDTO.memorySwap() < 0) {
            throw new BadRequestAlertException("The memory swap limit is invalid. The minimum memory swap limit is 0", "Exercise", "memorySwapLimitInvalid");
        }
    }

    /**
     * Checks if the project for the given programming exercise already exists in the version control system (VCS) and in the continuous integration system (CIS).
     * The check is done based on the project key (course short name + exercise short name) and the project name (course short name + exercise title).
     * This prevents errors then the actual projects will be generated later on.
     * An error response is returned in case the project does already exist. This will then e.g. stop the generation (or import) of the programming exercise.
     *
     * @param programmingExercise a typically new programming exercise for which the corresponding VCS and CIS projects should not yet exist.
     */
    public void checkIfProjectExists(ProgrammingExercise programmingExercise) {
        String projectKey = programmingExercise.getProjectKey();
        String projectName = programmingExercise.getProjectName();

        boolean projectExists = versionControlService.orElseThrow().checkIfProjectExists(projectKey, projectName);
        if (projectExists) {
            var errorMessageVcs = "Project already exists on the Version Control Server: " + projectName + ". Please choose a different title and short name!";
            throw new BadRequestAlertException(errorMessageVcs, "ProgrammingExercise", "vcsProjectExists");
        }
        String errorMessageCis = continuousIntegrationService.orElseThrow().checkIfProjectExists(projectKey, projectName);
        if (errorMessageCis != null) {
            throw new BadRequestAlertException(errorMessageCis, "ProgrammingExercise", "ciProjectExists");
        }
        // means the project does not exist in version control server and does not exist in continuous integration server
    }

    /**
     * Pre-Checks if a project with the same ProjectKey or ProjectName already exists in the version control system (VCS) and in the continuous integration system (CIS).
     * The check is done based on a generated project key (course short name + exercise short name) and the project name (course short name + exercise title).
     *
     * @param programmingExercise a typically new programming exercise for which the corresponding VCS and CIS projects should not yet exist.
     * @param courseShortName     the shortName of the course the programming exercise should be imported in
     * @return true if a project with the same ProjectKey or ProjectName already exists, otherwise false
     */
    public boolean preCheckProjectExistsOnVCSOrCI(ProgrammingExercise programmingExercise, String courseShortName) {
        String projectKey = (courseShortName + programmingExercise.getShortName().replaceAll("\\s+", "")).toUpperCase();
        String projectName = courseShortName + " " + programmingExercise.getTitle();
        log.debug("Project Key: {}", projectKey);
        log.debug("Project Name: {}", projectName);

        boolean projectExists = versionControlService.orElseThrow().checkIfProjectExists(projectKey, projectName);
        if (projectExists) {
            return true;
        }
        String errorMessageCis = continuousIntegrationService.orElseThrow().checkIfProjectExists(projectKey, projectName);
        // means the project does not exist in version control server and does not exist in continuous integration server
        return errorMessageCis != null;
    }

    /**
     * Checks whether the exercise to be updated has valid references to its template and solution repositories and build plans.
     *
     * @param exercise the programming exercise to be checked
     * @throws BadRequestAlertException if one of the references is invalid
     */
    public void checkProgrammingExerciseForError(ProgrammingExercise exercise) {
        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        VersionControlService versionControl = versionControlService.orElseThrow();

        if (!continuousIntegration.checkIfBuildPlanExists(exercise.getProjectKey(), exercise.getTemplateBuildPlanId())) {
            throw new BadRequestAlertException("The Template Build Plan ID seems to be invalid.", "Exercise", ProgrammingExerciseErrorKeys.INVALID_TEMPLATE_BUILD_PLAN_ID);
        }
        if (exercise.getVcsTemplateRepositoryUri() == null || !versionControl.repositoryUriIsValid(exercise.getVcsTemplateRepositoryUri())) {
            throw new BadRequestAlertException("The Template Repository URI seems to be invalid.", "Exercise", ProgrammingExerciseErrorKeys.INVALID_TEMPLATE_REPOSITORY_URL);
        }
        if (exercise.getSolutionBuildPlanId() != null && !continuousIntegration.checkIfBuildPlanExists(exercise.getProjectKey(), exercise.getSolutionBuildPlanId())) {
            throw new BadRequestAlertException("The Solution Build Plan ID seems to be invalid.", "Exercise", ProgrammingExerciseErrorKeys.INVALID_SOLUTION_BUILD_PLAN_ID);
        }
        var solutionRepositoryUri = exercise.getVcsSolutionRepositoryUri();
        if (solutionRepositoryUri != null && !versionControl.repositoryUriIsValid(solutionRepositoryUri)) {
            throw new BadRequestAlertException("The Solution Repository URI seems to be invalid.", "Exercise", ProgrammingExerciseErrorKeys.INVALID_SOLUTION_REPOSITORY_URL);
        }

        // It has already been checked when setting the test case weights that their sum is at least >= 0.
        // Only when changing the assessment format to automatic an additional check for > 0 has to be performed.
        if (exercise.getAssessmentType() == AssessmentType.AUTOMATIC) {
            final Set<ProgrammingExerciseTestCase> testCases = programmingExerciseTestCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);
            if (!ProgrammingExerciseTestCaseService.isTestCaseWeightSumValid(testCases)) {
                throw new BadRequestAlertException("For exercises with only automatic assignment at least one test case weight must be greater than zero.", "Exercise",
                        ProgrammingExerciseErrorKeys.INVALID_TEST_CASE_WEIGHTS);
            }
        }
    }

}
