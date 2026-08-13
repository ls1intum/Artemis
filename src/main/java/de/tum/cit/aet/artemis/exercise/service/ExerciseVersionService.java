package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.event.ExerciseVersionCreatedEvent;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentThreadDTO;
import de.tum.cit.aet.artemis.exercise.dto.review.ReviewThreadSyncDTO;
import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseEditorSyncTarget;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewVersionChangeService;
import de.tum.cit.aet.artemis.fileupload.api.FileUploadApi;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.modeling.api.ModelingRepositoryApi;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.text.api.TextRepositoryApi;

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

    private final Optional<TextRepositoryApi> textRepositoryApi;

    private final Optional<ModelingRepositoryApi> modelingRepositoryApi;

    private final Optional<FileUploadApi> fileUploadApi;

    private final UserRepository userRepository;

    private final ExerciseEditorSyncService exerciseEditorSyncService;

    private final ChannelRepository channelRepository;

    private final ExerciseReviewVersionChangeService exerciseReviewVersionChangeService;

    private final ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper;

    // Executor for versioning work. In production it delegates to the shared async pool so exercise updates do not
    // block on versioning; under the test profile it is synchronous, keeping versioning-triggering tests deterministic.
    private final Executor exerciseVersionExecutor;

    public ExerciseVersionService(ExerciseVersionRepository exerciseVersionRepository, GitService gitService, ProgrammingExerciseRepository programmingExerciseRepository,
            QuizExerciseRepository quizExerciseRepository, Optional<TextRepositoryApi> textRepositoryApi, Optional<ModelingRepositoryApi> modelingRepositoryApi,
            Optional<FileUploadApi> fileUploadApi, UserRepository userRepository, ExerciseEditorSyncService exerciseEditorSyncService, ChannelRepository channelRepository,
            ExerciseReviewVersionChangeService exerciseReviewVersionChangeService, ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper,
            @Qualifier("exerciseVersionTaskExecutor") Executor exerciseVersionExecutor) {
        this.exerciseVersionRepository = exerciseVersionRepository;
        this.gitService = gitService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.textRepositoryApi = textRepositoryApi;
        this.modelingRepositoryApi = modelingRepositoryApi;
        this.fileUploadApi = fileUploadApi;
        this.userRepository = userRepository;
        this.exerciseEditorSyncService = exerciseEditorSyncService;
        this.channelRepository = channelRepository;
        this.exerciseReviewVersionChangeService = exerciseReviewVersionChangeService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.exerciseVersionExecutor = exerciseVersionExecutor;
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
        // Resolve the current user on the request thread: the async executor thread has no SecurityContext.
        User user = userRepository.getUser();
        createExerciseVersion(targetExercise, user);
    }

    /**
     * Requests the (asynchronous) creation of an exercise version. This schedules the actual work on the
     * {@code exerciseVersionExecutor} and returns immediately, so exercise updates do not block the end user while
     * versioning executes (which may involve slower-than-usual queries and git access). Under the {@code test} profile
     * the executor is synchronous, so versioning still completes before the calling test continues.
     *
     * @param targetExercise The exercise to create a version of
     * @param author         The user who created the version
     */
    public void createExerciseVersion(Exercise targetExercise, User author) {
        createExerciseVersion(targetExercise, author, null, null, null);
    }

    /**
     * Requests the (asynchronous) creation of an exercise version for a repository commit, identifying that commit.
     * <p>
     * The repository and the commit together are what let the resulting new commit alert be attributed to the client that made
     * the commit, so that client can filter its own alert out instead of being warned about its own submit. Callers that do
     * not create a commit pass nothing and no alert is ever attributed to them.
     *
     * @param targetExercise                  The exercise to create a version of
     * @param author                          The user who created the version
     * @param triggeringRepositoryType        The repository this request committed to, or null when it committed to none
     * @param triggeringAuxiliaryRepositoryId The id of that repository when it is an auxiliary one, null otherwise
     * @param triggeringCommitHash            The commit this request created, or null when the request created no commit
     */
    public void createExerciseVersion(Exercise targetExercise, User author, @Nullable RepositoryType triggeringRepositoryType, @Nullable Long triggeringAuxiliaryRepositoryId,
            @Nullable String triggeringCommitHash) {
        // Read on the calling thread for the same reason the author is: the client session id lives in the request, and the
        // executor thread has no request context.
        String clientSessionId = ExerciseEditorSyncService.getClientSessionId();
        ExerciseEditorSyncTarget triggeringTarget = attributableTarget(triggeringRepositoryType);
        exerciseVersionExecutor
                .execute(() -> createExerciseVersionInternal(targetExercise, author, clientSessionId, triggeringTarget, triggeringAuxiliaryRepositoryId, triggeringCommitHash));
    }

    /**
     * Maps a committed repository to the synchronization target an alert about it would carry, for the repositories an alert
     * can be attributed to.
     * <p>
     * An auxiliary repository maps to the auxiliary target; which of the exercise's auxiliary repositories it is has to be
     * settled separately, by the id, because one target covers all of them.
     */
    @Nullable
    private static ExerciseEditorSyncTarget attributableTarget(@Nullable RepositoryType repositoryType) {
        if (repositoryType == null) {
            return null;
        }
        return switch (repositoryType) {
            case TEMPLATE -> ExerciseEditorSyncTarget.TEMPLATE_REPOSITORY;
            case SOLUTION -> ExerciseEditorSyncTarget.SOLUTION_REPOSITORY;
            case TESTS -> ExerciseEditorSyncTarget.TESTS_REPOSITORY;
            case AUXILIARY -> ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY;
            // a student repository has no editor to warn and never produces a version
            case USER -> null;
        };
    }

    /**
     * Creates an exercise version: fetches the exercise eagerly for its type, initializes an {@link ExerciseSnapshotDTO}
     * and persists a new {@link ExerciseVersion}. Runs on the {@code exerciseVersionExecutor} thread.
     *
     * @param targetExercise                  The exercise to create a version of
     * @param author                          The user who created the version
     * @param clientSessionId                 the session of the client that triggered this version, or null when no request did
     * @param triggeringTarget                the repository that client committed to, or null when it committed to none
     * @param triggeringAuxiliaryRepositoryId the id of that repository when it is an auxiliary one, null otherwise
     * @param triggeringCommitHash            the commit that client created, or null when it created none
     */
    private void createExerciseVersionInternal(Exercise targetExercise, User author, @Nullable String clientSessionId, @Nullable ExerciseEditorSyncTarget triggeringTarget,
            @Nullable Long triggeringAuxiliaryRepositoryId, @Nullable String triggeringCommitHash) {
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
            var programmingCommitHashes = ExerciseVersionCommitHashResolver.resolveForExercise(exercise, gitService);
            ExerciseSnapshotDTO rawSnapshot = ExerciseSnapshotDTO.of(exercise, programmingCommitHashes);
            // Normalize through JSON round-trip to ensure consistent null/empty list handling
            // (@JsonInclude(NON_EMPTY) causes empty lists to become null after deserialization)
            ExerciseSnapshotDTO exerciseSnapshot = objectMapper.readValue(objectMapper.writeValueAsString(rawSnapshot), ExerciseSnapshotDTO.class);
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
                    savedExerciseVersion.getId(), clientSessionId, triggeringTarget, triggeringAuxiliaryRepositoryId, triggeringCommitHash);
            log.info("Exercise version {} has been created for exercise {}", savedExerciseVersion.getId(), exercise.getId());
            previousVersion.ifPresent(prev -> {
                try {
                    List<CommentThreadDTO> updatedThreads = exerciseReviewVersionChangeService.updateThreadsForVersionChange(prev.getExerciseSnapshot(), exerciseSnapshot).stream()
                            .filter(thread -> thread.getId() != null).map(thread -> new CommentThreadDTO(thread, List.of())).toList();
                    for (CommentThreadDTO updatedThread : updatedThreads) {
                        exerciseEditorSyncService.broadcastReviewThreadUpdate(exercise.getId(), ReviewThreadSyncDTO.threadUpdated(updatedThread));
                    }
                }
                catch (Exception ex) {
                    log.warn("Could not update review threads for version {}: {}", savedExerciseVersion.getId(), ex.getMessage());
                }
            });
            // Publish event to notify listeners (e.g., search indexing services)
            eventPublisher.publishEvent(new ExerciseVersionCreatedEvent(exercise));
        }
        catch (Exception e) {
            // Intentionally swallowed: exercise version creation is a non-critical side effect
            // of saving an exercise. Failures here (e.g. serialization issues, DB errors) must
            // not prevent the exercise save itself from succeeding.
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
    @Nullable
    private Exercise fetchExerciseEagerly(Exercise exercise) {
        if (exercise == null || exercise.getId() == null) {
            log.error("fetchExerciseEagerly for versioning is called with null");
            return null;
        }
        ExerciseType exerciseType = exercise.getExerciseType();
        Exercise fetched = switch (exerciseType) {
            case PROGRAMMING -> programmingExerciseRepository.findForVersioningById(exercise.getId()).orElse(null);
            case QUIZ -> quizExerciseRepository.findForVersioningById(exercise.getId()).orElse(null);
            case TEXT -> textRepositoryApi.flatMap(api -> api.findForVersioningById(exercise.getId())).orElse(null);
            case MODELING -> modelingRepositoryApi.flatMap(api -> api.findForVersioningById(exercise.getId())).orElse(null);
            case FILE_UPLOAD -> fileUploadApi.flatMap(api -> api.findForVersioningById(exercise.getId())).orElse(null);
        };
        if (fetched != null) {
            Channel channel = channelRepository.findChannelByExerciseId(fetched.getId());
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
     * @param clientSessionId      the session of the client that triggered this version, so its own editor can filter the
     *                                 alert out again, or null when no request triggered it
     * @param triggeringTarget     the repository that client committed to, checked against the repository this alert is
     *                                 about before attributing it
     * @param triggeringCommitHash the commit that client created, used to check that the alert really is about its own
     *                                 commit before attributing it
     */
    private void determineSynchronizationForActiveEditors(Long exerciseId, ExerciseSnapshotDTO newSnapshot, ExerciseSnapshotDTO previousSnapshot, User author,
            Long newExerciseVersionId, @Nullable String clientSessionId, @Nullable ExerciseEditorSyncTarget triggeringTarget, @Nullable Long triggeringAuxiliaryRepositoryId,
            @Nullable String triggeringCommitHash) {
        if (previousSnapshot == null || newSnapshot == null) {
            return;
        }

        ProgrammingExerciseSnapshotDTO newProgrammingData = newSnapshot.programmingData();
        ProgrammingExerciseSnapshotDTO previousProgrammingData = previousSnapshot.programmingData();
        ExerciseEditorSyncTarget target = null;
        Long auxiliaryRepositoryId = null;
        // The commit the detected repository now stands at, needed to decide whose commit this alert is about
        String changedCommitId = null;

        // Repository commits cannot change simultaneously because each commit triggers a separate
        // version creation. The if-else chain intentionally detects only the first changed repository.
        if (newProgrammingData != null && previousProgrammingData != null) {
            if (participationCommitChanged(previousProgrammingData.templateParticipation(), newProgrammingData.templateParticipation())) {
                target = ExerciseEditorSyncTarget.TEMPLATE_REPOSITORY;
                changedCommitId = participationCommitId(newProgrammingData.templateParticipation());
            }
            else if (participationCommitChanged(previousProgrammingData.solutionParticipation(), newProgrammingData.solutionParticipation())) {
                target = ExerciseEditorSyncTarget.SOLUTION_REPOSITORY;
                changedCommitId = participationCommitId(newProgrammingData.solutionParticipation());
            }
            else if (!Objects.equals(previousProgrammingData.testsCommitId(), newProgrammingData.testsCommitId())) {
                target = ExerciseEditorSyncTarget.TESTS_REPOSITORY;
                changedCommitId = newProgrammingData.testsCommitId();
            }
            else {
                Map<Long, String> previousAuxiliaries = Optional.ofNullable(previousProgrammingData.auxiliaryRepositories()).orElseGet(List::of).stream()
                        .filter(auxiliary -> auxiliary.commitId() != null).collect(Collectors.toMap(ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO::id,
                                ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO::commitId));
                for (ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO auxiliary : Optional.ofNullable(newProgrammingData.auxiliaryRepositories())
                        .orElseGet(List::of)) {
                    String previousCommitId = previousAuxiliaries.get(auxiliary.id());
                    if (!Objects.equals(previousCommitId, auxiliary.commitId())) {
                        target = ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY;
                        auxiliaryRepositoryId = auxiliary.id();
                        changedCommitId = auxiliary.commitId();
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
            exerciseEditorSyncService.broadcastNewCommitAlert(exerciseId, target, auxiliaryRepositoryId,
                    sessionOwningCommit(clientSessionId, triggeringTarget, triggeringAuxiliaryRepositoryId, triggeringCommitHash, target, auxiliaryRepositoryId, changedCommitId));
        }
        if (!changedFields.isEmpty()) {
            exerciseEditorSyncService.broadcastNewExerciseVersionAlert(exerciseId, newExerciseVersionId, author, changedFields);
        }
    }

    /**
     * Collects the set of changed exercise fields between two snapshots.
     * <p>
     * IMPORTANT: When adding new fields to {@link ExerciseSnapshotDTO}, a corresponding
     * {@code addIfChanged} call must be added here so that metadata sync can detect the change.
     * {@code ExerciseVersionServiceTest.testCollectChangedFieldsCoversAllExerciseSnapshotFields}
     * will fail if a new field is not accounted for.
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
        addIfChanged(changedFields, "includedInOverallScore", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::includedInOverallScore);
        // problemStatement is excluded: changes are broadcast via Yjs client-to-client synchronization, not metadata sync.
        addIfChanged(changedFields, "gradingInstructions", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::gradingInstructions);
        addIfChanged(changedFields, "categories", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::categories);
        addIfChanged(changedFields, "teamAssignmentConfig", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::teamAssignmentConfig);
        addIfChanged(changedFields, "presentationScoreEnabled", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::presentationScoreEnabled);
        addIfChanged(changedFields, "secondCorrectionEnabled", newSnapshot, previousSnapshot, ExerciseSnapshotDTO::secondCorrectionEnabled);
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
        // Uses the full DTO including commitId and repositoryUri. Git commits may trigger a
        // false metadata change detection here, but this is harmless: the client-side handler
        // compares only the editable fields (name, checkoutDirectory, description) so no
        // conflict will be raised in the UI.
        addIfChanged(changedFields, "programmingData.auxiliaryRepositories", newData, previousData, ProgrammingExerciseSnapshotDTO::auxiliaryRepositories);
        addIfChanged(changedFields, "programmingData.buildAndTestStudentSubmissionsAfterDueDate", newData, previousData,
                ProgrammingExerciseSnapshotDTO::buildAndTestStudentSubmissionsAfterDueDate);
        addIfChanged(changedFields, "programmingData.releaseTestsWithExampleSolution", newData, previousData, ProgrammingExerciseSnapshotDTO::releaseTestsWithExampleSolution);
        addIfChanged(changedFields, "programmingData.buildConfig", newData, previousData, ProgrammingExerciseSnapshotDTO::buildConfig);
    }

    /**
     * Adds the field identifier to the set if the values differ.
     *
     * @param <T>              the snapshot type
     * @param <V>              the field value type
     * @param changedFields    the set to update
     * @param field            the field identifier
     * @param newSnapshot      the new snapshot
     * @param previousSnapshot the previous snapshot
     * @param fieldAccessor    extracts the field value from a snapshot
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
    /**
     * The session an alert may be attributed to, which is only the session whose own commit the alert describes.
     * <p>
     * Version jobs run asynchronously on several workers and read the repository refs when they execute, not when they were
     * queued. So a job queued by one client can snapshot a commit another client pushed in the meantime. Attributing that
     * alert to the queueing client would make its editor filter out a warning about somebody else's commit, and a missing
     * warning is worse than the duplicate warning this attribution exists to remove. Whenever the identity cannot be
     * established the alert goes out unattributed, which warns everyone, including the committer.
     *
     * The repository has to match as well as the commit. A commit id identifies an object, not a place: repositories of one
     * exercise are seeded from each other, so the same commit legitimately exists in more than one of them, and an empty
     * commit made in two of them by the same author in the same second is byte-identical and therefore has the same id.
     * Matching on the id alone would let an alert about one repository be attributed to a client that committed to another.
     *
     * For an auxiliary repository the id has to match as well, because one target covers every auxiliary repository of the
     * exercise. An auxiliary commit whose id could not be resolved stays unattributed rather than matching all of them.
     *
     * @param clientSessionId                 the session that queued this version, or null if no request did
     * @param triggeringTarget                the repository that session committed to, or null if it committed to none
     * @param triggeringAuxiliaryRepositoryId the id of that repository when it is an auxiliary one, null otherwise
     * @param triggeringCommitHash            the commit that session created, or null if it created none
     * @param alertedTarget                   the repository this alert is about
     * @param alertedAuxiliaryRepositoryId    the auxiliary repository this alert is about, null for the other targets
     * @param changedCommitId                 the commit the alerted repository now stands at
     * @return the session to attribute the alert to, or null to attribute it to nobody
     */
    @Nullable
    static String sessionOwningCommit(@Nullable String clientSessionId, @Nullable ExerciseEditorSyncTarget triggeringTarget, @Nullable Long triggeringAuxiliaryRepositoryId,
            @Nullable String triggeringCommitHash, @Nullable ExerciseEditorSyncTarget alertedTarget, @Nullable Long alertedAuxiliaryRepositoryId,
            @Nullable String changedCommitId) {
        if (clientSessionId == null || triggeringTarget == null || triggeringCommitHash == null || alertedTarget == null || changedCommitId == null) {
            return null;
        }
        if (triggeringTarget != alertedTarget || !triggeringCommitHash.equals(changedCommitId)) {
            return null;
        }
        if (alertedTarget == ExerciseEditorSyncTarget.AUXILIARY_REPOSITORY
                && (triggeringAuxiliaryRepositoryId == null || !triggeringAuxiliaryRepositoryId.equals(alertedAuxiliaryRepositoryId))) {
            return null;
        }
        return clientSessionId;
    }

    @Nullable
    private static String participationCommitId(ProgrammingExerciseSnapshotDTO.@Nullable ParticipationSnapshotDTO participation) {
        return participation == null ? null : participation.commitId();
    }

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
