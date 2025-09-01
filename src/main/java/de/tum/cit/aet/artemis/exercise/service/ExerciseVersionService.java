package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseSnapshot;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCServletService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

@Profile(PROFILE_CORE)
@Service
@Aspect
public class ExerciseVersionService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionService.class);

    private final ExerciseVersionRepository exerciseVersionRepository;

    private final GitService gitService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    public ExerciseVersionService(ExerciseVersionRepository exerciseVersionRepository, GitService gitService, ProgrammingExerciseRepository programmingExerciseRepository,
            QuizExerciseRepository quizExerciseRepository, TextExerciseRepository textExerciseRepository, ModelingExerciseRepository modelingExerciseRepository,
            FileUploadExerciseRepository fileUploadExerciseRepository) {
        this.exerciseVersionRepository = exerciseVersionRepository;
        this.gitService = gitService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
    }

    /**
     * Creates a version of an exercise, capturing a complete snapshot of the exercise
     * with all its associated data. The version is created by fetching the exercise
     * with all data eagerly loaded, and then creating a new ExerciseVersion object
     * with the fetched exercise as a snapshot.
     * <p>
     * This method is also explicitly called from {@link LocalVCServletService#processNewPush}
     *
     * @param exercise The exercise to create a version of
     * @param author   The user who created the version
     */
    @Async
    public void createExerciseVersion(Exercise exercise, User author) {
        try {
            Exercise eagerlyFetchedExercise = fetchExerciseEagerly(exercise);
            if (eagerlyFetchedExercise == null) {
                log.warn("Could not fetch exercise with id {} for versioning", exercise.getId());
                return;
            }
            ExerciseVersion exerciseVersion = new ExerciseVersion();
            exerciseVersion.setExercise(eagerlyFetchedExercise);
            exerciseVersion.setAuthor(author);

            var exerciseSnapshot = ExerciseSnapshot.of(eagerlyFetchedExercise, gitService);
            var previousVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(eagerlyFetchedExercise.getId());
            if (previousVersion.isPresent()) {
                var previousVersionSnapshot = previousVersion.get().getExerciseSnapshot();
                var equal = previousVersionSnapshot.equals(exerciseSnapshot);
                if (equal) {
                    log.info("Skipping version creation for exercise {} ({}) as it is already up to date", eagerlyFetchedExercise.getId(), eagerlyFetchedExercise.getTitle());
                    return;
                }
            }
            exerciseVersion.setExerciseSnapshot(exerciseSnapshot);
            ;
            exerciseVersionRepository.saveAndFlush(exerciseVersion);
        }
        catch (Exception e) {
            log.error("Error creating exercise version for exercise {} with id {}: {}", exercise.getTitle(), exercise.getId(), e.getMessage(), e);
        }
    }

    private Exercise fetchExerciseEagerly(Exercise exercise) {
        if (exercise == null || exercise.getId() == null) {
            return exercise;
        }
        var exerciseId = exercise.getId();

        return switch (exercise) {
            case ProgrammingExercise programmingExercise:
                yield programmingExerciseRepository.findWithEagerForVersioningById(exerciseId).orElse(null);
            case QuizExercise quizExercise:
                yield quizExerciseRepository.findWithEagerForVersioningById(exerciseId).orElse(null);
            case TextExercise textExercise:
                yield textExerciseRepository.findWithEagerForVersioningById(exerciseId).orElse(null);
            case ModelingExercise modelingExercise:
                yield modelingExerciseRepository.findWithEagerForVersioningById(exerciseId).orElse(null);
            case FileUploadExercise fileUploadExercise:
                yield fileUploadExerciseRepository.findWithEagerForVersioningById(exerciseId).orElse(null);
            default:
                log.error("Could not fetch exercise with id {} for versioning", exercise.getId());
                yield exercise;
        };
    }

}
