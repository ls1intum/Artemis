package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersionContent;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseVersionService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionService.class);

    private final ExerciseVersionRepository exerciseVersionRepository;

    private final GitService gitService;

    public ExerciseVersionService(ExerciseVersionRepository exerciseVersionRepository, GitService gitService) {
        this.exerciseVersionRepository = exerciseVersionRepository;
        this.gitService = gitService;
    }

    /**
     * Creates a new ExerciseVersion for the given exercise and saves it to the database.
     * The content field of the ExerciseVersion contains a structured ExerciseVersionContent object with the following fields:
     * - template_commit_id
     * - solution_commit_id
     * - tests_commit_id
     * - short_name
     * - title
     * - problem_statement
     * - start_date
     * - release_date
     * - due_date
     * - duration
     * - is_open_for_practice
     * - allowed_number_of_attempts
     * - randomize_question_order
     * - max_points
     * - difficulty
     * - bonus_points
     *
     * @param exercise the exercise to create a version for
     * @param user     the user who created the version
     */
    public void createExerciseVersion(Exercise exercise, User user) {
        try {
            ExerciseVersionContent currentContent = createExerciseVersionContent(exercise);

            if (!isNewVersionNeeded(exercise.getId(), currentContent)) {
                log.debug("No changes detected for exercise {} with id {}, skipping version creation", exercise.getTitle(), exercise.getId());
                return;
            }

            ExerciseVersion exerciseVersion = new ExerciseVersion();
            exerciseVersion.setExercise(exercise);
            exerciseVersion.setAuthor(user);
            exerciseVersion.setContent(currentContent);
            exerciseVersionRepository.save(exerciseVersion);
            log.info("User {} has created exercise version for {} {} with id {}, version id {}", user.getLogin(), exercise.getClass().getSimpleName(), exercise.getTitle(),
                    exercise.getId(), exerciseVersion.getId());
        }
        catch (Exception e) {
            log.error("Error creating exercise version for exercise {} with id {}: {}", exercise.getTitle(), exercise.getId(), e.getMessage());
        }
    }

    /**
     * Creates an ExerciseVersionContent object from an Exercise.
     *
     * @param exercise the exercise to convert
     * @return ExerciseVersionContent containing the exercise data
     */
    private ExerciseVersionContent createExerciseVersionContent(Exercise exercise) {
        ExerciseVersionContent.Builder builder = ExerciseVersionContent.builder().shortName(exercise.getShortName()).title(exercise.getTitle())
                .problemStatement(exercise.getProblemStatement()).startDate(exercise.getStartDate()).releaseDate(exercise.getReleaseDate()).dueDate(exercise.getDueDate())
                .maxPoints(exercise.getMaxPoints()).bonusPoints(exercise.getBonusPoints()).difficulty(exercise.getDifficulty());

        if (exercise instanceof ProgrammingExercise programmingExercise) {
            addProgrammingExerciseFields(programmingExercise, builder);
        }

        if (exercise instanceof QuizExercise quizExercise) {
            addQuizExerciseFields(quizExercise, builder);
        }

        return builder.build();
    }

    /**
     * Adds programming exercise specific fields to the builder.
     *
     * @param programmingExercise the programming exercise
     * @param builder             the builder to add fields to
     */
    private void addProgrammingExerciseFields(ProgrammingExercise programmingExercise, ExerciseVersionContent.Builder builder) {
        try {
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
        }
        catch (Exception e) {
            log.warn("Error retrieving commit hashes for exercise {}: {}", programmingExercise.getTitle(), e.getMessage());
        }
    }

    /**
     * Adds quiz exercise specific fields to the builder.
     *
     * @param quizExercise the quiz exercise
     * @param builder      the builder to add fields to
     */
    private void addQuizExerciseFields(QuizExercise quizExercise, ExerciseVersionContent.Builder builder) {
        builder.isOpenForPractice(quizExercise.isIsOpenForPractice()).randomizeQuestionOrder(quizExercise.isRandomizeQuestionOrder())
                .allowedNumberOfAttempts(quizExercise.getAllowedNumberOfAttempts()).duration(quizExercise.getDuration());
    }

    /**
     * Checks if a new version is needed by comparing the current exercise content with the latest version.
     *
     * @param exerciseId     the ID of the exercise
     * @param currentContent the current content of the exercise
     * @return true if a new version is needed, false otherwise
     */
    private boolean isNewVersionNeeded(Long exerciseId, ExerciseVersionContent currentContent) {
        Optional<ExerciseVersion> latestVersionOpt = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);

        if (latestVersionOpt.isEmpty()) {
            return true;
        }

        ExerciseVersion latestVersion = latestVersionOpt.get();
        ExerciseVersionContent latestContent = latestVersion.getContent();

        return !currentContent.equals(latestContent);
    }
}
