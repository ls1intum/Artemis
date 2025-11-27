package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SynchronizationTarget;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseSynchronizationService;
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

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final UserRepository userRepository;

    private final ProgrammingExerciseSynchronizationService programmingExerciseSynchronizationService;

    public ExerciseVersionService(ExerciseVersionRepository exerciseVersionRepository, GitService gitService, ProgrammingExerciseRepository programmingExerciseRepository,
            QuizExerciseRepository quizExerciseRepository, TextExerciseRepository textExerciseRepository, ModelingExerciseRepository modelingExerciseRepository,
            FileUploadExerciseRepository fileUploadExerciseRepository, UserRepository userRepository,
            ProgrammingExerciseSynchronizationService programmingExerciseSynchronizationService) {
        this.exerciseVersionRepository = exerciseVersionRepository;
        this.gitService = gitService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.userRepository = userRepository;
        this.programmingExerciseSynchronizationService = programmingExerciseSynchronizationService;
    }

    public boolean isRepositoryTypeVersionable(RepositoryType repositoryType) {
        return REPO_TYPES_TRIGGERING_EXERCISE_VERSIONING.contains(repositoryType);
    }

    /**
     * Creates an exercise version. This function would fetch the exercise eagerly
     * that corresponds to its type, and use the currently logged in user from {@link de.tum.cit.aet.artemis.core.security.SecurityUtils}
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
            this.determineSynchronizationForActiveEditors(exercise.getId(), exerciseSnapshot, previousVersion.map(ExerciseVersion::getExerciseSnapshot).orElse(null));
            log.info("Exercise version {} has been created for exercise {}", savedExerciseVersion.getId(), exercise.getId());
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
        return switch (exerciseType) {
            case PROGRAMMING -> programmingExerciseRepository.findForVersioningById(exercise.getId()).orElse(null);
            case QUIZ -> quizExerciseRepository.findForVersioningById(exercise.getId()).orElse(null);
            case TEXT -> textExerciseRepository.findForVersioningById(exercise.getId()).orElse(null);
            case MODELING -> modelingExerciseRepository.findForVersioningById(exercise.getId()).orElse(null);
            case FILE_UPLOAD -> fileUploadExerciseRepository.findForVersioningById(exercise.getId()).orElse(null);
        };
    }

    /**
     * Compare two exercise snapshots and return the change that should be broadcast to clients.
     *
     * @param exerciseId       the exercise id
     * @param newSnapshot      the new snapshot
     * @param previousSnapshot the previous snapshot (optional)
     */
    private void determineSynchronizationForActiveEditors(Long exerciseId, ExerciseSnapshotDTO newSnapshot, ExerciseSnapshotDTO previousSnapshot) {
        if (previousSnapshot == null || newSnapshot.programmingData() == null || previousSnapshot.programmingData() == null) {
            return;
        }

        var newProgrammingData = newSnapshot.programmingData();
        var previousProgrammingData = previousSnapshot.programmingData();
        SynchronizationTarget target = null;
        Long auxiliaryRepositoryId = null;

        if (commitIdChanged(previousProgrammingData.templateParticipation(), newProgrammingData.templateParticipation())) {
            target = SynchronizationTarget.TEMPLATE_REPOSITORY;
        }
        else if (commitIdChanged(previousProgrammingData.solutionParticipation(), newProgrammingData.solutionParticipation())) {
            target = SynchronizationTarget.SOLUTION_REPOSITORY;
        }
        else if (!Objects.equals(previousProgrammingData.testsCommitId(), newProgrammingData.testsCommitId())) {
            target = SynchronizationTarget.TESTS_REPOSITORY;
        }
        else {
            var previousAuxiliaries = Optional.ofNullable(previousProgrammingData.auxiliaryRepositories()).orElseGet(List::of).stream().collect(
                    Collectors.toMap(ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO::id, ProgrammingExerciseSnapshotDTO.AuxiliaryRepositorySnapshotDTO::commitId));
            for (var auxiliary : Optional.ofNullable(newProgrammingData.auxiliaryRepositories()).orElseGet(List::of)) {
                var previousCommitId = previousAuxiliaries.get(auxiliary.id());
                if (!Objects.equals(previousCommitId, auxiliary.commitId())) {
                    target = SynchronizationTarget.AUXILIARY_REPOSITORY;
                    auxiliaryRepositoryId = auxiliary.id();
                    break;
                }
            }
        }

        if (target == null && !Objects.equals(previousSnapshot.problemStatement(), newSnapshot.problemStatement())) {
            target = SynchronizationTarget.PROBLEM_STATEMENT;
        }

        if (target != null) {
            programmingExerciseSynchronizationService.broadcastChange(exerciseId, target, auxiliaryRepositoryId);
        }
    }

    private boolean commitIdChanged(ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO previousParticipation,
            ProgrammingExerciseSnapshotDTO.ParticipationSnapshotDTO newParticipation) {
        if (previousParticipation == null && newParticipation == null) {
            return false;
        }
        var previousCommitId = previousParticipation == null ? null : previousParticipation.commitId();
        var newCommitId = newParticipation == null ? null : newParticipation.commitId();
        return !Objects.equals(previousCommitId, newCommitId);
    }
}
