package de.tum.cit.aet.artemis.athena.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATHENA;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.athena.domain.AthenaModuleMode;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.NetworkingException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
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

    private static final Logger log = LoggerFactory.getLogger(AthenaModuleService.class);

    private final RestTemplate shortTimeoutRestTemplate;

    private final ObjectMapper objectMapper;

    private final ExerciseRepository exerciseRepository;

    public AthenaModuleService(@Qualifier("shortTimeoutAthenaRestTemplate") RestTemplate shortTimeoutRestTemplate, MappingJackson2HttpMessageConverter springMvcJacksonConverter,
            ExerciseRepository exerciseRepository) {
        this.shortTimeoutRestTemplate = shortTimeoutRestTemplate;
        this.objectMapper = springMvcJacksonConverter.getObjectMapper();
        this.exerciseRepository = exerciseRepository;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record AthenaModuleDTO(@NotNull String name, @NotNull String type, @JsonProperty("supports_graded_feedback_requests") boolean supportsGradedFeedbackRequests,
            @JsonProperty("supports_non_graded_feedback_requests") boolean supportsNonGradedFeedbackRequests) {
    }

    /**
     * Get all available modules from Athena.
     *
     * @return A list of all available Athena Modules
     * @throws NetworkingException is thrown in case the modules can't be fetched from Athena
     */
    private List<AthenaModuleDTO> getAthenaModules() throws NetworkingException {
        try {
            var response = shortTimeoutRestTemplate.getForEntity(athenaUrl + "/modules", JsonNode.class);
            if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
                throw new NetworkingException("Could not fetch Athena modules");
            }
            AthenaModuleDTO[] modules = objectMapper.treeToValue(response.getBody(), AthenaModuleDTO[].class);
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
     * @param course           The course for which the modules should be retrieved
     * @param exerciseType     The exercise type for which the modules should be retrieved
     * @param athenaModuleMode the optional module type to filter the available modules
     * @return The list of available Athena text modules for the course
     * @throws NetworkingException is thrown in case the modules can't be fetched from Athena
     */
    public List<String> getAthenaModulesForCourse(Course course, ExerciseType exerciseType, @Nullable AthenaModuleMode athenaModuleMode) throws NetworkingException {

        final String exerciseTypeName = exerciseType.getExerciseTypeAsReadableString();

        Stream<AthenaModuleDTO> availableModules = getAthenaModules().stream().filter(module -> exerciseTypeName.equals(module.type()));

        if (!course.getRestrictedAthenaModulesAccess()) {
            // filter out restricted modules
            availableModules = availableModules.filter(module -> !restrictedModules.contains(module.name()));
        }

        if (athenaModuleMode != null) {
            availableModules = switch (athenaModuleMode) {
                case FEEDBACK_SUGGESTIONS -> availableModules.filter(AthenaModuleDTO::supportsGradedFeedbackRequests);
                case PRELIMINARY_FEEDBACK -> availableModules.filter(AthenaModuleDTO::supportsNonGradedFeedbackRequests);
            };
        }

        return availableModules.map(AthenaModuleDTO::name).toList();
    }

    /**
     * Get the URL for an Athena module, depending on the type of exercise.
     *
     * @param exerciseType The exercise type for which the URL to Athena should be returned
     * @param moduleName   The name of the Athena module to be consulted
     * @return The URL prefix to access the Athena module. Example: <a href="http://athena.example.com/modules/text/module_text_cofee"></a>
     */
    public String getAthenaModuleUrl(ExerciseType exerciseType, String moduleName) {
        switch (exerciseType) {
            case TEXT -> {
                return athenaUrl + "/modules/text/" + moduleName;
            }
            case PROGRAMMING -> {
                return athenaUrl + "/modules/programming/" + moduleName;
            }
            case MODELING -> {
                return athenaUrl + "/modules/modeling/" + moduleName;
            }
            default -> throw new IllegalArgumentException("Exercise type not supported: " + exerciseType);
        }
    }

    /**
     * Checks if an exercise has access to the provided Athena module.
     *
     * @param exercise         The exercise for which the access should be checked
     * @param course           The course to which the exercise belongs to.
     * @param athenaModuleMode The module type for which the access should be checked.
     * @param entityName       Name of the entity
     * @throws BadRequestAlertException when the exercise has no access to the exercise's provided module.
     */
    public void checkHasAccessToAthenaModule(Exercise exercise, Course course, AthenaModuleMode athenaModuleMode, String entityName) throws BadRequestAlertException {
        String module = getModule(exercise, athenaModuleMode);
        if (exercise.isExamExercise() && module != null) {
            throw new BadRequestAlertException("The exam exercise has no access to Athena", entityName, "examExerciseNoAccessToAthena");
        }
        if (!course.getRestrictedAthenaModulesAccess() && restrictedModules.contains(module)) {
            // Course does not have access to the restricted Athena modules
            throw new BadRequestAlertException("The exercise has no access to the selected Athena module of type " + athenaModuleMode, entityName, "noAccessToAthenaModule");
        }
    }

    private static String getModule(Exercise exercise, AthenaModuleMode athenaModuleMode) {
        var config = exercise.getAthenaConfig();
        if (config == null) {
            return null;
        }
        return switch (athenaModuleMode) {
            case FEEDBACK_SUGGESTIONS -> config.getFeedbackSuggestionModule();
            case PRELIMINARY_FEEDBACK -> config.getPreliminaryFeedbackModule();
        };
    }

    /**
     * Checks if a module change is valid or not. In case it is not allowed it throws an exception.
     * Modules cannot be changed after the exercise due date has passed.
     * Holds only for feedback suggestion modules.
     *
     * @param originalExercise The exercise before the update
     * @param updatedExercise  The exercise after the update
     * @param entityName       Name of the entity
     * @throws BadRequestAlertException Is thrown in case the module change is not allowed
     */
    public void checkValidAthenaModuleChange(Exercise originalExercise, Exercise updatedExercise, String entityName) throws BadRequestAlertException {
        var dueDate = originalExercise.getDueDate();
        var originalConfig = originalExercise.getAthenaConfig();
        var updatedConfig = updatedExercise.getAthenaConfig();
        var originalFeedbackModule = originalConfig != null ? originalConfig.getFeedbackSuggestionModule() : null;
        var updatedFeedbackModule = updatedConfig != null ? updatedConfig.getFeedbackSuggestionModule() : null;
        var originalPreliminaryModule = originalConfig != null ? originalConfig.getPreliminaryFeedbackModule() : null;
        var updatedPreliminaryModule = updatedConfig != null ? updatedConfig.getPreliminaryFeedbackModule() : null;

        checkValidityOfAnAthenaModuleBasedOnDueDate(originalFeedbackModule, updatedFeedbackModule, entityName, dueDate);
        checkValidityOfAnAthenaModuleBasedOnDueDate(originalPreliminaryModule, updatedPreliminaryModule, entityName, dueDate);
    }

    private static void checkValidityOfAnAthenaModuleBasedOnDueDate(String originalExerciseModule, String updatedExerciseModule, String entityName, ZonedDateTime dueDate) {
        if (!Objects.equals(originalExerciseModule, updatedExerciseModule) && dueDate != null && dueDate.isBefore(ZonedDateTime.now())) {
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
        exerciseRepository.revokeAccessToRestrictedPreliminaryFeedbackModulesByCourseId(course.getId(), restrictedModules);
    }
}
