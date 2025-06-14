package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.ALLOWED_CHECKOUT_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_ENVIRONMENT_VARIABLES_DOCKER_FLAG_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.SOLUTION;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.TEMPLATE;
import static de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository.ProgrammingExerciseFetchOptions.AuxiliaryRepositories;
import static de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository.ProgrammingExerciseFetchOptions.GradingCriteria;
import static de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository.SolutionParticipationFetchOptions;
import static de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository.TemplateParticipationFetchOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.buildagent.dto.DockerFlagsDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.dto.aeolus.Windfile;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseGitDiffReportRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.service.structureoraclegenerator.OracleGenerator;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseService {

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

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

    // The minimum memory that a Docker container can be assigned is 6MB. This is a Docker limitation.
    private static final int MIN_DOCKER_MEMORY_MB = 6;

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final GitService gitService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    private final ParticipationRepository participationRepository;

    private final ParticipationDeletionService participationDeletionService;

    private final UserRepository userRepository;

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ResultRepository resultRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository;

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final AuxiliaryRepositoryService auxiliaryRepositoryService;

    private final SubmissionPolicyService submissionPolicyService;

    private final Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService;

    private final ChannelService channelService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    private final Optional<AeolusTemplateService> aeolusTemplateService;

    private final Optional<BuildScriptGenerationService> buildScriptGenerationService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProfileService profileService;

    private final ExerciseService exerciseService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final ProgrammingExerciseBuildConfigService programmingExerciseBuildConfigService;

    public ProgrammingExerciseService(ProgrammingExerciseRepository programmingExerciseRepository, GitService gitService, Optional<VersionControlService> versionControlService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ParticipationDeletionService participationDeletionService,
            ParticipationRepository participationRepository, ResultRepository resultRepository, UserRepository userRepository,
            GroupNotificationScheduleService groupNotificationScheduleService, InstanceMessageSendService instanceMessageSendService,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, ProgrammingExerciseTaskRepository programmingExerciseTaskRepository,
            ProgrammingExerciseTaskService programmingExerciseTaskService, ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository,
            ExerciseSpecificationService exerciseSpecificationService, ProgrammingExerciseRepositoryService programmingExerciseRepositoryService,
            AuxiliaryRepositoryService auxiliaryRepositoryService, SubmissionPolicyService submissionPolicyService,
            Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService, ChannelService channelService, ProgrammingSubmissionService programmingSubmissionService,
            Optional<IrisSettingsApi> irisSettingsApi, Optional<AeolusTemplateService> aeolusTemplateService, Optional<BuildScriptGenerationService> buildScriptGenerationService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProfileService profileService, ExerciseService exerciseService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, Optional<CompetencyProgressApi> competencyProgressApi,
            ProgrammingExerciseBuildConfigService programmingExerciseBuildConfigService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gitService = gitService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.participationRepository = participationRepository;
        this.participationDeletionService = participationDeletionService;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.groupNotificationScheduleService = groupNotificationScheduleService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseGitDiffReportRepository = programmingExerciseGitDiffReportRepository;
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.auxiliaryRepositoryService = auxiliaryRepositoryService;
        this.submissionPolicyService = submissionPolicyService;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.channelService = channelService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.irisSettingsApi = irisSettingsApi;
        this.aeolusTemplateService = aeolusTemplateService;
        this.buildScriptGenerationService = buildScriptGenerationService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.profileService = profileService;
        this.exerciseService = exerciseService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.competencyProgressApi = competencyProgressApi;
        this.programmingExerciseBuildConfigService = programmingExerciseBuildConfigService;
    }

    /**
     * Setups the context of a new programming exercise. This includes:
     * <ul>
     * <li>The VCS project</li>
     * <li>All repositories (test, exercise, solution)</li>
     * <li>The template and solution participation</li>
     * <li>VCS webhooks</li>
     * </ul>
     * The exercise gets set up in the following order:
     * <ol>
     * <li>Create all repositories for the new exercise</li>
     * <li>Setup template and push it to the repositories</li>
     * <li>Setup new build plans for exercise</li>
     * <li>Add all webhooks</li>
     * <li>Init scheduled jobs for exercise maintenance</li>
     * </ol>
     *
     * @param programmingExercise The programmingExercise that should be setup
     * @return The new setup exercise
     * @throws GitAPIException If something during the communication with the remote Git repository went wrong
     * @throws IOException     If the template files couldn't be read
     */
    public ProgrammingExercise createProgrammingExercise(ProgrammingExercise programmingExercise) throws GitAPIException, IOException {
        final User exerciseCreator = userRepository.getUser();

        // The client sends a solution and template participation object (filled with null values) when creating a programming exercise.
        // When saving the object leads to an exception at runtime.
        // As the participations objects are just dummy values representing the data structure in the client, we set this to null.
        // See https://github.com/ls1intum/Artemis/pull/7451/files#r1459228917
        programmingExercise.setSolutionParticipation(null);
        programmingExercise.setTemplateParticipation(null);
        programmingExercise.getBuildConfig().setId(null);

        // We save once in order to generate an id for the programming exercise
        var savedBuildConfig = programmingExerciseBuildConfigRepository.saveAndFlush(programmingExercise.getBuildConfig());
        programmingExercise.setBuildConfig(savedBuildConfig);

        var savedProgrammingExercise = exerciseService.saveWithCompetencyLinks(programmingExercise, programmingExerciseRepository::saveForCreation);

        savedProgrammingExercise.getBuildConfig().setProgrammingExercise(savedProgrammingExercise);
        programmingExerciseBuildConfigRepository.save(savedProgrammingExercise.getBuildConfig());
        // Step 1: Setting constant facts for a programming exercise
        savedProgrammingExercise.generateAndSetProjectKey();
        savedProgrammingExercise.getBuildConfig().setBranch(defaultBranch);

        // Step 2: Creating repositories for new exercise
        programmingExerciseRepositoryService.createRepositoriesForNewExercise(savedProgrammingExercise);
        // Step 3: Initializing solution and template participation
        initParticipations(savedProgrammingExercise);

        // Step 4a: Setting build plan IDs and URLs for template and solution participation
        setURLsAndBuildPlanIDsForNewExercise(savedProgrammingExercise);

        // Step 4b: Connecting base participations with the exercise
        connectBaseParticipationsToExerciseAndSave(savedProgrammingExercise);

        programmingExerciseBuildConfigRepository.saveAndFlush(savedProgrammingExercise.getBuildConfig());
        savedProgrammingExercise = programmingExerciseRepository.saveForCreation(savedProgrammingExercise);

        // Step 4c: Connect auxiliary repositories
        connectAuxiliaryRepositoriesToExercise(savedProgrammingExercise);

        // Step 5: Setup exercise template
        programmingExerciseRepositoryService.setupExerciseTemplate(savedProgrammingExercise, exerciseCreator);

        // Step 6: Create initial submission
        programmingSubmissionService.createInitialSubmissions(savedProgrammingExercise);

        // Step 7: Make sure that plagiarism detection config does not use existing id
        Optional.ofNullable(savedProgrammingExercise.getPlagiarismDetectionConfig()).ifPresent(it -> it.setId(null));

        // Step 8: For LocalCI and Aeolus, we store the build plan definition in the database as a windfile, we don't do that for Jenkins as
        // we want to use the default approach of Jenkinsfiles and Build Plans if no customizations are made
        if (aeolusTemplateService.isPresent() && savedProgrammingExercise.getBuildConfig().getBuildPlanConfiguration() == null && !profileService.isJenkinsActive()) {
            Windfile windfile = aeolusTemplateService.get().getDefaultWindfileFor(savedProgrammingExercise);
            if (windfile != null) {
                savedProgrammingExercise.getBuildConfig().setBuildPlanConfiguration(new ObjectMapper().writeValueAsString(windfile));
                programmingExerciseBuildConfigRepository.saveAndFlush(savedProgrammingExercise.getBuildConfig());
            }
            else {
                log.warn("No windfile for the settings of exercise {}", savedProgrammingExercise.getId());
            }
        }

        // Step 9: Create exercise channel
        channelService.createExerciseChannel(savedProgrammingExercise, Optional.ofNullable(programmingExercise.getChannelName()));

        // Step 10: Setup build plans for template and solution participation
        setupBuildPlansForNewExercise(savedProgrammingExercise);
        savedProgrammingExercise = programmingExerciseRepository.findForCreationByIdElseThrow(savedProgrammingExercise.getId());

        // Step 11: Update task from problem statement
        programmingExerciseTaskService.updateTasksFromProblemStatement(savedProgrammingExercise);

        // Step 12: Scheduling
        // Step 12a: Schedule operations
        scheduleOperations(savedProgrammingExercise.getId());
        // Step 12b: Check notifications for new exercise
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(savedProgrammingExercise);
        // Step 12c: Update student competency progress
        ProgrammingExercise finalSavedProgrammingExercise = savedProgrammingExercise;
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(finalSavedProgrammingExercise));

        // Step 13: Set Iris settings
        if (irisSettingsApi.isPresent()) {
            irisSettingsApi.get().setEnabledForExerciseByCategories(savedProgrammingExercise, new HashSet<>());
        }

        return programmingExerciseRepository.saveForCreation(savedProgrammingExercise);
    }

    public void scheduleOperations(Long programmingExerciseId) {
        instanceMessageSendService.sendProgrammingExerciseSchedule(programmingExerciseId);
    }

    public void cancelScheduledOperations(Long programmingExerciseId) {
        instanceMessageSendService.sendProgrammingExerciseScheduleCancel(programmingExerciseId);
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
     * Creates build plans for a new programming exercise.
     * 1. Create the project for the exercise on the CI Server
     * 2. Create template and solution build plan in this project
     * 3. Configure CI permissions
     * 4. Trigger initial build for template and solution build plan (if the exercise is not imported)
     *
     * @param programmingExercise Programming exercise for the build plans should be generated. The programming
     *                                exercise should contain a fully initialized template and solution participation.
     */
    public void setupBuildPlansForNewExercise(ProgrammingExercise programmingExercise) throws JsonProcessingException {
        // Get URLs for repos
        var exerciseRepoUri = programmingExercise.getVcsTemplateRepositoryUri();
        var testsRepoUri = programmingExercise.getVcsTestRepositoryUri();
        var solutionRepoUri = programmingExercise.getVcsSolutionRepositoryUri();

        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        continuousIntegration.createProjectForExercise(programmingExercise);
        // template build plan
        continuousIntegration.createBuildPlanForExercise(programmingExercise, TEMPLATE.getName(), exerciseRepoUri, testsRepoUri, solutionRepoUri);
        // solution build plan
        continuousIntegration.createBuildPlanForExercise(programmingExercise, SOLUTION.getName(), solutionRepoUri, testsRepoUri, solutionRepoUri);

        Windfile windfile = programmingExercise.getBuildConfig().getWindfile();
        if (windfile != null && buildScriptGenerationService.isPresent() && programmingExercise.getBuildConfig().getBuildScript() == null) {
            String script = buildScriptGenerationService.get().getScript(programmingExercise);
            programmingExercise.getBuildConfig().setBuildPlanConfiguration(new ObjectMapper().writeValueAsString(windfile));
            programmingExercise.getBuildConfig().setBuildScript(script);
            programmingExerciseBuildConfigRepository.saveAndFlush(programmingExercise.getBuildConfig());
        }

        // trigger BASE and SOLUTION build plans once here
        continuousIntegrationTriggerService.orElseThrow().triggerBuild(programmingExercise.getTemplateParticipation());
        continuousIntegrationTriggerService.orElseThrow().triggerBuild(programmingExercise.getSolutionParticipation());
    }

    /**
     * This method connects the new programming exercise with the template and solution participation
     *
     * @param programmingExercise the new programming exercise
     */
    public void connectBaseParticipationsToExerciseAndSave(ProgrammingExercise programmingExercise) {
        var templateParticipation = programmingExercise.getTemplateParticipation();
        var solutionParticipation = programmingExercise.getSolutionParticipation();
        templateParticipation.setProgrammingExercise(programmingExercise);
        solutionParticipation.setProgrammingExercise(programmingExercise);
        templateParticipation = templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        solutionParticipation = solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        programmingExercise.setTemplateParticipation(templateParticipation);
        programmingExercise.setSolutionParticipation(solutionParticipation);
    }

    private void connectAuxiliaryRepositoriesToExercise(ProgrammingExercise exercise) {
        List<AuxiliaryRepository> savedRepositories = new ArrayList<>(exercise.getAuxiliaryRepositories().stream().filter(repo -> repo.getId() != null).toList());
        exercise.getAuxiliaryRepositories().stream().filter(repository -> repository.getId() == null).forEach(repository -> {
            // We have to disconnect the exercise from the auxiliary repository
            // since the auxiliary repositories of an exercise are represented as
            // a sorted collection (list).
            repository.setExercise(null);
            repository = auxiliaryRepositoryRepository.save(repository);
            repository.setExercise(exercise);
            savedRepositories.add(repository);
        });
        exercise.setAuxiliaryRepositories(savedRepositories);
    }

    private void setURLsAndBuildPlanIDsForNewExercise(ProgrammingExercise programmingExercise) {
        final var projectKey = programmingExercise.getProjectKey();
        final var templateParticipation = programmingExercise.getTemplateParticipation();
        final var solutionParticipation = programmingExercise.getSolutionParticipation();
        final var templatePlanId = programmingExercise.generateBuildPlanId(TEMPLATE);
        final var solutionPlanId = programmingExercise.generateBuildPlanId(SOLUTION);
        final var exerciseRepoName = programmingExercise.generateRepositoryName(RepositoryType.TEMPLATE);
        final var solutionRepoName = programmingExercise.generateRepositoryName(RepositoryType.SOLUTION);
        final var testRepoName = programmingExercise.generateRepositoryName(RepositoryType.TESTS);

        VersionControlService versionControl = versionControlService.orElseThrow();
        templateParticipation.setBuildPlanId(templatePlanId); // Set build plan id to newly created BaseBuild plan
        templateParticipation.setRepositoryUri(versionControl.getCloneRepositoryUri(projectKey, exerciseRepoName).toString());
        solutionParticipation.setBuildPlanId(solutionPlanId);
        solutionParticipation.setRepositoryUri(versionControl.getCloneRepositoryUri(projectKey, solutionRepoName).toString());
        programmingExercise.setTestRepositoryUri(versionControl.getCloneRepositoryUri(projectKey, testRepoName).toString());
    }

    private void setURLsForAuxiliaryRepositoriesOfExercise(ProgrammingExercise programmingExercise) {
        programmingExercise.getAuxiliaryRepositories().forEach(repo -> repo.setRepositoryUri(versionControlService.orElseThrow()
                .getCloneRepositoryUri(programmingExercise.getProjectKey(), programmingExercise.generateRepositoryName(repo.getName())).toString()));
    }

    public static Path getProgrammingLanguageProjectTypePath(ProgrammingLanguage programmingLanguage, ProjectType projectType) {
        return getProgrammingLanguageTemplatePath(programmingLanguage).resolve(projectType.name().toLowerCase());
    }

    public static Path getProgrammingLanguageTemplatePath(ProgrammingLanguage programmingLanguage) {
        return Path.of("templates", programmingLanguage.name().toLowerCase());
    }

    /**
     * @param programmingExerciseBeforeUpdate the original programming exercise with its old values
     * @param updatedProgrammingExercise      the changed programming exercise with its new values
     * @param notificationText                optional text about the changes for a notification
     * @return the updates programming exercise from the database
     */
    public ProgrammingExercise updateProgrammingExercise(ProgrammingExercise programmingExerciseBeforeUpdate, ProgrammingExercise updatedProgrammingExercise,
            @Nullable String notificationText) throws JsonProcessingException {
        setURLsForAuxiliaryRepositoriesOfExercise(updatedProgrammingExercise);
        connectAuxiliaryRepositoriesToExercise(updatedProgrammingExercise);

        updateBuildPlanForExercise(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        channelService.updateExerciseChannel(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        String problemStatementWithTestNames = updatedProgrammingExercise.getProblemStatement();
        programmingExerciseTaskService.replaceTestNamesWithIds(updatedProgrammingExercise);
        programmingExerciseBuildConfigRepository.save(updatedProgrammingExercise.getBuildConfig());

        ProgrammingExercise savedProgrammingExercise = exerciseService.saveWithCompetencyLinks(updatedProgrammingExercise, programmingExerciseRepository::save);

        // The returned value should use test case names since it gets send back to the client
        savedProgrammingExercise.setProblemStatement(problemStatementWithTestNames);

        participationRepository.removeIndividualDueDatesIfBeforeDueDate(savedProgrammingExercise, programmingExerciseBeforeUpdate.getDueDate());
        programmingExerciseTaskService.updateTasksFromProblemStatement(savedProgrammingExercise);

        if (programmingExerciseBeforeUpdate.isCourseExercise()) {
            scheduleOperations(updatedProgrammingExercise.getId());
        }

        exerciseService.notifyAboutExerciseChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise, notificationText);

        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(programmingExerciseBeforeUpdate, Optional.of(updatedProgrammingExercise)));

        irisSettingsApi.ifPresent(api -> api.setEnabledForExerciseByCategories(savedProgrammingExercise, programmingExerciseBeforeUpdate.getCategories()));

        return savedProgrammingExercise;
    }

    /**
     * This method updates the build plan for the given programming exercise.
     * If LocalCI is not active, it deletes the old build plan and creates a new one if the build plan configuration has changed.
     *
     * @param programmingExerciseBeforeUpdate the original programming exercise with its old values
     * @param updatedProgrammingExercise      the changed programming exercise with its new values
     */
    private void updateBuildPlanForExercise(ProgrammingExercise programmingExerciseBeforeUpdate, ProgrammingExercise updatedProgrammingExercise) throws JsonProcessingException {
        if (continuousIntegrationService.isEmpty() || Objects.equals(programmingExerciseBeforeUpdate.getBuildConfig().getBuildPlanConfiguration(),
                updatedProgrammingExercise.getBuildConfig().getBuildPlanConfiguration())) {
            return;
        }
        // we only update the build plan configuration if it has changed and is not null, otherwise we
        // do not have a valid exercise anymore
        if (updatedProgrammingExercise.getBuildConfig().getBuildPlanConfiguration() != null) {
            if (!profileService.isLocalCIActive()) {
                continuousIntegrationService.get().deleteProject(updatedProgrammingExercise.getProjectKey());
                continuousIntegrationService.get().createProjectForExercise(updatedProgrammingExercise);
                continuousIntegrationService.get().recreateBuildPlansForExercise(updatedProgrammingExercise);
                resetAllStudentBuildPlanIdsForExercise(updatedProgrammingExercise);
            }
            // For Aeolus, we have to regenerate the build script based on the new Windfile of the exercise.
            // We skip this for pure LocalCI to prevent the build script from being overwritten by the default one.
            if (profileService.isAeolusActive() && buildScriptGenerationService.isPresent()) {
                String script = buildScriptGenerationService.get().getScript(updatedProgrammingExercise);
                updatedProgrammingExercise.getBuildConfig().setBuildScript(script);
                programmingExerciseBuildConfigRepository.save(updatedProgrammingExercise.getBuildConfig());
            }
        }
        else {
            // if the user does not change the build plan configuration, we have to set the old one again
            updatedProgrammingExercise.getBuildConfig().setBuildPlanConfiguration(programmingExerciseBeforeUpdate.getBuildConfig().getBuildPlanConfiguration());
        }
    }

    /**
     * These methods set the values (initialization date and initialization state) of the template and solution participation.
     * If either participation is null, a new one will be created.
     *
     * @param programmingExercise The programming exercise
     */
    public void initParticipations(ProgrammingExercise programmingExercise) {
        var solutionParticipation = programmingExercise.getSolutionParticipation();
        var templateParticipation = programmingExercise.getTemplateParticipation();

        if (templateParticipation == null) {
            templateParticipation = new TemplateProgrammingExerciseParticipation();
            programmingExercise.setTemplateParticipation(templateParticipation);
        }
        if (solutionParticipation == null) {
            solutionParticipation = new SolutionProgrammingExerciseParticipation();
            programmingExercise.setSolutionParticipation(solutionParticipation);
        }

        solutionParticipation.setInitializationState(InitializationState.INITIALIZED);
        templateParticipation.setInitializationState(InitializationState.INITIALIZED);
        solutionParticipation.setInitializationDate(ZonedDateTime.now());
        templateParticipation.setInitializationDate(ZonedDateTime.now());
    }

    /**
     * Updates the timeline attributes of the given programming exercise
     *
     * @param updatedProgrammingExercise containing the changes that have to be saved
     * @param notificationText           optional text for a notification to all students about the update
     * @return the updated ProgrammingExercise object.
     */
    public ProgrammingExercise updateTimeline(ProgrammingExercise updatedProgrammingExercise, @Nullable String notificationText) {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(updatedProgrammingExercise.getId());

        // create slim copy of programmingExercise before the update - needed for notifications (only release date needed)
        ProgrammingExercise programmingExerciseBeforeUpdate = new ProgrammingExercise();
        programmingExerciseBeforeUpdate.setReleaseDate(programmingExercise.getReleaseDate());
        programmingExerciseBeforeUpdate.setStartDate(programmingExercise.getStartDate());
        programmingExerciseBeforeUpdate.setAssessmentDueDate(programmingExercise.getAssessmentDueDate());

        programmingExercise.setReleaseDate(updatedProgrammingExercise.getReleaseDate());
        programmingExercise.setStartDate(updatedProgrammingExercise.getStartDate());
        programmingExercise.setDueDate(updatedProgrammingExercise.getDueDate());
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(updatedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        programmingExercise.setAssessmentType(updatedProgrammingExercise.getAssessmentType());
        programmingExercise.setAssessmentDueDate(updatedProgrammingExercise.getAssessmentDueDate());
        programmingExercise.setExampleSolutionPublicationDate(updatedProgrammingExercise.getExampleSolutionPublicationDate());

        programmingExercise.validateDates();

        ProgrammingExercise savedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);
        groupNotificationScheduleService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(programmingExerciseBeforeUpdate, savedProgrammingExercise, notificationText);
        return savedProgrammingExercise;
    }

    /**
     * Updates the problem statement of the given programming exercise.
     *
     * @param programmingExercise The ProgrammingExercise of which the problem statement is updated.
     * @param problemStatement    markdown of the problem statement.
     * @param notificationText    optional text for a notification to all students about the update
     * @return the updated ProgrammingExercise object.
     * @throws EntityNotFoundException if there is no ProgrammingExercise for the given id.
     */
    public ProgrammingExercise updateProblemStatement(ProgrammingExercise programmingExercise, String problemStatement, @Nullable String notificationText)
            throws EntityNotFoundException {

        String oldProblemStatement = programmingExercise.getProblemStatement();
        programmingExercise.setProblemStatement(problemStatement);
        programmingExerciseTaskService.replaceTestNamesWithIds(programmingExercise);
        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);

        // Set the old problem statement again for notifyAboutExerciseChanges method, but don't save it
        programmingExercise.setProblemStatement(oldProblemStatement);

        programmingExerciseTaskService.updateTasksFromProblemStatement(updatedProgrammingExercise);

        exerciseService.notifyAboutExerciseChanges(programmingExercise, updatedProgrammingExercise, notificationText);

        return updatedProgrammingExercise;
    }

    /**
     * This method calls the StructureOracleGenerator, generates the string out of the JSON representation of the structure oracle of the programming exercise and returns true if
     * the file was updated or generated, false otherwise. This can happen if the contents of the file have not changed.
     *
     * @param solutionRepoUri The URL of the solution repository.
     * @param exerciseRepoUri The URL of the exercise repository.
     * @param testRepoUri     The URL of the tests' repository.
     * @param testsPath       The path to the tests' folder, e.g. the path inside the repository where the structure oracle file will be saved in.
     * @param user            The user who has initiated the action
     * @return True, if the structure oracle was successfully generated or updated, false if no changes to the file were made.
     * @throws IOException     If the URLs cannot be converted to actual {@link Path paths}
     * @throws GitAPIException If the checkout fails
     */
    public boolean generateStructureOracleFile(VcsRepositoryUri solutionRepoUri, VcsRepositoryUri exerciseRepoUri, VcsRepositoryUri testRepoUri, String testsPath, User user)
            throws IOException, GitAPIException {
        Repository solutionRepository = gitService.getOrCheckoutRepository(solutionRepoUri, true);
        Repository exerciseRepository = gitService.getOrCheckoutRepository(exerciseRepoUri, true);
        Repository testRepository = gitService.getOrCheckoutRepository(testRepoUri, true);

        gitService.resetToOriginHead(solutionRepository);
        gitService.pullIgnoreConflicts(solutionRepository);
        gitService.resetToOriginHead(exerciseRepository);
        gitService.pullIgnoreConflicts(exerciseRepository);
        gitService.resetToOriginHead(testRepository);
        gitService.pullIgnoreConflicts(testRepository);

        Path solutionRepositoryPath = solutionRepository.getLocalPath().toRealPath();
        Path exerciseRepositoryPath = exerciseRepository.getLocalPath().toRealPath();
        Path structureOraclePath = Path.of(testRepository.getLocalPath().toRealPath().toString(), testsPath, "test.json");

        String structureOracleJSON = OracleGenerator.generateStructureOracleJSON(solutionRepositoryPath, exerciseRepositoryPath);
        return saveAndPushStructuralOracle(user, testRepository, structureOraclePath, structureOracleJSON);
    }

    private boolean saveAndPushStructuralOracle(User user, Repository testRepository, Path structureOraclePath, String structureOracleJSON) throws IOException {
        // If the oracle file does not already exist, then save the generated string to the file.
        // If it does, check if the contents of the existing file are the same as the generated one.
        // If they are, do not push anything and inform the user about it.
        // If not, then update the oracle file by rewriting it and push the changes.
        if (!Files.exists(structureOraclePath)) {
            try {
                FileUtils.writeStringToFile(structureOraclePath.toFile(), structureOracleJSON, StandardCharsets.UTF_8);
                gitService.stageAllChanges(testRepository);
                gitService.commitAndPush(testRepository, "Generate the structure oracle file.", true, user);
                return true;
            }
            catch (GitAPIException e) {
                log.error("An exception occurred while pushing the structure oracle file to the test repository.", e);
                return false;
            }
        }
        else {
            Byte[] existingContents = ArrayUtils.toObject(Files.readAllBytes(structureOraclePath));
            Byte[] newContents = ArrayUtils.toObject(structureOracleJSON.getBytes());

            if (Arrays.deepEquals(existingContents, newContents)) {
                log.info("No changes to the oracle detected.");
                return false;
            }
            else {
                try {
                    FileUtils.writeStringToFile(structureOraclePath.toFile(), structureOracleJSON, StandardCharsets.UTF_8);
                    gitService.stageAllChanges(testRepository);
                    gitService.commitAndPush(testRepository, "Update the structure oracle file.", true, user);
                    return true;
                }
                catch (GitAPIException e) {
                    log.error("An exception occurred while pushing the structure oracle file to the test repository.", e);
                    return false;
                }
            }
        }
    }

    /**
     * Delete a programming exercise, including its template and solution participations.
     *
     * @param programmingExerciseId     id of the programming exercise to delete.
     * @param deleteBaseReposBuildPlans if true will also delete build plans and projects.
     */
    public void delete(Long programmingExerciseId, boolean deleteBaseReposBuildPlans) {
        // Note: This method does not accept a programming exercise to solve issues with nested Transactions.
        // It would be good to refactor the delete calls and move the validity checks down from the resources to the service methods (e.g. EntityNotFound).
        final var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseId)
                .orElseThrow(() -> new EntityNotFoundException("Programming Exercise", programmingExerciseId));

        // The delete operation cancels scheduled tasks (like locking/unlocking repositories)
        // As the programming exercise might already be deleted once the scheduling node receives the message, only the
        // id is used to cancel the scheduling. No interaction with the database is required.
        cancelScheduledOperations(programmingExercise.getId());

        if (deleteBaseReposBuildPlans) {
            deleteBuildPlans(programmingExercise);
            programmingExerciseRepositoryService.deleteRepositories(programmingExercise);
        }
        programmingExerciseRepositoryService.deleteLocalRepoCopies(programmingExercise);

        programmingExerciseGitDiffReportRepository.deleteByProgrammingExerciseId(programmingExerciseId);

        irisSettingsApi.ifPresent(api -> api.deleteSettingsFor(programmingExercise));

        SolutionProgrammingExerciseParticipation solutionProgrammingExerciseParticipation = programmingExercise.getSolutionParticipation();
        TemplateProgrammingExerciseParticipation templateProgrammingExerciseParticipation = programmingExercise.getTemplateParticipation();
        if (solutionProgrammingExerciseParticipation != null) {
            participationDeletionService.deleteResultsAndSubmissionsOfParticipation(solutionProgrammingExerciseParticipation.getId(), true);
        }
        if (templateProgrammingExerciseParticipation != null) {
            participationDeletionService.deleteResultsAndSubmissionsOfParticipation(templateProgrammingExerciseParticipation.getId(), true);
        }

        // Note: we fetch the programming exercise again here with student participations to avoid Hibernate issues during the delete operation below
        var programmingExerciseWithStudentParticipations = programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(programmingExerciseId);
        log.debug("Delete programming exercises with student participations: {}", programmingExerciseWithStudentParticipations.getStudentParticipations());
        // This will also delete the template & solution participation: we explicitly use deleteById to avoid potential Hibernate issues during deletion
        programmingExerciseRepository.deleteById(programmingExerciseId);
    }

    private void deleteBuildPlans(ProgrammingExercise programmingExercise) {
        final var templateBuildPlanId = programmingExercise.getTemplateBuildPlanId();
        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        if (templateBuildPlanId != null) {
            continuousIntegration.deleteBuildPlan(programmingExercise.getProjectKey(), templateBuildPlanId);
        }
        final var solutionBuildPlanId = programmingExercise.getSolutionBuildPlanId();
        if (solutionBuildPlanId != null) {
            continuousIntegration.deleteBuildPlan(programmingExercise.getProjectKey(), solutionBuildPlanId);
        }
        continuousIntegration.deleteProject(programmingExercise.getProjectKey());
    }

    public boolean hasAtLeastOneStudentResult(ProgrammingExercise programmingExercise) {
        // Is true if the exercise is released and has at least one result.
        // We can't use the resultService here due to a circular dependency issue.
        return resultRepository.existsBySubmission_Participation_Exercise_Id(programmingExercise.getId());
    }

    /**
     * Search for all programming exercises fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<ProgrammingExercise> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final boolean isCourseFilter, final boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        String searchTerm = search.getSearchTerm();
        PageRequest pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        Specification<ProgrammingExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        return getAllOnPageForSpecification(pageable, specification);
    }

    /**
     * Search for all programming exercises with SCA enabled and with a specific programming language.
     *
     * @param search              The search query defining the search term and the size of the returned page
     * @param isCourseFilter      Whether to search in the courses for exercises
     * @param isExamFilter        Whether to search in the groups for exercises
     * @param user                The user for whom to fetch all available exercises
     * @param programmingLanguage The result will only include exercises in this language
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<ProgrammingExercise> getAllWithSCAOnPageWithSize(SearchTermPageableSearchDTO<String> search, boolean isCourseFilter, boolean isExamFilter,
            ProgrammingLanguage programmingLanguage, User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        String searchTerm = search.getSearchTerm();
        PageRequest pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        Specification<ProgrammingExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        specification = specification.and(exerciseSpecificationService.createSCAFilter(programmingLanguage));
        return getAllOnPageForSpecification(pageable, specification);
    }

    private SearchResultPageDTO<ProgrammingExercise> getAllOnPageForSpecification(PageRequest pageable, Specification<ProgrammingExercise> specification) {
        Page<ProgrammingExercise> exercisePage = programmingExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
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
     * Delete all tasks with solution entries for an existing ProgrammingExercise.
     * This method can be used to reset the mappings in case of unconsidered edge cases.
     *
     * @param exerciseId of the exercise
     */
    public void deleteTasks(Long exerciseId) {
        List<ProgrammingExerciseTask> tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCaseElseThrow(exerciseId);
        programmingExerciseTaskRepository.deleteAll(tasks);
    }

    private void resetAllStudentBuildPlanIdsForExercise(ProgrammingExercise programmingExercise) {
        programmingExerciseStudentParticipationRepository.unsetBuildPlanIdForExercise(programmingExercise.getId());
    }

    /**
     * Load a programming exercise with eager
     * - auxiliary repositories
     * - template participation with submissions (and results if withSubmissionResults is true)
     * - solution participation with submissions (and results if withSubmissionResults is true)
     * - grading criteria (only if withGradingCriteria is true)
     *
     * @param exerciseId            the ID of the programming exercise to load
     * @param withSubmissionResults a flag indicating whether to include submission results
     * @param withGradingCriteria   a flag indicating whether to include grading criteria
     * @return the loaded programming exercise entity
     */
    public ProgrammingExercise loadProgrammingExercise(long exerciseId, boolean withSubmissionResults, boolean withGradingCriteria) {
        // 1. Load programming exercise, optionally with grading criteria
        final Set<ProgrammingExerciseRepository.ProgrammingExerciseFetchOptions> fetchOptions = withGradingCriteria ? Set.of(GradingCriteria, AuxiliaryRepositories)
                : Set.of(AuxiliaryRepositories);
        var programmingExercise = programmingExerciseRepository.findByIdWithDynamicFetchElseThrow(exerciseId, fetchOptions);

        // 2. Load template and solution participation, either with only submissions or with submissions and results
        final var templateFetchOptions = withSubmissionResults ? Set.of(TemplateParticipationFetchOptions.SubmissionsAndResults)
                : Set.of(TemplateParticipationFetchOptions.Submissions);
        final var templateParticipation = templateProgrammingExerciseParticipationRepository.findByExerciseIdWithDynamicFetchElseThrow(exerciseId, templateFetchOptions);

        final var solutionFetchOptions = withSubmissionResults ? Set.of(SolutionParticipationFetchOptions.SubmissionsAndResults)
                : Set.of(SolutionParticipationFetchOptions.Submissions);
        final var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByExerciseIdWithDynamicFetchElseThrow(exerciseId, solutionFetchOptions);

        programmingExercise.setSolutionParticipation(solutionParticipation);
        programmingExercise.setTemplateParticipation(templateParticipation);

        programmingExerciseTaskService.replaceTestIdsWithNames(programmingExercise);
        return programmingExercise;
    }

    /**
     * Load a programming exercise, only with eager auxiliary repositories
     *
     * @param exerciseId the ID of the programming exercise to load
     * @return the loaded programming exercise entity
     */
    public ProgrammingExercise loadProgrammingExerciseWithAuxiliaryRepositories(long exerciseId) {
        final Set<ProgrammingExerciseRepository.ProgrammingExerciseFetchOptions> fetchOptions = Set.of(AuxiliaryRepositories);
        return programmingExerciseRepository.findByIdWithDynamicFetchElseThrow(exerciseId, fetchOptions);
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
     * Find a programming exercise by its id, with eagerly loaded template and solution participation,
     * including their latest submission with the latest result with feedback and test cases.
     * <p>
     * NOTICE: this method is quite expensive because it loads all feedback and test cases,
     * IMPORTANT: you should generally avoid using this query except you really need all information!!
     *
     * @param programmingExerciseId of the programming exercise.
     * @return The programming exercise related to the given id
     * @throws EntityNotFoundException the programming exercise could not be found.
     */
    @NotNull
    public ProgrammingExercise findByIdWithTemplateAndSolutionParticipationAndAuxiliaryReposAndLatestResultFeedbackTestCasesElseThrow(long programmingExerciseId)
            throws EntityNotFoundException {
        ProgrammingExercise programmingExerciseWithTemplate = programmingExerciseRepository.findWithTemplateParticipationAndLatestSubmissionByIdElseThrow(programmingExerciseId);
        // if there are no submissions we can neither access a submission nor does it make sense to load a result
        if (!programmingExerciseWithTemplate.getTemplateParticipation().getSubmissions().isEmpty()) {
            Optional<Result> latestResultForLatestSubmissionOfTemplate = resultRepository
                    .findLatestResultWithFeedbacksAndTestcasesForSubmission(programmingExerciseWithTemplate.getTemplateParticipation().getSubmissions().iterator().next().getId());
            List<Result> resultsForLatestSubmissionTemplate = new ArrayList<>();
            latestResultForLatestSubmissionOfTemplate.ifPresent(resultsForLatestSubmissionTemplate::add);
            programmingExerciseWithTemplate.getTemplateParticipation().getSubmissions().iterator().next().setResults(resultsForLatestSubmissionTemplate);
        }
        SolutionProgrammingExerciseParticipation solutionParticipationWithLatestSubmission = solutionProgrammingExerciseParticipationRepository
                .findWithLatestSubmissionByExerciseIdElseThrow(programmingExerciseId);

        if (!solutionParticipationWithLatestSubmission.getSubmissions().isEmpty()) {
            Optional<Result> latestResultForLatestSubmissionOfSolution = resultRepository
                    .findLatestResultWithFeedbacksAndTestcasesForSubmission(solutionParticipationWithLatestSubmission.getSubmissions().iterator().next().getId());
            List<Result> resultsForLatestSubmissionSolution = new ArrayList<>();
            latestResultForLatestSubmissionOfSolution.ifPresent(resultsForLatestSubmissionSolution::add);
            solutionParticipationWithLatestSubmission.getSubmissions().iterator().next().setResults(resultsForLatestSubmissionSolution);
        }
        List<AuxiliaryRepository> auxiliaryRepositories = auxiliaryRepositoryRepository.findByProgrammingExerciseId(programmingExerciseId);

        programmingExerciseWithTemplate.setSolutionParticipation(solutionParticipationWithLatestSubmission);
        programmingExerciseWithTemplate.setAuxiliaryRepositories(auxiliaryRepositories);

        return programmingExerciseWithTemplate;
    }

    /**
     * Retrieves all programming exercises for a given course, including their categories, template and solution participations with their latest submissions and results.
     * This method avoids one big and expensive query by splitting the retrieval into multiple smaller queries.
     *
     * @param courseId the course the returned programming exercises belong to.
     * @return all exercises for the given course with only the latest result and latest submission for solution and template each (if present).
     */
    public List<ProgrammingExercise> findByCourseIdWithCategoriesLatestSubmissionResultForTemplateAndSolutionParticipation(long courseId) {
        List<ProgrammingExercise> programmingExercisesWithCategories = programmingExerciseRepository.findAllWithCategoriesByCourseId(courseId);
        if (programmingExercisesWithCategories.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> exerciseIds = programmingExercisesWithCategories.stream().map(ProgrammingExercise::getId).collect(Collectors.toSet());

        Set<SolutionProgrammingExerciseParticipation> solutionParticipationsWithLatestSubmission = solutionProgrammingExerciseParticipationRepository
                .findAllWithLatestSubmissionByExerciseIds(exerciseIds);
        Set<TemplateProgrammingExerciseParticipation> templateParticipationsWithLatestSubmission = templateProgrammingExerciseParticipationRepository
                .findAllWithLatestSubmissionByExerciseIds(exerciseIds);

        Set<Long> solutionSubmissionIds = solutionParticipationsWithLatestSubmission.stream().flatMap(p -> p.getSubmissions().stream().map(DomainObject::getId))
                .collect(Collectors.toSet());
        Set<Long> templateSubmissionIds = templateParticipationsWithLatestSubmission.stream().flatMap(p -> p.getSubmissions().stream().map(DomainObject::getId))
                .collect(Collectors.toSet());

        Map<Long, Result> latestResultsForSolutionSubmissions = resultRepository.findLatestResultsBySubmissionIds(solutionSubmissionIds).stream()
                .collect(Collectors.toMap(result -> result.getSubmission().getId(), result -> result, (r1, r2) -> r1)); // In case of multiple, take first

        Map<Long, Result> latestResultsForTemplateSubmissions = resultRepository.findLatestResultsBySubmissionIds(templateSubmissionIds).stream()
                .collect(Collectors.toMap(result -> result.getSubmission().getId(), result -> result, (r1, r2) -> r1));

        Map<Long, SolutionProgrammingExerciseParticipation> solutionParticipationMap = solutionParticipationsWithLatestSubmission.stream()
                .collect(Collectors.toMap(p -> p.getProgrammingExercise().getId(), p -> p));

        Map<Long, TemplateProgrammingExerciseParticipation> templateParticipationMap = templateParticipationsWithLatestSubmission.stream()
                .collect(Collectors.toMap(p -> p.getProgrammingExercise().getId(), p -> p));

        for (ProgrammingExercise programmingExercise : programmingExercisesWithCategories) {
            TemplateProgrammingExerciseParticipation templateParticipation = templateParticipationMap.get(programmingExercise.getId());
            if (templateParticipation != null) {
                programmingExercise.setTemplateParticipation(templateParticipation);
                connectSubmissionAndResult(latestResultsForTemplateSubmissions, templateParticipation.getSubmissions());
            }
            SolutionProgrammingExerciseParticipation solutionParticipation = solutionParticipationMap.get(programmingExercise.getId());
            if (solutionParticipation != null) {
                programmingExercise.setSolutionParticipation(solutionParticipation);
                connectSubmissionAndResult(latestResultsForSolutionSubmissions, solutionParticipation.getSubmissions());
            }
        }
        return programmingExercisesWithCategories;
    }

    private void connectSubmissionAndResult(Map<Long, Result> latestResultsForSolutionSubmissions, Set<Submission> submissions) {
        if (submissions != null && !submissions.isEmpty()) {
            Submission submission = submissions.iterator().next();
            Result res = latestResultsForSolutionSubmissions.get(submission.getId());
            if (res != null) {
                submission.setResults(Collections.singletonList(res));
            }
        }
    }
}
