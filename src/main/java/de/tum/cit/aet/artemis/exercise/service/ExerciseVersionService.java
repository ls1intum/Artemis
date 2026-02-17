package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.synchronization.ExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewService;
import de.tum.cit.aet.artemis.fileupload.api.FileUploadApi;
import de.tum.cit.aet.artemis.modeling.api.ModelingRepositoryApi;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

@Profile(PROFILE_CORE)
@Service
@Lazy
public class ExerciseVersionService {

    private static final Set<RepositoryType> REPO_TYPES_TRIGGERING_EXERCISE_VERSIONING = EnumSet.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS,
            RepositoryType.AUXILIARY);

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionService.class);

    private final ExerciseVersionRepository exerciseVersionRepository;

    private final GitService gitService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final Optional<ModelingRepositoryApi> modelingRepositoryApi;

    private final Optional<FileUploadApi> fileUploadApi;

    private final UserRepository userRepository;

    private final ExerciseEditorSyncService exerciseEditorSyncService;

    private final ChannelRepository channelRepository;

    private final ExerciseReviewService exerciseReviewService;

    public ExerciseVersionService(ExerciseVersionRepository exerciseVersionRepository, GitService gitService, ProgrammingExerciseRepository programmingExerciseRepository,
            QuizExerciseRepository quizExerciseRepository, TextExerciseRepository textExerciseRepository, Optional<ModelingRepositoryApi> modelingRepositoryApi,
            Optional<FileUploadApi> fileUploadApi, UserRepository userRepository, ExerciseEditorSyncService exerciseEditorSyncService, ChannelRepository channelRepository,
            ExerciseReviewService exerciseReviewService) {
        this.exerciseVersionRepository = exerciseVersionRepository;
        this.gitService = gitService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.modelingRepositoryApi = modelingRepositoryApi;
        this.fileUploadApi = fileUploadApi;
        this.userRepository = userRepository;
        this.exerciseEditorSyncService = exerciseEditorSyncService;
        this.channelRepository = channelRepository;
        this.exerciseReviewService = exerciseReviewService;
    }

    /**
     * Determines whether a repository type triggers exercise versioning.
     *
     * @param repositoryType the repository type
     * @return true if the repository type is versionable
     */
    public boolean isRepositoryTypeVersionable(RepositoryType repositoryType) {
        return REPO_TYPES_TRIGGERING_EXERCISE_VERSIONING.contains(repositoryType);
    }

    /**
     * Creates an exercise version. This function would fetch the exercise eagerly
     * that corresponds to its type, and use the currently logged in user from
     * {@link de.tum.cit.aet.artemis.core.security.SecurityUtils}
     * initialize an {@link ExerciseSnapshotDTO} and create a new
     * {@link ExerciseVersion} to persist.
     *
     * @param targetExercise The exercise to create a version of
     */
    public void createExerciseVersion(Exercise targetExercise) {
        User user = userRepository.getUser();
        createExerciseVersion(targetExercise, user);
    }

    /**
     * Creates an exercise version. This function would fetch the exercise eagerly
     * that corresponds to its type,
     * initialize an {@link ExerciseSnapshotDTO} and create a new
     * {@link ExerciseVersion} to persist.
     *
     * @param targetExercise The exercise to create a version of
     * @param author         The user who created the version
     */
    public void createExerciseVersion(Exercise targetExercise, User author) {
        if (author == null) {
            log.error("No active user during exercise version creation check");
            return;
        }
        if (targetExercise == null || targetExercise.getId() == null) {
            log.error("createExerciseVersion called with null");
            return;
        }
        try {
            Exercise exercise = fetchExerciseEagerly(targetExercise);
            if (exercise == null) {
                log.error("Exercise with id {} not found", targetExercise.getId());
                return;
            }
            ExerciseVersion exerciseVersion = new ExerciseVersion();
            exerciseVersion.setExerciseId(targetExercise.getId());
            exerciseVersion.setAuthorId(author.getId());
            ExerciseSnapshotDTO exerciseSnapshot = ExerciseSnapshotDTO.of(exercise, gitService);
            Optional<ExerciseVersion> previousVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId());
            if (previousVersion.isPresent()) {
                ExerciseSnapshotDTO previousVersionSnapshot = previousVersion.get().getExerciseSnapshot();
                boolean equal = previousVersionSnapshot.equals(exerciseSnapshot);
                if (equal) {
                    log.info("Exercise {} has no versionable changes from last version", exercise.getId());
                    return;
                }
            }
            exerciseVersion.setExerciseSnapshot(exerciseSnapshot);
            ExerciseVersion savedExerciseVersion = exerciseVersionRepository.save(exerciseVersion);
            this.determineSynchronizationForActiveEditors(exercise.getId(), exerciseSnapshot, previousVersion.map(ExerciseVersion::getExerciseSnapshot).orElse(null), author,
                    savedExerciseVersion.getId());
            log.info("Exercise version {} has been created for exercise {}", savedExerciseVersion.getId(), exercise.getId());
            previousVersion.ifPresent(prev -> {
                try {
                    exerciseReviewService.updateThreadsForVersionChange(prev.getExerciseSnapshot(), exerciseSnapshot);
                }
                catch (Exception ex) {
                    log.warn("Could not update review threads for version {}: {}", savedExerciseVersion.getId(), ex.getMessage());
                }
            });
        }
        catch (Exception e) {
            log.error("Error creating exercise version for exercise with id {}: {}", targetExercise.getId(), e.getMessage(), e);
        }
    }

    /**
     * Fetches an exercise eagerly with versioned fields, with the correct exercise
     * type.
     *
     * @param exercise the exercise to be eagerly fetched
     * @return the exercise with the given id of the specific subclass, fetched
     *         eagerly with versioned fields,
     *         or null if the exercise does not exist
     */
    private Exercise fetchExerciseEagerly(Exercise exercise) {
        if (exercise == null || exercise.getId() == null) {
            log.error("fetchExerciseEagerly for versioning is called with null");
            return null;
        }
        ExerciseType exerciseType = exercise.getExerciseType();
        Exercise fetched = switch (exerciseType) {
            case PROGRAMMING -> programmingExerciseRepository.findForVersioningById(exercise.getId()).orElse(null);
            case QUIZ -> quizExerciseRepository.findForVersioningById(exercise.getId()).orElse(null);
            case TEXT -> textExerciseRepository.findForVersioningById(exercise.getId()).orElse(null);
            case MODELING -> modelingRepositoryApi.flatMap(api -> api.findForVersioningById(exercise.getId())).orElse(null);
            case FILE_UPLOAD -> fileUploadApi.flatMap(api -> api.findForVersioningById(exercise.getId())).orElse(null);
        };
        if (fetched != null) {
            var channel = channelRepository.findChannelByExerciseId(fetched.getId());
            if (channel != null) {
                fetched.setChannelName(channel.getName());
            }
        }
        return fetched;
    }

    /**
     * Compare two exercise snapshots and broadcast synchronization messages to
     * active editors.
     * For repository commits (template, solution, tests, auxiliary), broadcasts a
     * new commit alert
     * so clients can display a notification prompting users to refresh.
     *
     * @param exerciseId           the exercise id
     * @param newSnapshot          the new snapshot
     * @param previousSnapshot     the previous snapshot (optional)
     * @param author               the author of the new version
     * @param newExerciseVersionId the id of the new exercise version
     */
    private void determineSynchronizationForActiveEditors(Long exerciseId, ExerciseSnapshotDTO newSnapshot, ExerciseSnapshotDTO previousSnapshot, User author,
            Long newExerciseVersionId) {
        if (previousSnapshot == null || newSnapshot == null) {
            return;
        }

        ProgrammingExerciseSnapshotDTO newProgrammingData = newSnapshot.programmingData();
        ProgrammingExerciseSnapshotDTO previousProgrammingData = previousSnapshot.programmingData();
        ExerciseEditorSyncTarget target = null;
        Long auxiliaryRepositoryId = null;

        if (newProgrammingData != null && previousProgrammingData != null) {
            if (participationCommitChanged(previousProgrammingData.templateParticipation(), newProgrammingData.templateParticipation())) {
                target = ExerciseEditorSyncTarget.TEMPLATE_REPOSITORY;
            }
            else if (participationCommitChanged(previousProgrammingData.solutionParticipation(), newProgrammingData.solutionParticipation())) {
                target = ExerciseEditorSyncTarget.SOLUTION_REPOSITORY;
            }
            else if (!Objects.equals(previousProgrammingData.testsCommitId(), newProgrammingData.testsCommitId())) {
                target = ExerciseEditorSyncTarget.TESTS_REPOSITORY;
            }
            else {
                Map<Long, String> previousAuxiliaries = Optional.ofNullable(previousProgrammingData.auxiliaryRepositories()).orElseGet(List::of).stream()
                        .filter(auxiliary -> auxiliary.commitId() != null).collect(Collectors.toMap(ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO::id,
                                ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO::commitId));
                for (ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO auxiliary : Optional.ofNullable(newProgrammingData.auxiliaryRepositories())
                        .orElseGet(List::of)) {
                    var previousCommitId = previousAuxiliaries.get(auxiliary.id());
                    if (!Objects.equals(previousCommitId, auxiliary.commitId())) {
                        target = ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY;
                        auxiliaryRepositoryId = auxiliary.id();
                        break;
                    }
                }
            }
        }

        Set<String> changedFields = collectChangedFields(newSnapshot, previousSnapshot);

        if (target != null) {
            // For repository commits, send a new commit alert so clients can notify users
            // to refresh
            // For problem statement changes, changes are broadcasted via client-to-client
            // messages.
            exerciseEditorSyncService.broadcastNewCommitAlert(exerciseId, target, auxiliaryRepositoryId);
        }
        if (!changedFields.isEmpty()) {
            exerciseEditorSyncService.broadcastNewExerciseVersionAlert(exerciseId, newExerciseVersionId, author, changedFields);
        }
    }

    /**
     * Collects the set of changed exercise fields between two snapshots.
     *
     * @param newSnapshot      the new snapshot
     * @param previousSnapshot the previous snapshot
     * @return the set of changed field identifiers
     */
    private Set<String> collectChangedFields(ExerciseSnapshotDTO newSnapshot, ExerciseSnapshotDTO previousSnapshot) {
        Set<String> changedFields = new HashSet<>();
        addIfChanged(changedFields, "title", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::title);
        addIfChanged(changedFields, "shortName", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::shortName);
        addIfChanged(changedFields, "channelName", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::channelName);
        addIfChanged(changedFields, "competencyLinks", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::competencyLinks);
        addIfChanged(changedFields, "maxPoints", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::maxPoints);
        addIfChanged(changedFields, "bonusPoints", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::bonusPoints);
        addIfChanged(changedFields, "assessmentType", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::assessmentType);
        addIfChanged(changedFields, "releaseDate", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::releaseDate);
        addIfChanged(changedFields, "startDate", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::startDate);
        addIfChanged(changedFields, "dueDate", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::dueDate);
        addIfChanged(changedFields, "assessmentDueDate", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::assessmentDueDate);
        addIfChanged(changedFields, "exampleSolutionPublicationDate", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::exampleSolutionPublicationDate);
        addIfChanged(changedFields, "difficulty", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::difficulty);
        addIfChanged(changedFields, "mode", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::mode);
        addIfChanged(changedFields, "allowComplaintsForAutomaticAssessments", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::allowComplaintsForAutomaticAssessments);
        addIfChanged(changedFields, "allowFeedbackRequests", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::allowFeedbackRequests);
        addIfChanged(changedFields, "includedInOverallScore", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::includedInOverallScore);
        // problemStatement is excluded: changes are broadcast via Yjs client-to-client synchronization, not metadata sync.
        addIfChanged(changedFields, "gradingInstructions", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::gradingInstructions);
        addIfChanged(changedFields, "categories", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::categories);
        addIfChanged(changedFields, "teamAssignmentConfig", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::teamAssignmentConfig);
        addIfChanged(changedFields, "presentationScoreEnabled", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::presentationScoreEnabled);
        addIfChanged(changedFields, "secondCorrectionEnabled", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::secondCorrectionEnabled);
        addIfChanged(changedFields, "feedbackSuggestionModule", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::feedbackSuggestionModule);
        addIfChanged(changedFields, "gradingCriteria", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::gradingCriteria);
        addIfChanged(changedFields, "plagiarismDetectionConfig", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::plagiarismDetectionConfig);

        collectProgrammingChanges(changedFields, newSnapshot.programmingData(), previousSnapshot.programmingData());

        return changedFields;
    }

    /**
     * Collects changed fields for programming exercise snapshot data.
     *
     * @param changedFields the set to update with changed fields
     * @param newData       the new programming snapshot data
     * @param previousData  the previous programming snapshot data
     */
    private void collectProgrammingChanges(Set<String> changedFields, ProgrammingExerciseSnapshotDTO newData, ProgrammingExerciseSnapshotDTO previousData) {
        if (newData == null && previousData == null) {
            return;
        }
        if (newData == null || previousData == null) {
            changedFields.add("programmingData");
            return;
        }
        // Note: repository URLs, submission policy, programming language, project type, package name,
        // static code analysis enablement, and project keys are not editable on the exercise edit page.
        addIfChanged(changedFields, "programmingData.allowOnlineEditor", newData, previousData, ProgrammingExerciseSnapshotDTO::allowOnlineEditor);
        addIfChanged(changedFields, "programmingData.allowOfflineIde", newData, previousData, ProgrammingExerciseSnapshotDTO::allowOfflineIde);
        addIfChanged(changedFields, "programmingData.allowOnlineIde", newData, previousData, ProgrammingExerciseSnapshotDTO::allowOnlineIde);
        addIfChanged(changedFields, "programmingData.maxStaticCodeAnalysisPenalty", newData, previousData, ProgrammingExerciseSnapshotDTO::maxStaticCodeAnalysisPenalty);
        addIfChanged(changedFields, "programmingData.showTestNamesToStudents", newData, previousData, ProgrammingExerciseSnapshotDTO::showTestNamesToStudents);
        addIfChanged(changedFields, "programmingData.auxiliaryRepositories", newData, previousData, this::extractAuxiliaryRepositoryMetadata);
        addIfChanged(changedFields, "programmingData.buildAndTestStudentSubmissionsAfterDueDate", newData, previousData,
                ProgrammingExerciseSnapshotDTO::buildAndTestStudentSubmissionsAfterDueDate);
        addIfChanged(changedFields, "programmingData.releaseTestsWithExampleSolution", newData, previousData, ProgrammingExerciseSnapshotDTO::releaseTestsWithExampleSolution);
        addIfChanged(changedFields, "programmingData.buildConfig", newData, previousData, ProgrammingExerciseSnapshotDTO::buildConfig);
    }

    private List<AuxiliaryRepositoryMetadata> extractAuxiliaryRepositoryMetadata(ProgrammingExerciseSnapshotDTO snapshot) {
        var auxiliaryRepositories = snapshot.auxiliaryRepositories();
        if (auxiliaryRepositories == null) {
            return null;
        }
        return auxiliaryRepositories.stream()
                .map((repository) -> new AuxiliaryRepositoryMetadata(repository.id(), repository.name(), repository.checkoutDirectory(), repository.description()))
                .sorted((left, right) -> Long.compare(left.id(), right.id())).toList();
    }

    private record AuxiliaryRepositoryMetadata(long id, String name, String checkoutDirectory, String description) {
    }

    /**
     * Adds the field identifier to the set if the values differ.
     *
     * @param changedFields the set to update
     * @param field         the field identifier
     * @param newValue      the new value
     * @param previousValue the previous value
     */
    private <T, V> void addIfChanged(Set<String> changedFields, String field, T newSnapshot, T previousSnapshot, Function<T, V> fieldAccessor) {
        if (!Objects.equals(fieldAccessor.apply(newSnapshot), fieldAccessor.apply(previousSnapshot))) {
            changedFields.add(field);
        }
    }

    /**
     * Checks whether the commit id changed for a participation snapshot.
     *
     * @param previousParticipation the previous participation snapshot
     * @param newParticipation      the new participation snapshot
     * @return true if the commit id changed
     */
    private boolean participationCommitChanged(ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO previousParticipation,
            ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO newParticipation) {
        if (previousParticipation == null && newParticipation == null) {
            return false;
        }
        String previousCommitId = previousParticipation == null ? null : previousParticipation.commitId();
        String newCommitId = newParticipation == null ? null : newParticipation.commitId();
        return !Objects.equals(previousCommitId, newCommitId);
    }
}
