package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import jakarta.annotation.Nullable;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersionMetadata;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
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

    /**
     * Enumeration of entity types that trigger exercise versioning when modified.
     * Each type represents a different kind of exercise-related entity that, when saved or deleted,
     * should create a new version of the associated exercise to track changes over time.
     * Not all entity updates are listened to, because:
     * a. some entities does not have their own repository, they are saved along with the exercise (e.g. GradingCriteria, ProgrammingExerciseTestCase)
     * b. some entities are only initialized once when exercise is created (e.g. SolutionParticipation, TemplateParticipation)
     * c. some entities are not relevant for exercise versioning (e.g. Submission, Feedback)
     */
    public enum ExerciseVersionHookType {

        COMPETENCY_LINK("Competency link"), EXAMPLE_SUBMISSION("Example submission"), AUXILIARY_REPOSITORY("Auxiliary repository"),
        STATIC_CODE_ANALYSIS_CATEGORY("Static code analysis category"), SUBMISSION_POLICY("Submission policy"),
        PROGRAMMING_EXERCISE_BUILD_CONFIG("Programming exercise build config");

        private final String displayName;

        ExerciseVersionHookType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionService.class);

    private final ExerciseVersionRepository exerciseVersionRepository;

    private final UserRepository userRepository;

    private final GitService gitService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    public ExerciseVersionService(ExerciseVersionRepository exerciseVersionRepository, UserRepository userRepository, GitService gitService,
            ProgrammingExerciseRepository programmingExerciseRepository, QuizExerciseRepository quizExerciseRepository, TextExerciseRepository textExerciseRepository,
            ModelingExerciseRepository modelingExerciseRepository, FileUploadExerciseRepository fileUploadExerciseRepository) {
        this.exerciseVersionRepository = exerciseVersionRepository;
        this.userRepository = userRepository;
        this.gitService = gitService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
    }

    @AfterReturning(value = "execution( * de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository.save*(..))", returning = "result")
    public void onCompetencyLinkSaveReturn(Object result) {
        handleEntitySaveReturn(result, CompetencyExerciseLink.class, CompetencyExerciseLink::getExercise, ExerciseVersionHookType.COMPETENCY_LINK);
    }

    @AfterReturning(value = "execution(* de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository.delete*(..))")
    public void onCompetencyLinkDeleteReturn(JoinPoint joinPoint) {
        handleEntityDeleteReturn(joinPoint, CompetencyExerciseLink.class, CompetencyExerciseLink::getExercise, ExerciseVersionHookType.COMPETENCY_LINK);
    }

    @AfterReturning(value = "execution( * de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository.save*(..))", returning = "result")
    public void onExampleSubmissionSaveReturn(Object result) {
        handleEntitySaveReturn(result, ExampleSubmission.class, ExampleSubmission::getExercise, ExerciseVersionHookType.EXAMPLE_SUBMISSION);
    }

    @AfterReturning(value = "execution(* de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository.delete*(..))")
    public void onExampleSubmissionDeleteReturn(JoinPoint joinPoint) {
        handleEntityDeleteReturn(joinPoint, ExampleSubmission.class, ExampleSubmission::getExercise, ExerciseVersionHookType.EXAMPLE_SUBMISSION);
    }

    @AfterReturning(value = "execution( * de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository.save*(..))", returning = "result")
    public void onAuxiliaryRepositorySaveReturn(Object result) {
        handleEntitySaveReturn(result, AuxiliaryRepository.class, AuxiliaryRepository::getExercise, ExerciseVersionHookType.AUXILIARY_REPOSITORY);
    }

    @AfterReturning(value = "execution(* de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository.delete*(..))")
    public void onAuxiliaryRepositoryDeleteReturn(JoinPoint joinPoint) {
        handleEntityDeleteReturn(joinPoint, AuxiliaryRepository.class, AuxiliaryRepository::getExercise, ExerciseVersionHookType.AUXILIARY_REPOSITORY);
    }

    @AfterReturning(value = "execution( * de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository.save*(..))", returning = "result")
    public void onStaticCodeAnalysisCategorySaveReturn(Object result) {
        handleEntitySaveReturn(result, StaticCodeAnalysisCategory.class, StaticCodeAnalysisCategory::getExercise, ExerciseVersionHookType.STATIC_CODE_ANALYSIS_CATEGORY);
    }

    @AfterReturning(value = "execution(* de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository.delete*(..))")
    public void onStaticCodeAnalysisCategoryDeleteReturn(JoinPoint joinPoint) {
        handleEntityDeleteReturn(joinPoint, StaticCodeAnalysisCategory.class, StaticCodeAnalysisCategory::getExercise, ExerciseVersionHookType.STATIC_CODE_ANALYSIS_CATEGORY);
    }

    @AfterReturning(value = "execution( * de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository.save*(..))", returning = "result")
    public void onSubmissionPolicySaveReturn(Object result) {
        handleEntitySaveReturn(result, SubmissionPolicy.class, SubmissionPolicy::getProgrammingExercise, ExerciseVersionHookType.SUBMISSION_POLICY);
    }

    @AfterReturning(value = "execution(* de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository.delete*(..))")
    public void onSubmissionPolicyDeleteReturn(JoinPoint joinPoint) {
        handleEntityDeleteReturn(joinPoint, SubmissionPolicy.class, SubmissionPolicy::getProgrammingExercise, ExerciseVersionHookType.SUBMISSION_POLICY);
    }

    @AfterReturning(value = "execution( * de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository.save*(..))", returning = "result")
    public void onProgrammingExerciseBuildConfigSaveReturn(Object result) {
        handleEntitySaveReturn(result, ProgrammingExerciseBuildConfig.class, ProgrammingExerciseBuildConfig::getProgrammingExercise,
                ExerciseVersionHookType.PROGRAMMING_EXERCISE_BUILD_CONFIG);
    }

    @AfterReturning(value = "execution(* de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository.delete*(..))")
    public void onProgrammingExerciseBuildConfigDeleteReturn(JoinPoint joinPoint) {
        handleEntityDeleteReturn(joinPoint, ProgrammingExerciseBuildConfig.class, ProgrammingExerciseBuildConfig::getProgrammingExercise,
                ExerciseVersionHookType.PROGRAMMING_EXERCISE_BUILD_CONFIG);
    }

    @AfterReturning(value = "execution( * de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository.save*(..)) ||"
            + "execution( * de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository.save*(..)) ||"
            + "execution( * de.tum.cit.aet.artemis.text.repository.TextExerciseRepository.save*(..)) ||"
            + "execution( * de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository.save*(..)) ||"
            + "execution( * de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository.save*(..)) ||"
            + "execution( * de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository.save*(..))", returning = "result")
    public void onExerciseSaveReturn(JoinPoint joinPoint, Object result) {
        User user = userRepository.getUser();
        if (result instanceof Exercise exercise) {
            createExerciseVersion(exercise, user);
        }
        else if (result instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Exercise exercise) {
                    createExerciseVersion(exercise, user);
                }
            }
        }
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
    public void createExerciseVersion(Exercise exercise, User author) {
        try {
            // Fetch exercise with all data eagerly loaded for complete versioning
            Exercise eagerlyFetchedExercise = fetchExerciseEagerly(exercise);
            if (eagerlyFetchedExercise == null) {
                log.warn("Could not fetch exercise with id {} for versioning", exercise.getId());
                return;
            }

            // Create version with complete exercise snapshot
            ExerciseVersion exerciseVersion = new ExerciseVersion();
            exerciseVersion.setExercise(exercise);
            exerciseVersion.setMetadata(createExerciseVersionMetadata(exercise));
            exerciseVersion.setExerciseSnapshot(eagerlyFetchedExercise);
            exerciseVersion.setAuthor(author);

            // TODO: if the exercise is already a version, we should compare with previous version and skip if unchanged
            exerciseVersionRepository.save(exerciseVersion);

            log.info("User {} created exercise version for {} {} with id {}, version id {}", author.getLogin(), exercise.getClass().getSimpleName(), exercise.getTitle(),
                    exercise.getId(), exerciseVersion.getId());
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

    @Nullable
    private <T> Set<Exercise> getUniqueExercisesFromEntityUpdate(Object object, Class<T> entityClass, Function<T, Exercise> exerciseExtractor) {
        Set<Exercise> uniqueExercises = new HashSet<>();

        if (entityClass.isInstance(object)) {
            T entity = entityClass.cast(object);
            Exercise exercise = exerciseExtractor.apply(entity);
            if (exercise != null) {
                uniqueExercises.add(exercise);
            }
        }
        else if (object instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (entityClass.isInstance(item)) {
                    T entity = entityClass.cast(item);
                    Exercise exercise = exerciseExtractor.apply(entity);
                    if (exercise != null) {
                        uniqueExercises.add(exercise);
                    }
                }
            }
        }
        else {
            return null;
        }
        return uniqueExercises;
    }

    /**
     * Generic handler for save operations that return entities with exercise relationships
     */
    private <T> void handleEntitySaveReturn(Object result, Class<T> entityClass, Function<T, Exercise> exerciseExtractor, ExerciseVersionHookType entityType) {
        User user = userRepository.getUser();
        Set<Exercise> uniqueExercises = getUniqueExercisesFromEntityUpdate(result, entityClass, exerciseExtractor);

        if (uniqueExercises == null) {
            log.warn("Exercise Version Service: Could not get unique exercises for {} update", entityType.getDisplayName());
            return;
        }

        // Create versions for unique exercises only
        for (Exercise exercise : uniqueExercises) {
            createExerciseVersion(exercise, user);
        }
    }

    /**
     * Generic handler for delete operations that process method arguments
     */
    private <T> void handleEntityDeleteReturn(JoinPoint joinPoint, Class<T> entityClass, Function<T, Exercise> exerciseExtractor, ExerciseVersionHookType entityType) {
        User user = userRepository.getUser();
        Set<Exercise> uniqueExercises = null;

        for (Object item : joinPoint.getArgs()) {
            if (entityClass.isInstance(item) || item instanceof Collection<?>) {
                uniqueExercises = getUniqueExercisesFromEntityUpdate(item, entityClass, exerciseExtractor);
                break;
            }
        }

        if (uniqueExercises == null) {
            log.warn("Exercise Version Service: Could not get unique exercises for {} deletion", entityType.getDisplayName());
            return;
        }

        // Create versions for unique exercises only
        for (Exercise exercise : uniqueExercises) {
            createExerciseVersion(exercise, user);
        }
    }

    private ExerciseVersionMetadata createExerciseVersionMetadata(Exercise exercise) {
        if (Objects.requireNonNull(exercise) instanceof ProgrammingExercise programmingExercise) {
            return createProgrammingExerciseVersionMetadata(programmingExercise);
        }
        return ExerciseVersionMetadata.builder().build();
    }

    private ExerciseVersionMetadata createProgrammingExerciseVersionMetadata(ProgrammingExercise programmingExercise) {

        var builder = ExerciseVersionMetadata.builder();
        if (programmingExercise.getTemplateParticipation() != null && programmingExercise.getTemplateParticipation().getVcsRepositoryUri() != null) {
            var templateCommitHash = gitService.getLastCommitHash(programmingExercise.getTemplateParticipation().getVcsRepositoryUri());
            if (templateCommitHash != null) {
                builder.templateCommitId(templateCommitHash.getName());
            }
        }

        if (programmingExercise.getSolutionParticipation() != null && programmingExercise.getSolutionParticipation().getVcsRepositoryUri() != null) {
            var solutionCommitHash = gitService.getLastCommitHash(programmingExercise.getSolutionParticipation().getVcsRepositoryUri());
            if (solutionCommitHash != null) {
                builder.solutionCommitId(solutionCommitHash.getName());
            }
        }

        if (programmingExercise.getTestRepositoryUri() != null) {
            var testCommitHash = gitService.getLastCommitHash(programmingExercise.getVcsTestRepositoryUri());
            if (testCommitHash != null) {
                builder.testsCommitId(testCommitHash.getName());
            }
        }

        if (programmingExercise.getAuxiliaryRepositories() != null) {
            var auxiliaryCommitIds = new HashMap<String, String>();
            for (AuxiliaryRepository auxiliaryRepository : programmingExercise.getAuxiliaryRepositories()) {
                var auxiliaryCommitHash = gitService.getLastCommitHash(auxiliaryRepository.getVcsRepositoryUri());
                if (auxiliaryCommitHash != null) {
                    auxiliaryCommitIds.put(auxiliaryRepository.getName(), auxiliaryCommitHash.getName());
                }
            }
            builder.auxiliaryCommitIds(auxiliaryCommitIds);
        }
        return builder.build();
    }

}
