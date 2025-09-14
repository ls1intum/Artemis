package de.tum.cit.aet.artemis.versioning.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCServletService;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.versioning.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.versioning.dto.ExerciseSnapshot;
import de.tum.cit.aet.artemis.versioning.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.versioning.repository.FileUploadExerciseVersioningRepository;
import de.tum.cit.aet.artemis.versioning.repository.TextExerciseVersioningRepository;
import de.tum.cit.aet.artemis.versioning.service.event.ExerciseChangedEvent;

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

    private final TransactionTemplate transactionTemplate;

    public ExerciseVersionService(ExerciseVersionRepository exerciseVersionRepository, GitService gitService, ProgrammingExerciseRepository programmingExerciseRepository,
            QuizExerciseRepository quizExerciseRepository, TextExerciseVersioningRepository textExerciseRepository, ModelingExerciseRepository modelingExerciseRepository,
            FileUploadExerciseVersioningRepository fileUploadExerciseRepository, UserRepository userRepository, PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
     * Creates an exercise version asynchronously. This function is used when a new push is received on repositories.
     * It should NOT be called during JPA entity events, as this function requires a transaction context to function
     * correctly. {@link ExerciseChangedEvent} is used for entity change events instead to defer version creation to
     * after the surrounding transaction commits.
     * <p>
     * This method is explicitly called from {@link LocalVCServletService#processNewPush}
     * <p>
     *
     * @param exerciseId The ID of the exercise to create a version of
     * @param author     The user who created the version
     */
    @Async
    public void createProgrammingExerciseVersion(Long exerciseId, User author) {
        createExerciseVersionSafely(exerciseId, author, ExerciseType.PROGRAMMING);
    }

    /**
     * Creates an exercise version safely. This function would fetch the exercise eagerly that corresponds to its type,
     * initialze an {@link ExerciseSnapshot} and create a new {@link ExerciseVersion} to persist.
     *
     * @param exerciseId The ID of the exercise to create a version of
     * @param author     The user who created the version
     */
    private void createExerciseVersionSafely(Long exerciseId, User author, ExerciseType exerciseType) {
        try {
            Exercise exercise = fetchExerciseEagerly(exerciseId, exerciseType);
            // Exercise exercise = exerciseRepository.findById(exerciseId).orElse(null);
            if (exercise == null) {
                return;
            }
            ExerciseVersion exerciseVersion = new ExerciseVersion();
            exerciseVersion.setExercise(exercise);
            exerciseVersion.setAuthor(author);
            var exerciseSnapshot = ExerciseSnapshot.of(exercise, gitService);
            var previousVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId());
            if (previousVersion.isPresent()) {
                var previousVersionSnapshot = previousVersion.get().getExerciseSnapshot();
                var equal = previousVersionSnapshot.equals(exerciseSnapshot);
                if (equal) {
                    log.info("Exercise {} has no versionable changes from last version", exercise.getId());
                    return;
                }
            }
            exerciseVersion.setExerciseSnapshot(exerciseSnapshot);
            log.info("Creating exercise version for exercise with id {}", exerciseId);
            transactionTemplate.executeWithoutResult(status -> {
                var version = exerciseVersionRepository.save(exerciseVersion);
                log.info("Exercise version for exercise with id {} created", version.getId());
            });
        }
        catch (Exception e) {
            log.error("Error creating exercise version for exercise with id {}: {}", exerciseId, e.getMessage(), e);
        }
    }

    /**
     * Handle ExerciseChangedEvent after the surrounding transaction commits.
     * Falls back to immediate execution if no transaction is active when the event is published.
     *
     * @param event The ExerciseChangedEvent to handle, containing the exercise ID and the user login.
     *                  userLogin is used, due to the method being async thus not being able to access
     *                  the user from security context. Also, methods in {link @ExerciseVersionEntityListener}
     *                  should not try to access {@link User} from {@link UserRepository} as we should not try to access
     *                  JPA entities from within JPA entity listeners.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async
    public void onExerciseChangedEvent(ExerciseChangedEvent event) {
        try {
            var user = userRepository.getUserByLoginElseThrow(event.userLogin());
            createExerciseVersionSafely(event.exerciseId(), user, event.exerciseType());
        }
        catch (EntityNotFoundException e) {
            log.warn("No active user during exercise version creation check");
        }
        catch (Exception e) {
            log.error("Error handling ExerciseChangedEvent for exercise {}: {}", event.exerciseId(), e.getMessage(), e);
        }
    }

    /**
     * Fetches an exercise eagerly with versioned fields, with the correct exercise type.
     *
     * @param exerciseId the exercise id to fetch from
     * @return the exercise with the given id of the specific subclass, fetched eagerly with versioned fields,
     *         or null if the exercise does not exist
     */
    public Exercise fetchExerciseEagerly(Long exerciseId, ExerciseType exerciseType) {
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
