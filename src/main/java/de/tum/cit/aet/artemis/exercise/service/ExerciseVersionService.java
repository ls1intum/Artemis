package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

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
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.service.review.ExerciseReviewService;
import de.tum.cit.aet.artemis.fileupload.api.FileUploadApi;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entitySchemas.ExerciseSchema;
import de.tum.cit.aet.artemis.globalsearch.service.ExerciseWeaviateService;
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

    private final ExerciseReviewService exerciseReviewService;

    private final Optional<ExerciseWeaviateService> exerciseWeaviateService;

    public ExerciseVersionService(ExerciseVersionRepository exerciseVersionRepository, GitService gitService, ProgrammingExerciseRepository programmingExerciseRepository,
            QuizExerciseRepository quizExerciseRepository, TextExerciseRepository textExerciseRepository, Optional<ModelingRepositoryApi> modelingRepositoryApi,
            Optional<FileUploadApi> fileUploadApi, UserRepository userRepository, ExerciseReviewService exerciseReviewService,
            Optional<ExerciseWeaviateService> exerciseWeaviateService) {
        this.exerciseVersionRepository = exerciseVersionRepository;
        this.gitService = gitService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.modelingRepositoryApi = modelingRepositoryApi;
        this.fileUploadApi = fileUploadApi;
        this.userRepository = userRepository;
        this.exerciseReviewService = exerciseReviewService;
        this.exerciseWeaviateService = exerciseWeaviateService;
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
     * Creates an exercise version and inserts the exercise into Weaviate (if enabled).
     * Use this for newly created or imported exercises.
     *
     * @param exercise The newly created or imported exercise
     */
    public void createExerciseVersionAndInsertInWeaviate(Exercise exercise) {
        createExerciseVersion(exercise);
        exerciseWeaviateService.ifPresent(weaviateService -> weaviateService.insertExerciseAsync(exercise));
    }

    /**
     * Creates an exercise version and inserts the exercise into Weaviate (if enabled).
     * Use this for newly created or imported exercises.
     *
     * @param exercise The newly created or imported exercise
     * @param author   The user who created the version
     */
    public void createExerciseVersionAndInsertInWeaviate(Exercise exercise, User author) {
        createExerciseVersion(exercise, author);
        exerciseWeaviateService.ifPresent(weaviateService -> weaviateService.insertExerciseAsync(exercise));
    }

    /**
     * Creates an exercise version and updates the exercise in Weaviate (if enabled).
     * Use this for exercises whose indexed metadata {@link ExerciseSchema} has been modified.
     *
     * @param exercise The updated exercise
     */
    public void createExerciseVersionAndUpdateInWeaviate(Exercise exercise) {
        createExerciseVersion(exercise);
        exerciseWeaviateService.ifPresent(weaviateService -> weaviateService.updateExerciseAsync(exercise));
    }

    /**
     * Creates an exercise version and updates the exercise in Weaviate (if enabled).
     * Use this for exercises whose indexed metadata {@link ExerciseSchema} has been modified.
     *
     * @param exercise The updated exercise
     * @param author   The user who created the version
     */
    public void createExerciseVersionAndUpdateInWeaviate(Exercise exercise, User author) {
        createExerciseVersion(exercise, author);
        exerciseWeaviateService.ifPresent(weaviateService -> weaviateService.updateExerciseAsync(exercise));
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
            case MODELING -> modelingRepositoryApi.flatMap(api -> api.findForVersioningById(exercise.getId())).orElse(null);
            case FILE_UPLOAD -> fileUploadApi.flatMap(api -> api.findForVersioningById(exercise.getId())).orElse(null);
        };
    }
}
