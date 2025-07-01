package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
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

    private final ObjectMapper objectMapper;

    private final GitService gitService;

    public ExerciseVersionService(ExerciseVersionRepository exerciseVersionRepository, ObjectMapper objectMapper, GitService gitService) {
        this.exerciseVersionRepository = exerciseVersionRepository;
        this.objectMapper = objectMapper;
        this.gitService = gitService;
    }

    /**
     * Creates a new ExerciseVersion for the given exercise and saves it to the database.
     * The content field of the ExerciseVersion contains a JSON representation of the exercise with the following fields:
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
            // Create JSON content for the current exercise state
            ObjectNode currentContent = createExerciseVersionContent(exercise);

            // Check if a new version is needed by comparing with the latest version
            if (!isNewVersionNeeded(exercise.getId(), currentContent)) {
                log.debug("No changes detected for exercise {} with id {}, skipping version creation", exercise.getTitle(), exercise.getId());
                return;
            }

            // Create and save the new version
            ExerciseVersion exerciseVersion = new ExerciseVersion();
            exerciseVersion.setExercise(exercise);
            exerciseVersion.setAuthor(user);
            exerciseVersion.setContent(objectMapper.writeValueAsString(currentContent));

            // Save the exercise version
            exerciseVersionRepository.save(exerciseVersion);

            log.info("User {} has created exercise version for {} {} with id {}, version id {}", user.getLogin(), exercise.getClass().getSimpleName(), exercise.getTitle(),
                    exercise.getId(), exerciseVersion.getId());
        }
        catch (Exception e) {
            log.error("Error creating exercise version for exercise {} with id {}: {}", exercise.getTitle(), exercise.getId(), e.getMessage());
        }
    }

    /**
     * Creates a JSON representation of the exercise with all the relevant fields.
     *
     * @param exercise the exercise to convert to JSON
     * @return ObjectNode containing the JSON representation of the exercise
     */
    private ObjectNode createExerciseVersionContent(Exercise exercise) {
        ObjectNode contentNode = objectMapper.createObjectNode();

        // Add commit IDs based on exercise type
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            // For programming exercises, get the commit hashes
            try {
                if (programmingExercise.getTemplateParticipation() != null && programmingExercise.getTemplateParticipation().getVcsRepositoryUri() != null) {
                    var templateCommitHash = gitService.getLastCommitHash(programmingExercise.getTemplateParticipation().getVcsRepositoryUri());
                    if (templateCommitHash != null) {
                        contentNode.put("template_commit_id", templateCommitHash.getName());
                    }
                }

                if (programmingExercise.getSolutionParticipation() != null && programmingExercise.getSolutionParticipation().getVcsRepositoryUri() != null) {
                    var solutionCommitHash = gitService.getLastCommitHash(programmingExercise.getSolutionParticipation().getVcsRepositoryUri());
                    if (solutionCommitHash != null) {
                        contentNode.put("solution_commit_id", solutionCommitHash.getName());
                    }
                }

                if (programmingExercise.getTestRepositoryUri() != null) {
                    var testCommitHash = gitService.getLastCommitHash(programmingExercise.getVcsTestRepositoryUri());
                    if (testCommitHash != null) {
                        contentNode.put("tests_commit_id", testCommitHash.getName());
                    }
                }
            }
            catch (Exception e) {
                log.warn("Error retrieving commit hashes for exercise {}: {}", exercise.getTitle(), e.getMessage());
            }
        }

        // Add other metadata fields
        contentNode.put("short_name", exercise.getShortName());
        contentNode.put("title", exercise.getTitle());
        contentNode.put("problem_statement", exercise.getProblemStatement());

        // Add dates
        if (exercise.getStartDate() != null) {
            contentNode.put("start_date", exercise.getStartDate().toString());
        }
        if (exercise.getReleaseDate() != null) {
            contentNode.put("release_date", exercise.getReleaseDate().toString());
        }
        if (exercise.getDueDate() != null) {
            contentNode.put("due_date", exercise.getDueDate().toString());
        }

        // Add duration (if applicable)
        if (exercise.getDueDate() != null && exercise.getReleaseDate() != null) {
            long durationInSeconds = java.time.Duration.between(exercise.getReleaseDate(), exercise.getDueDate()).getSeconds();
            contentNode.put("duration", durationInSeconds);
        }

        // Add points and difficulty
        contentNode.put("max_points", exercise.getMaxPoints());
        if (exercise.getDifficulty() != null) {
            contentNode.put("difficulty", exercise.getDifficulty().toString());
        }
        if (exercise.getBonusPoints() != null) {
            contentNode.put("bonus_points", exercise.getBonusPoints());
        }

        // Add quiz-specific fields if the exercise is a QuizExercise
        if (exercise instanceof QuizExercise quizExercise) {
            contentNode.put("is_open_for_practice", quizExercise.isIsOpenForPractice());

            // Add randomize_question_order
            if (quizExercise.isRandomizeQuestionOrder() != null) {
                contentNode.put("randomize_question_order", quizExercise.isRandomizeQuestionOrder());
            }

            // Add allowed_number_of_attempts
            if (quizExercise.getAllowedNumberOfAttempts() != null) {
                contentNode.put("allowed_number_of_attempts", quizExercise.getAllowedNumberOfAttempts());
            }

            // Add is_open_for_practice specific to QuizExercise
            if (quizExercise.isIsOpenForPractice() != null) {
                contentNode.put("is_open_for_practice", quizExercise.isIsOpenForPractice());
            }

            // Add duration specific to QuizExercise (overrides the calculated duration)
            if (quizExercise.getDuration() != null) {
                contentNode.put("duration", quizExercise.getDuration());
            }
        }

        return contentNode;
    }

    /**
     * Checks if a new version is needed by comparing the current exercise content with the latest version.
     *
     * @param exerciseId     the ID of the exercise
     * @param currentContent the current content of the exercise as JSON
     * @return true if a new version is needed, false otherwise
     */
    private boolean isNewVersionNeeded(Long exerciseId, ObjectNode currentContent) {
        // Get the latest version of the exercise
        Optional<ExerciseVersion> latestVersionOpt = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId);

        // If no previous version exists, a new version is needed
        if (latestVersionOpt.isEmpty()) {
            return true;
        }

        try {
            // Compare the current content with the latest version
            ExerciseVersion latestVersion = latestVersionOpt.get();
            JsonNode latestContent = objectMapper.readTree(latestVersion.getContent());

            // Deep comparison of the JSON content
            return !currentContent.equals(latestContent);
        }
        catch (JsonProcessingException e) {
            // If there's an error parsing the JSON, assume a new version is needed
            log.warn("Error comparing exercise versions for exercise with id {}: {}", exerciseId, e.getMessage());
            return true;
        }
    }
}
