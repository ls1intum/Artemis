package de.tum.cit.aet.artemis.versioning.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.versioning.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.versioning.dto.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.versioning.event.ExerciseChangedEvent;
import de.tum.cit.aet.artemis.versioning.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.versioning.repository.FileUploadExerciseVersioningRepository;
import de.tum.cit.aet.artemis.versioning.repository.TextExerciseVersioningRepository;

@Profile(PROFILE_CORE)
@Service
@Lazy
public class ExerciseVersionService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionService.class);

    private final ExerciseVersionRepository exerciseVersionRepository;

    private final GitService gitService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final TextExerciseVersioningRepository textExerciseRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final FileUploadExerciseVersioningRepository fileUploadExerciseRepository;

    private final UserRepository userRepository;

    public ExerciseVersionService(ExerciseVersionRepository exerciseVersionRepository, GitService gitService, ProgrammingExerciseRepository programmingExerciseRepository,
            QuizExerciseRepository quizExerciseRepository, TextExerciseVersioningRepository textExerciseRepository, ModelingExerciseRepository modelingExerciseRepository,
            FileUploadExerciseVersioningRepository fileUploadExerciseRepository, UserRepository userRepository) {
        this.exerciseVersionRepository = exerciseVersionRepository;
        this.gitService = gitService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.userRepository = userRepository;
    }

    /**
     * Handle ExerciseChangedEvent after the surrounding transaction commits.
     *
     * @param event The ExerciseChangedEvent to handle, containing the exercise ID
     *                  and the user login. userLogin is used to avoid attempting to
     *                  fetch from database before transaction commits.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    public void onExerciseChangedEvent(ExerciseChangedEvent event) {
        createExerciseVersion(event.exerciseId(), event.exerciseType(), event.userLogin());
    }

    /**
     * Creates an exercise version. This function would fetch the exercise eagerly
     * that corresponds to its type,
     * initialize an {@link ExerciseSnapshotDTO} and create a new
     * {@link ExerciseVersion} to persist.
     *
     * @param exerciseId   The ID of the exercise to create a version of
     * @param exerciseType the {@link ExerciseType} of the exercise
     * @param userLogin    The user login of who created the version
     */
    private void createExerciseVersion(Long exerciseId, ExerciseType exerciseType, String userLogin) {
        try {
            User author = userRepository.findOneByLogin(userLogin).orElse(null);
            if (author == null) {
                log.error("No active user during exercise version creation check");
                return;
            }
            Exercise exercise = fetchExerciseEagerly(exerciseId, exerciseType);
            if (exercise == null) {
                log.error("Exercise with id {} not found", exerciseId);
                return;
            }
            ExerciseVersion exerciseVersion = new ExerciseVersion();
            exerciseVersion.setExercise(exercise);
            exerciseVersion.setAuthor(author);
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
        }
        catch (Exception e) {
            log.error("Error creating exercise version for exercise with id {}: {}", exerciseId, e.getMessage(), e);
        }
    }

    /**
     * Fetches an exercise eagerly with versioned fields, with the correct exercise
     * type.
     *
     * @param exerciseId   the exercise id to fetch from
     * @param exerciseType the {@link ExerciseType} to fetch from
     * @return the exercise with the given id of the specific subclass, fetched
     *         eagerly with versioned fields,
     *         or null if the exercise does not exist
     */
    private Exercise fetchExerciseEagerly(Long exerciseId, ExerciseType exerciseType) {
        if (exerciseId == null) {
            log.error("fetchExerciseEagerly for versioning is called with null");
            return null;
        }
        return switch (exerciseType) {
            case PROGRAMMING -> programmingExerciseRepository.findWithEagerForVersioningById(exerciseId).orElse(null);
            case QUIZ -> quizExerciseRepository.findWithEagerForVersioningById(exerciseId).orElse(null);
            case TEXT -> textExerciseRepository.findWithEagerForVersioningById(exerciseId).orElse(null);
            case MODELING -> modelingExerciseRepository.findWithEagerForVersioningById(exerciseId).orElse(null);
            case FILE_UPLOAD -> fileUploadExerciseRepository.findWithEagerForVersioningById(exerciseId).orElse(null);
        };
    }
}
