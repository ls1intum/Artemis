package de.tum.cit.aet.artemis.athena.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.athena.domain.AthenaModuleMode;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseAthenaConfigRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * Service to get the URL for an Athena module, depending on the type of exercise.
 */
@Lazy
@Service
@Profile(PROFILE_ATHENA)
public class AthenaModuleService {

    @Value("${artemis.athena.url}")
    private String athenaUrl;

    // If value is present, split the provided modules by comma; default to empty list
    @Value("#{'${artemis.athena.restricted-modules:}'}")
    private List<String> restrictedModules;

    private static final String ENTITY_NAME = "exercise";

    private static final Logger log = LoggerFactory.getLogger(AthenaModuleService.class);

    private final RestTemplate shortTimeoutRestTemplate;

    private final ObjectMapper objectMapper;

    private final ExerciseRepository exerciseRepository;

    private final ExerciseAthenaConfigRepository exerciseAthenaConfigRepository;

    public AthenaModuleService(@Qualifier("shortTimeoutAthenaRestTemplate") RestTemplate shortTimeoutRestTemplate, ObjectMapper objectMapper,
            ExerciseRepository exerciseRepository, ExerciseAthenaConfigRepository exerciseAthenaConfigRepository) {
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.objectMapper = objectMapper;
        this.exerciseRepository = exerciseRepository;
        this.exerciseAthenaConfigRepository = exerciseAthenaConfigRepository;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record AthenaModuleDTO(String name, String type) {
    }

    /**
     * Get all available modules from Athena.
     *
     * @return A list of all available Athena Modules
     * @throws NetworkingException is thrown in case the modules can't be fetched from Athena
     */
    private List<AthenaModuleDTO> getAthenaModules() throws NetworkingException {
        try {
            var response = shortTimeoutRestTemplate.getForEntity(athenaUrl + "/modules", String.class);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
                throw new NetworkingException("Could not fetch Athena modules");
            }
            AthenaModuleDTO[] modules = objectMapper.readValue(response.getBody(), AthenaModuleDTO[].class);
            return List.of(modules);
        }
        catch (RestClientException | JsonProcessingException e) {
            log.error("Failed to fetch modules from Athena", e);
            throw new NetworkingException("Failed to fetch modules from Athena", e);
        }
    }

    /**
     * Get all available Athena modules for a specific course and exercise type
     *
     * @param course       The course for which the modules should be retrieved
     * @param exerciseType The exercise type for which the modules should be retrieved
     * @return The list of available Athena text modules for the course
     * @throws NetworkingException is thrown in case the modules can't be fetched from Athena
     */
    public List<String> getAthenaModulesForCourse(Course course, ExerciseType exerciseType) throws NetworkingException {

        final String exerciseTypeName = exerciseType.getExerciseTypeAsReadableString();

        Stream<String> availableModules = getAthenaModules().stream().filter(module -> exerciseTypeName.equals(module.type)).map(module -> module.name);

        if (!course.getRestrictedAthenaModulesAccess()) {
            // filter out restricted modules
            availableModules = availableModules.filter(moduleName -> !restrictedModules.contains(moduleName));
        }
        return availableModules.toList();
    }

    /**
     * Get all available Athena modules for a specific course, exercise type, and module mode.
     *
     * @param course       The course for which the modules should be retrieved
     * @param exerciseType The exercise type for which the modules should be retrieved
     * @param moduleMode   The module mode (PRELIMINARY or GRADED)
     * @return The list of available Athena modules for the course and mode
     * @throws NetworkingException is thrown in case the modules can't be fetched from Athena
     */
    public List<String> getAthenaModulesForCourse(Course course, ExerciseType exerciseType, AthenaModuleMode moduleMode) throws NetworkingException {
        // For now, return all modules for the exercise type
        // In the future, this could filter based on module capabilities
        return getAthenaModulesForCourse(course, exerciseType);
    }

    /**
     * Resolves the Athena module name for the given exercise and mode.
     * Checks the mode-specific module from {@link de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig}
     * first, then falls back to the legacy {@code feedbackSuggestionModule} field.
     *
     * @param exercise   the exercise
     * @param moduleMode the mode (PRELIMINARY or GRADED)
     * @return the resolved module name, or {@code null} if none is configured
     */
    private String resolveModuleForMode(Exercise exercise, AthenaModuleMode moduleMode) {
        String module = moduleMode == AthenaModuleMode.PRELIMINARY ? exercise.getPreliminaryFeedbackModule() : exercise.getGradedFeedbackModule();
        return module != null ? module : exercise.getFeedbackSuggestionModule();
    }

    /**
     * Get the URL for an Athena module for the given mode.
     * Uses the mode-specific module from {@link de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig}
     * if set, otherwise falls back to the legacy {@code feedbackSuggestionModule}.
     *
     * @param exercise   The exercise for which the URL to Athena should be returned
     * @param moduleMode The module mode (PRELIMINARY or GRADED)
     * @return The URL prefix to access the Athena module
     * @throws BadRequestAlertException if no module is configured for the exercise and mode
     */
    public String getAthenaModuleUrl(Exercise exercise, AthenaModuleMode moduleMode) {
        String module = resolveModuleForMode(exercise, moduleMode);
        if (module == null) {
            throw new BadRequestAlertException("Exercise does not have a feedback suggestion module configured", ENTITY_NAME, "missingFeedbackSuggestionModule");
        }
        return switch (exercise.getExerciseType()) {
            case TEXT -> athenaUrl + "/modules/text/" + module;
            case PROGRAMMING -> athenaUrl + "/modules/programming/" + module;
            case MODELING -> athenaUrl + "/modules/modeling/" + module;
            default -> throw new IllegalArgumentException("Exercise type not supported: " + exercise.getExerciseType());
        };
    }

    /**
     * Get the URL for an Athena module, depending on the type of exercise.
     * Uses the legacy {@code feedbackSuggestionModule} field. Prefer {@link #getAthenaModuleUrl(Exercise, AthenaModuleMode)}.
     *
     * @param exercise The exercise for which the URL to Athena should be returned
     * @return The URL prefix to access the Athena module. Example: <a href="http://athena.example.com/modules/text/module_text_cofee"></a>
     * @throws BadRequestAlertException if the exercise has no feedback suggestion module configured
     */
    public String getAthenaModuleUrl(Exercise exercise) {
        return getAthenaModuleUrl(exercise, AthenaModuleMode.GRADED);
    }

    /**
     * Checks if an exercise has access to the provided Athena module for a specific mode.
     *
     * @param exercise   The exercise for which the access should be checked
     * @param course     The course to which the exercise belongs to.
     * @param moduleMode The module mode (PRELIMINARY or GRADED)
     * @param entityName Name of the entity
     * @throws BadRequestAlertException when the exercise has no access to the exercise's provided module.
     */
    public void checkHasAccessToAthenaModule(Exercise exercise, Course course, AthenaModuleMode moduleMode, String entityName) throws BadRequestAlertException {
        String module = moduleMode == AthenaModuleMode.PRELIMINARY ? exercise.getPreliminaryFeedbackModule() : exercise.getGradedFeedbackModule();

        if (exercise.isExamExercise() && module != null) {
            throw new BadRequestAlertException("The exam exercise has no access to Athena", entityName, "examExerciseNoAccessToAthena");
        }
        if (!course.getRestrictedAthenaModulesAccess() && restrictedModules.contains(module)) {
            throw new BadRequestAlertException("The exercise has no access to the selected Athena module", entityName, "noAccessToAthenaModule");
        }
    }

    /**
     * Checks if a module change is valid or not. In case it is not allowed it throws an exception.
     * Modules cannot be changed after the exercise due date has passed.
     *
     * @param originalExercise The exercise before the update
     * @param updatedExercise  The exercise after the update
     * @param entityName       Name of the entity
     * @throws BadRequestAlertException Is thrown in case the module change is not allowed
     */
    public void checkValidAthenaModuleChange(Exercise originalExercise, Exercise updatedExercise, String entityName) throws BadRequestAlertException {
        var dueDate = originalExercise.getDueDate();
        if (dueDate == null || !dueDate.isBefore(ZonedDateTime.now())) {
            return;
        }
        boolean anyModuleChanged = !Objects.equals(originalExercise.getFeedbackSuggestionModule(), updatedExercise.getFeedbackSuggestionModule())
                || !Objects.equals(originalExercise.getPreliminaryFeedbackModule(), updatedExercise.getPreliminaryFeedbackModule())
                || !Objects.equals(originalExercise.getGradedFeedbackModule(), updatedExercise.getGradedFeedbackModule());
        if (anyModuleChanged) {
            throw new BadRequestAlertException("Athena module can't be changed after due date has passed", entityName, "athenaModuleChangeAfterDueDate");
        }
    }

    /**
     * Revokes the access to restricted Athena modules for all exercises of a course.
     *
     * @param course The course for which the access to restricted modules should be revoked
     */
    public void revokeAccessToRestrictedFeedbackModules(Course course) {
        exerciseRepository.revokeAccessToRestrictedFeedbackSuggestionModulesByCourseId(course.getId(), restrictedModules);
        if (!restrictedModules.isEmpty()) {
            exerciseAthenaConfigRepository.revokeRestrictedModulesByCourseId(course.getId(), restrictedModules);
        }
    }
}
