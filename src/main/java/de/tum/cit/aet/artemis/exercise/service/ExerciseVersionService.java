package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.Transient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.exception.ExerciseVersioningException;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;


@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseVersionService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseVersionService.class);

    /**
     * Field patterns to exclude from versioning based on analysis recommendations.
     * These patterns help filter out transient, sensitive, or large data that shouldn't be versioned.
     */
    private static final Set<String> EXCLUDED_FIELD_PATTERNS = Set.of(
        ".*Transient$",
        "studentParticipations",
        "submissions",
        "results",
        "participation.*",
        ".*Password.*",
        ".*Token.*"
    );

    private final ExerciseVersionRepository exerciseVersionRepository;
    private final GitService gitService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    
    // Type-specific repositories for complete data loading
    private final TextExerciseRepository textExerciseRepository;
    private final ProgrammingExerciseRepository programmingExerciseRepository;
    private final ModelingExerciseRepository modelingExerciseRepository;
    private final QuizExerciseRepository quizExerciseRepository;
    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    public ExerciseVersionService(ExerciseVersionRepository exerciseVersionRepository,
                                  GitService gitService,
                                  ObjectMapper objectMapper,
                                  UserRepository userRepository,
                                  TextExerciseRepository textExerciseRepository,
                                  ProgrammingExerciseRepository programmingExerciseRepository,
                                  ModelingExerciseRepository modelingExerciseRepository,
                                  QuizExerciseRepository quizExerciseRepository,
                                  FileUploadExerciseRepository fileUploadExerciseRepository) {
        this.exerciseVersionRepository = exerciseVersionRepository;
        this.gitService = gitService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.textExerciseRepository = textExerciseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
    }

    /**
     * Creates versions for existing exercises (called pre-save).
     * This method should only be called for exercises that already have an ID.
     * For new exercises, use onExerciseCreated() after the save operation.
     *
     * @param exercise the exercise to version (must have an ID)
     * @throws ExerciseVersioningException.InvalidExerciseStateException if exercise has no ID
     * @throws ExerciseVersioningException.VersionCreationException      if version creation fails
     */
    public <T extends Exercise> void onSaveExercise(T exercise) {
        if (exercise.getId() == null) {
            log.error("onSaveExercise called for exercise without ID - this violates the architecture contract");
            throw new ExerciseVersioningException.InvalidExerciseStateException(
                "onSaveExercise called for exercise without ID - this should not happen with the new architecture");
        }

        createVersionForExistingExercise(exercise);
    }

    /**
     * Creates versions for existing exercises (incremental diff logic).
     *
     * @throws ExerciseVersioningException.InvalidExerciseStateException if exercise doesn't exist in database
     */
    private void createVersionForExistingExercise(Exercise exercise) {
        // Get current exercise with appropriate data loading based on type
        Optional<Exercise> currentExerciseOpt = findExerciseWithCompleteData(exercise.getId());

        // Get latest version of exercise
        Optional<ExerciseVersion> latestVersionOpt = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId());

        if (currentExerciseOpt.isPresent()) {
            Exercise currentExercise = currentExerciseOpt.get();

            if (latestVersionOpt.isPresent()) {
                // Exercise exists and has previous versions - create incremental diff
                createIncrementalVersion(exercise, currentExercise, latestVersionOpt.get());
            } else {
                // Exercise exists but no versions yet - create initial full snapshot
                createInitialVersion(exercise);
            }
        } else {
            // Exercise doesn't exist in database - this is an error state
            log.error("Exercise with ID {} not found in database during versioning - cannot create version for non-existent exercise", exercise.getId());
            throw new ExerciseVersioningException.InvalidExerciseStateException(
                "Exercise with ID " + exercise.getId() + " not found in database during versioning - cannot create version for non-existent exercise");
        }
    }

    /**
     * Called after a new exercise has been created and saved to the database.
     * Creates the initial version for newly created exercises.
     *
     * @param exercise the newly created exercise (must have an ID)
     * @throws ExerciseVersioningException.InvalidExerciseStateException    if exercise has no ID
     * @throws ExerciseVersioningException.DuplicateInitialVersionException if initial version already exists
     * @throws ExerciseVersioningException.VersionCreationException         if version creation fails
     */
    public <T extends Exercise> void onExerciseCreated(T exercise) {
        if (exercise.getId() == null) {
            log.error("Cannot create initial version for exercise without ID - exercise must be saved first");
            throw new ExerciseVersioningException.InvalidExerciseStateException(
                "Cannot create initial version for exercise without ID");
        }

        // Check if version already exists - this should NOT happen for new exercises
        Optional<ExerciseVersion> existingVersion = exerciseVersionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exercise.getId());
        if (existingVersion.isPresent()) {
            log.error("Attempt to create duplicate initial version for exercise ID: {} - initial version already exists", exercise.getId());
            throw new ExerciseVersioningException.DuplicateInitialVersionException(exercise.getId());
        }

        // Create initial version for the new exercise
        createInitialVersion(exercise);
    }


    /**
     * Creates the initial full snapshot version for an exercise
     */
    private void createInitialVersion(Exercise exercise) {
        log.debug("Creating initial version for exercise ID: {}", exercise.getId());

        Map<String, Object> exerciseData = extractExerciseData(exercise);
        String contentHash = calculateContentHash(exerciseData);

        ExerciseVersion version = new ExerciseVersion();
        version.setExercise(exercise);
        version.setVersionType(ExerciseVersion.VersionType.FULL_SNAPSHOT);
        version.setContent(exerciseData);
        version.setContentHash(contentHash);
        version.setPreviousVersion(null);
        
        // Set the author to the current user
        version.setAuthor(userRepository.getUser());

        exerciseVersionRepository.save(version);
        log.info("Created initial version for exercise ID: {}", exercise.getId());
    }

    /**
     * Creates an incremental diff version by comparing current state with previous state
     */
    private void createIncrementalVersion(Exercise newExercise, Exercise currentExercise, ExerciseVersion latestVersion) {
        log.debug("Creating incremental version for exercise ID: {}", newExercise.getId());

        // Extract data from both versions
        Map<String, Object> currentData = extractExerciseData(currentExercise);
        Map<String, Object> newData = extractExerciseData(newExercise);

        // Calculate differences
        Map<String, Object> diff = calculateDifferences(currentData, newData);

        // Only create version if there are actual changes
        if (diff.isEmpty()) {
            log.debug("No changes detected for exercise ID: {}, skipping version creation", newExercise.getId());
            return;
        }

        String contentHash = calculateContentHash(newData);

        ExerciseVersion version = new ExerciseVersion();
        version.setExercise(newExercise);
        version.setVersionType(ExerciseVersion.VersionType.INCREMENTAL_DIFF);
        version.setContent(diff);
        version.setContentHash(contentHash);
        version.setPreviousVersion(latestVersion);
        
        // Set the author to the current user
        version.setAuthor(userRepository.getUser());

        exerciseVersionRepository.save(version);
        log.info("Created incremental version for exercise ID: {} with {} changes", newExercise.getId(), diff.size());
    }

    /**
     * Finds an exercise with complete data using type-specific repositories.
     * Each repository can define proper EntityGraphs for their specific exercise type.
     */
    private Optional<Exercise> findExerciseWithCompleteData(Long exerciseId) {
        // First, determine the exercise type by checking each repository
        // We check in order from most specific to most general
        
        // Try to find with complete data in each repository type
        var programmingExercise = programmingExerciseRepository.findWithCompleteDataForVersioningById(exerciseId);
        if (programmingExercise.isPresent()) {
            return Optional.of(programmingExercise.get());
        }
        
        var textExercise = textExerciseRepository.findWithCompleteDataForVersioningById(exerciseId);
        if (textExercise.isPresent()) {
            return Optional.of(textExercise.get());
        }
        
        var modelingExercise = modelingExerciseRepository.findWithCompleteDataForVersioningById(exerciseId);
        if (modelingExercise.isPresent()) {
            return Optional.of(modelingExercise.get());
        }
        
        var quizExercise = quizExerciseRepository.findWithCompleteDataForVersioningById(exerciseId);
        if (quizExercise.isPresent()) {
            return Optional.of(quizExercise.get());
        }
        
        var fileUploadExercise = fileUploadExerciseRepository.findWithCompleteDataForVersioningById(exerciseId);
        if (fileUploadExercise.isPresent()) {
            return Optional.of(fileUploadExercise.get());
        }
        
        // Exercise not found in any type-specific repository
        return Optional.empty();
    }

    /**
     * Extracts all relevant data from an exercise using reflection with smart filtering.
     * Based on analysis recommendations - combines reflection with type-specific logic.
     *
     * @throws ExerciseVersioningException.VersionCreationException if data extraction fails
     */
    private Map<String, Object> extractExerciseData(Exercise exercise) {
        Map<String, Object> fields = new HashMap<>();

        try {
            // 1. Reflection-based capture with filtering (primary approach)
            captureReflectionFields(exercise, fields);

            // 2. Type-specific logic for special fields (as per analysis)
            captureTypeSpecificFields(exercise, fields);

            return fields;
        } catch (Exception e) {
            log.error("Failed to extract exercise data for exercise ID: {} - data extraction is critical for versioning", exercise.getId(), e);
            throw new ExerciseVersioningException.VersionCreationException(
                exercise.getId(), "extract exercise data", e);
        }
    }

    /**
     * Primary field extraction using reflection with filtering rules from analysis.
     */
    private void captureReflectionFields(Exercise exercise, Map<String, Object> fields) {
        Class<?> clazz = exercise.getClass();

        // Walk up inheritance hierarchy to capture all fields
        while (clazz != null && clazz != Object.class) {
            Field[] declaredFields = clazz.getDeclaredFields();

            for (Field field : declaredFields) {
                if (shouldIncludeField(field)) {
                    field.setAccessible(true);
                    try {
                        // Only access fields that are declared on this specific class or its proper parent classes
                        if (field.getDeclaringClass().isAssignableFrom(exercise.getClass())) {
                            Object value = field.get(exercise);
                            if (value != null) {
                                fields.put(field.getName(), sanitizeValue(value));
                            }
                        }
                    } catch (IllegalAccessException e) {
                        log.debug("Cannot access field {}: {}", field.getName(), e.getMessage());
                    } catch (Exception e) {
                        // Handle other reflection errors (e.g., field not available on this exercise type)
                        log.debug("Error accessing field {} on exercise type {}: {}", 
                            field.getName(), exercise.getClass().getSimpleName(), e.getMessage());
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Determines if a field should be included based on analysis filtering rules.
     */
    private boolean shouldIncludeField(Field field) {
        // Exclude transient fields
        if (Modifier.isTransient(field.getModifiers()) ||
            field.isAnnotationPresent(Transient.class)) {
            return false;
        }

        // Exclude static fields
        if (Modifier.isStatic(field.getModifiers())) {
            return false;
        }

        // Exclude by pattern matching
        String fieldName = field.getName();
        return EXCLUDED_FIELD_PATTERNS.stream()
            .noneMatch(fieldName::matches);
    }

    /**
     * Type-specific field capture as recommended in analysis.
     * Handles special cases like git commits for programming exercises.
     */
    private void captureTypeSpecificFields(Exercise exercise, Map<String, Object> fields) {
        if (exercise instanceof ProgrammingExercise programmingEx) {
            captureProgrammingSpecificFields(programmingEx, fields);
        }
        // Text, Modeling, Quiz and FileUpload exercises don't need special handling
    }

    /**
     * Captures programming exercise specific data as per analysis recommendations.
     */
    private void captureProgrammingSpecificFields(ProgrammingExercise exercise,
                                                  Map<String, Object> fields) {
        try {
            // Capture git commit IDs - critical external data as identified in analysis
            if (exercise.getTemplateParticipation() != null &&
                exercise.getTemplateParticipation().getVcsRepositoryUri() != null) {

                var templateCommit = gitService.getLastCommitHash(
                    exercise.getTemplateParticipation().getVcsRepositoryUri());
                if (templateCommit != null) {
                    fields.put("templateCommitId", templateCommit.getName());
                }
            }

            // Solution repository
            if (exercise.getSolutionParticipation() != null &&
                exercise.getSolutionParticipation().getVcsRepositoryUri() != null) {

                var solutionCommit = gitService.getLastCommitHash(
                    exercise.getSolutionParticipation().getVcsRepositoryUri());
                if (solutionCommit != null) {
                    fields.put("solutionCommitId", solutionCommit.getName());
                }
            }

            // Test repository
            if (exercise.getVcsTestRepositoryUri() != null) {
                var testCommit = gitService.getLastCommitHash(exercise.getVcsTestRepositoryUri());
                if (testCommit != null) {
                    fields.put("testsCommitId", testCommit.getName());
                }
            }

            // Auxiliary repositories - capture commit IDs for each auxiliary repo
            if (exercise.getAuxiliaryRepositories() != null && !exercise.getAuxiliaryRepositories().isEmpty()) {
                Map<String, String> auxiliaryCommitIds = new HashMap<>();

                for (var auxRepo : exercise.getAuxiliaryRepositories()) {
                    if (auxRepo.getVcsRepositoryUri() != null && auxRepo.getName() != null) {
                        try {
                            var auxCommit = gitService.getLastCommitHash(auxRepo.getVcsRepositoryUri());
                            if (auxCommit != null) {
                                auxiliaryCommitIds.put(auxRepo.getName(), auxCommit.getName());
                            }
                        } catch (Exception e) {
                            log.debug("Could not get commit hash for auxiliary repository '{}' in exercise {}: {}",
                                auxRepo.getName(), exercise.getId(), e.getMessage());
                            // Continue with other auxiliary repos - don't fail the entire versioning
                        }
                    }
                }

                if (!auxiliaryCommitIds.isEmpty()) {
                    fields.put("auxiliaryRepositoryCommitIds", auxiliaryCommitIds);
                }
            }
        } catch (Exception e) {
            log.warn("Error capturing programming exercise specific data for {}: {}",
                exercise.getId(), e.getMessage());
        }
    }

    /**
     * Sanitizes field values to ensure they can be properly stored as JSON.
     */
    private Object sanitizeValue(Object value) {
        // Handle special cases that might not serialize well
        if (value instanceof Class<?>) {
            return ((Class<?>) value).getSimpleName();
        }

        // For now, return as-is. Jackson will handle most cases.
        // Future enhancements could add more sanitization logic here.
        return value;
    }


    /**
     * Calculates differences between two exercise data maps.
     * Returns only the fields that have changed.
     */
    private Map<String, Object> calculateDifferences(Map<String, Object> oldData, Map<String, Object> newData) {
        Map<String, Object> differences = new HashMap<>();

        // Check for new or changed fields
        for (Map.Entry<String, Object> entry : newData.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = oldData.get(key);

            if (!java.util.Objects.equals(oldValue, newValue)) {
                differences.put(key, newValue);
            }
        }

        // Check for removed fields (fields that were in old but not in new)
        for (String key : oldData.keySet()) {
            if (!newData.containsKey(key)) {
                differences.put(key, null); // null indicates field was removed
            }
        }

        return differences;
    }

    /**
     * Calculates SHA-256 hash of the content for integrity verification.
     *
     * @throws ExerciseVersioningException.VersionCreationException if hash calculation fails
     */
    private String calculateContentHash(Map<String, Object> content) {
        try {
            String jsonString = objectMapper.writeValueAsString(content);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jsonString.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            log.error("Failed to calculate content hash for exercise data - hash calculation is critical for versioning integrity", e);
            throw new ExerciseVersioningException.VersionCreationException(
                null, "calculate content hash", e);
        }
    }
}
