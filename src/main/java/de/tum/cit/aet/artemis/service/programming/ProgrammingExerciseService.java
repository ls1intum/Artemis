package de.tum.cit.aet.artemis.service.programming;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.domain.enumeration.BuildPlanType.SOLUTION;
import static de.tum.cit.aet.artemis.domain.enumeration.BuildPlanType.TEMPLATE;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.domain.Repository;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.domain.enumeration.InitializationState;
import de.tum.cit.aet.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.cit.aet.artemis.domain.enumeration.ProjectType;
import de.tum.cit.aet.artemis.domain.enumeration.RepositoryType;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseGitDiffReportRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.cit.aet.artemis.programming.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.service.ExerciseService;
import de.tum.cit.aet.artemis.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.service.ParticipationService;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.service.SubmissionPolicyService;
import de.tum.cit.aet.artemis.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.service.connectors.BuildScriptGenerationService;
import de.tum.cit.aet.artemis.service.connectors.GitService;
import de.tum.cit.aet.artemis.service.connectors.aeolus.AeolusTemplateService;
import de.tum.cit.aet.artemis.service.connectors.aeolus.Windfile;
import de.tum.cit.aet.artemis.service.connectors.ci.CIPermission;
import de.tum.cit.aet.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.service.connectors.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.service.connectors.vcs.VersionControlService;
import de.tum.cit.aet.artemis.service.hestia.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.service.iris.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.service.metis.conversation.ChannelService;
import de.tum.cit.aet.artemis.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.service.util.structureoraclegenerator.OracleGenerator;
import de.tum.cit.aet.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.cit.aet.artemis.web.rest.util.PageUtil;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseService {

    /**
     * Java package name Regex according to Java 14 JLS
     * (<a href="https://docs.oracle.com/javase/specs/jls/se14/html/jls-7.html#jls-7.4.1">https://docs.oracle.com/javase/specs/jls/se14/html/jls-7.html#jls-7.4.1</a>)
     * with the restriction to a-z,A-Z,_ as "Java letter" and 0-9 as digits due to JavaScript/Browser Unicode character class limitations
     */
    private static final String PACKAGE_NAME_REGEX = "^(?!.*(?:\\.|^)(?:abstract|continue|for|new|switch|assert|default|if|package|synchronized|boolean|do|goto|private|this|break|double|implements|protected|throw|byte|else|import|public|throws|case|enum|instanceof|return|transient|catch|extends|int|short|try|char|final|interface|static|void|class|finally|long|strictfp|volatile|const|float|native|super|while|_|true|false|null)(?:\\.|$))[A-Z_a-z]\\w*(?:\\.[A-Z_a-z]\\w*)*$";

    /**
     * Swift package name Regex derived from
     * (<a href="https://docs.swift.org/swift-book/ReferenceManual/LexicalStructure.html#ID412">https://docs.swift.org/swift-book/ReferenceManual/LexicalStructure.html#ID412</a>),
     * with the restriction to a-z,A-Z as "Swift letter" and 0-9 as digits where no separators are allowed
     */
    private static final String SWIFT_PACKAGE_NAME_REGEX = "^(?!(?:associatedtype|class|deinit|enum|extension|fileprivate|func|import|init|inout|internal|let|open|operator|private|protocol|public|rethrows|static|struct|subscript|typealias|var|break|case|continue|default|defer|do|else|fallthrough|for|guard|if|in|repeat|return|switch|where|while|as|Any|catch|false|is|nil|super|self|Self|throw|throws|true|try|_|[sS]wift)$)[A-Za-z][\\dA-Za-z]*$";

    private final Pattern packageNamePattern = Pattern.compile(PACKAGE_NAME_REGEX);

    private final Pattern packageNamePatternForSwift = Pattern.compile(SWIFT_PACKAGE_NAME_REGEX);

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final GitService gitService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    private final ParticipationRepository participationRepository;

    private final ParticipationService participationService;

    private final UserRepository userRepository;

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ResultRepository resultRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository;

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final AuxiliaryRepositoryService auxiliaryRepositoryService;

    private final SubmissionPolicyService submissionPolicyService;

    private final Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService;

    private final ChannelService channelService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final Optional<IrisSettingsService> irisSettingsService;

    private final Optional<AeolusTemplateService> aeolusTemplateService;

    private final Optional<BuildScriptGenerationService> buildScriptGenerationService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProfileService profileService;

    private final ExerciseService exerciseService;

    private final CompetencyProgressService competencyProgressService;

    public ProgrammingExerciseService(ProgrammingExerciseRepository programmingExerciseRepository, GitService gitService, Optional<VersionControlService> versionControlService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ParticipationService participationService,
            ParticipationRepository participationRepository, ResultRepository resultRepository, UserRepository userRepository,
            GroupNotificationScheduleService groupNotificationScheduleService, InstanceMessageSendService instanceMessageSendService,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, ProgrammingExerciseTaskRepository programmingExerciseTaskRepository,
            ProgrammingExerciseSolutionEntryRepository programmingExerciseSolutionEntryRepository, ProgrammingExerciseTaskService programmingExerciseTaskService,
            ProgrammingExerciseGitDiffReportRepository programmingExerciseGitDiffReportRepository, ExerciseSpecificationService exerciseSpecificationService,
            ProgrammingExerciseRepositoryService programmingExerciseRepositoryService, AuxiliaryRepositoryService auxiliaryRepositoryService,
            SubmissionPolicyService submissionPolicyService, Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService, ChannelService channelService,
            ProgrammingSubmissionService programmingSubmissionService, Optional<IrisSettingsService> irisSettingsService, Optional<AeolusTemplateService> aeolusTemplateService,
            Optional<BuildScriptGenerationService> buildScriptGenerationService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProfileService profileService, ExerciseService exerciseService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, CompetencyProgressService competencyProgressService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gitService = gitService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.participationRepository = participationRepository;
        this.participationService = participationService;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.groupNotificationScheduleService = groupNotificationScheduleService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.programmingExerciseSolutionEntryRepository = programmingExerciseSolutionEntryRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseGitDiffReportRepository = programmingExerciseGitDiffReportRepository;
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.auxiliaryRepositoryService = auxiliaryRepositoryService;
        this.submissionPolicyService = submissionPolicyService;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.channelService = channelService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.irisSettingsService = irisSettingsService;
        this.aeolusTemplateService = aeolusTemplateService;
        this.buildScriptGenerationService = buildScriptGenerationService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.profileService = profileService;
        this.exerciseService = exerciseService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.competencyProgressService = competencyProgressService;
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
     * @param isImportedFromFile  defines if the programming exercise is imported from a file
     * @return The new setup exercise
     * @throws GitAPIException If something during the communication with the remote Git repository went wrong
     * @throws IOException     If the template files couldn't be read
     */
    public ProgrammingExercise createProgrammingExercise(ProgrammingExercise programmingExercise, boolean isImportedFromFile) throws GitAPIException, IOException {
        final User exerciseCreator = userRepository.getUser();
        VersionControlService versionControl = versionControlService.orElseThrow();

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
        var savedProgrammingExercise = programmingExerciseRepository.saveForCreation(programmingExercise);
        savedProgrammingExercise.getBuildConfig().setProgrammingExercise(savedProgrammingExercise);
        programmingExerciseBuildConfigRepository.save(savedProgrammingExercise.getBuildConfig());
        // Step 1: Setting constant facts for a programming exercise
        savedProgrammingExercise.generateAndSetProjectKey();
        savedProgrammingExercise.getBuildConfig().setBranch(versionControl.getDefaultBranchOfArtemis());

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
        setupBuildPlansForNewExercise(savedProgrammingExercise, isImportedFromFile);
        savedProgrammingExercise = programmingExerciseRepository.findForCreationByIdElseThrow(savedProgrammingExercise.getId());

        // Step 11: Update task from problem statement
        programmingExerciseTaskService.updateTasksFromProblemStatement(savedProgrammingExercise);

        // Step 12: Webhooks and scheduling
        // Step 12a: Create web hooks for version control
        versionControl.addWebHooksForExercise(savedProgrammingExercise);
        // Step 12b: Schedule operations
        scheduleOperations(savedProgrammingExercise.getId());
        // Step 12c: Check notifications for new exercise
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(savedProgrammingExercise);
        // Step 12d: Update student competency progress
        competencyProgressService.updateProgressByLearningObjectAsync(savedProgrammingExercise);

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

        // Check if testwise coverage analysis is enabled
        if (Boolean.TRUE.equals(buildConfig.isTestwiseCoverageEnabled()) && !programmingLanguageFeature.testwiseCoverageAnalysisSupported()) {
            throw new BadRequestAlertException("Testwise coverage analysis is not supported for this language", "Exercise", "testwiseCoverageAnalysisNotSupported");
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
        Matcher packageNameMatcher;
        switch (programmingExercise.getProgrammingLanguage()) {
            case JAVA, KOTLIN -> packageNameMatcher = packageNamePattern.matcher(programmingExercise.getPackageName());
            case SWIFT -> packageNameMatcher = packageNamePatternForSwift.matcher(programmingExercise.getPackageName());
            default -> throw new IllegalArgumentException("Programming language not supported");
        }
        if (!packageNameMatcher.matches()) {
            throw new BadRequestAlertException("The package name is invalid", "Exercise", "packagenameInvalid");
        }
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
     * Creates build plans for a new programming exercise.
     * 1. Create the project for the exercise on the CI Server
     * 2. Create template and solution build plan in this project
     * 3. Configure CI permissions
     * 4. Trigger initial build for template and solution build plan (if the exercise is not imported)
     *
     * @param programmingExercise Programming exercise for the build plans should be generated. The programming
     *                                exercise should contain a fully initialized template and solution participation.
     * @param isImportedFromFile  defines if the programming exercise is imported from a file, if the
     *                                exercise is imported, the build plans will not be triggered to prevent erroneous builds
     */
    public void setupBuildPlansForNewExercise(ProgrammingExercise programmingExercise, boolean isImportedFromFile) throws JsonProcessingException {
        String projectKey = programmingExercise.getProjectKey();
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

        // Give appropriate permissions for CI projects
        continuousIntegration.removeAllDefaultProjectPermissions(projectKey);

        giveCIProjectPermissions(programmingExercise);

        Windfile windfile = programmingExercise.getBuildConfig().getWindfile();
        if (windfile != null && buildScriptGenerationService.isPresent() && programmingExercise.getBuildConfig().getBuildScript() == null) {
            String script = buildScriptGenerationService.get().getScript(programmingExercise);
            programmingExercise.getBuildConfig().setBuildPlanConfiguration(new ObjectMapper().writeValueAsString(windfile));
            programmingExercise.getBuildConfig().setBuildScript(script);
            programmingExerciseBuildConfigRepository.saveAndFlush(programmingExercise.getBuildConfig());
        }

        // if the exercise is imported from a file, the changes fixing the project name will trigger a first build anyway, so
        // we do not trigger them here
        if (!isImportedFromFile) {
            // trigger BASE and SOLUTION build plans once here
            continuousIntegrationTriggerService.orElseThrow().triggerBuild(programmingExercise.getTemplateParticipation());
            continuousIntegrationTriggerService.orElseThrow().triggerBuild(programmingExercise.getSolutionParticipation());
        }
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
        ProgrammingExercise savedProgrammingExercise = programmingExerciseRepository.save(updatedProgrammingExercise);
        // The returned value should use test case names since it gets send back to the client
        savedProgrammingExercise.setProblemStatement(problemStatementWithTestNames);

        participationRepository.removeIndividualDueDatesIfBeforeDueDate(savedProgrammingExercise, programmingExerciseBeforeUpdate.getDueDate());
        programmingExerciseTaskService.updateTasksFromProblemStatement(savedProgrammingExercise);

        if (programmingExerciseBeforeUpdate.isCourseExercise()) {
            scheduleOperations(updatedProgrammingExercise.getId());
        }

        exerciseService.notifyAboutExerciseChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise, notificationText);

        competencyProgressService.updateProgressForUpdatedLearningObjectAsync(programmingExerciseBeforeUpdate, Optional.of(updatedProgrammingExercise));

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
            if (!profileService.isLocalCiActive()) {
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

        irisSettingsService.ifPresent(iss -> iss.deleteSettingsFor(programmingExercise));

        SolutionProgrammingExerciseParticipation solutionProgrammingExerciseParticipation = programmingExercise.getSolutionParticipation();
        TemplateProgrammingExerciseParticipation templateProgrammingExerciseParticipation = programmingExercise.getTemplateParticipation();
        if (solutionProgrammingExerciseParticipation != null) {
            participationService.deleteResultsAndSubmissionsOfParticipation(solutionProgrammingExerciseParticipation.getId(), true);
        }
        if (templateProgrammingExerciseParticipation != null) {
            participationService.deleteResultsAndSubmissionsOfParticipation(templateProgrammingExerciseParticipation.getId(), true);
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
        return resultRepository.existsByParticipation_ExerciseId(programmingExercise.getId());
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
     * add project permissions to project of the build plans of the given exercise
     *
     * @param exercise the exercise whose build plans projects should be configured with permissions
     */
    public void giveCIProjectPermissions(ProgrammingExercise exercise) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();

        final var editorGroup = course.getEditorGroupName();
        final var teachingAssistantGroup = course.getTeachingAssistantGroupName();

        List<String> adminGroups = new ArrayList<>();
        adminGroups.add(course.getInstructorGroupName());
        if (StringUtils.isNotEmpty(editorGroup)) {
            adminGroups.add(editorGroup);
        }

        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        continuousIntegration.giveProjectPermissions(exercise.getProjectKey(), adminGroups,
                List.of(CIPermission.CREATE, CIPermission.READ, CIPermission.CREATEREPOSITORY, CIPermission.ADMIN));
        if (teachingAssistantGroup != null) {
            continuousIntegration.giveProjectPermissions(exercise.getProjectKey(), List.of(teachingAssistantGroup), List.of(CIPermission.READ));
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
        String projectKey = courseShortName + programmingExercise.getShortName().toUpperCase().replaceAll("\\s+", "");
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
    public void deleteTasksWithSolutionEntries(Long exerciseId) {
        Set<ProgrammingExerciseTask> tasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCaseAndSolutionEntriesElseThrow(exerciseId);
        Set<ProgrammingExerciseSolutionEntry> solutionEntries = tasks.stream().map(ProgrammingExerciseTask::getTestCases).flatMap(Collection::stream)
                .map(ProgrammingExerciseTestCase::getSolutionEntries).flatMap(Collection::stream).collect(Collectors.toSet());
        programmingExerciseTaskRepository.deleteAll(tasks);
        programmingExerciseSolutionEntryRepository.deleteAll(solutionEntries);
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
}
