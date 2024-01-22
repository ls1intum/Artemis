package de.tum.in.www1.artemis.service.connectors.athena;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * Service to get the URL for an Athena module, depending on the type of exercise.
 */
@Service
@Profile("athena")
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
     * Get all available Athena programming modules for a specific course.
     *
     * @param course The course for which the modules should be retrieved
     * @return The list of available Athena text modules for the course
     * @throws NetworkingException is thrown in case the modules can't be fetched from Athena
     */
    public List<String> getAthenaProgrammingModulesForCourse(Course course) throws NetworkingException {
        List<String> availableProgrammingModules = getAthenaModules().stream().filter(module -> "programming".equals(module.type)).map(module -> module.name).toList();
        if (!course.getRestrictedAthenaModulesAccess()) {
            // filter out restricted modules
            availableProgrammingModules = availableProgrammingModules.stream().filter(moduleName -> !restrictedModules.contains(moduleName)).toList();
        }
        return availableProgrammingModules;
    }

    /**
     * Get all available Athena text modules for a specific course.
     *
     * @param course The course for which the modules should be retrieved
     * @return The list of available Athena text modules for the course
     * @throws NetworkingException is thrown in case the modules can't be fetched from Athena
     */
    public List<String> getAthenaTextModulesForCourse(Course course) throws NetworkingException {
        List<String> availableProgrammingModules = getAthenaModules().stream().filter(module -> "text".equals(module.type)).map(module -> module.name).toList();
        if (!course.getRestrictedAthenaModulesAccess()) {
            // filter out restricted modules
            availableProgrammingModules = availableProgrammingModules.stream().filter(moduleName -> !restrictedModules.contains(moduleName)).toList();
        }
        return availableProgrammingModules;
    }

    /**
     * Get the URL for an Athena module, depending on the type of exercise.
     *
     * @param exercise The exercise for which the URL to Athena should be returned
     * @return The URL prefix to access the Athena module. Example: <a href="http://athena.example.com/modules/text/module_text_cofee"></a>
     */
    public String getAthenaModuleUrl(Exercise exercise) {
        switch (exercise.getExerciseType()) {
            case TEXT -> {
                return athenaUrl + "/modules/text/" + exercise.getFeedbackSuggestionModule();
            }
            case PROGRAMMING -> {
                return athenaUrl + "/modules/programming/" + exercise.getFeedbackSuggestionModule();
            }
            default -> throw new IllegalArgumentException("Exercise type not supported: " + exercise.getExerciseType());
        }
    }

    /**
     * Checks if an exercise has access to the provided Athena module.
     *
     * @param exercise   The exercise for which the access should be checked
     * @param course     The course to which the exercise belongs to.
     * @param entityName Name of the entity
     * @throws BadRequestAlertException when the exercise has no access to the exercise's provided module.
     */
    public void checkHasAccessToAthenaModule(Exercise exercise, Course course, String entityName) throws BadRequestAlertException {
        if (exercise.isExamExercise() && exercise.getFeedbackSuggestionModule() != null) {
            throw new BadRequestAlertException("The exam exercise has no access to Athena", entityName, "examExerciseNoAccessToAthena");
        }
        if (!course.getRestrictedAthenaModulesAccess() && restrictedModules.contains(exercise.getFeedbackSuggestionModule())) {
            // Course does not have access to the restricted Athena modules
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
        if (!Objects.equals(originalExercise.getFeedbackSuggestionModule(), updatedExercise.getFeedbackSuggestionModule()) && dueDate != null
                && dueDate.isBefore(ZonedDateTime.now())) {
            throw new BadRequestAlertException("Athena module can't be changed after due date has passed", entityName, "athenaModuleChangeAfterDueDate");
        }
    }

    /**
     * Revokes the access to restricted Athena modules for all exercises of a course.
     *
     * @param course The course for which the access to restricted modules should be revoked
     */
    public void revokeAccessToRestrictedFeedbackSuggestionModules(Course course) {
        exerciseRepository.revokeAccessToRestrictedFeedbackSuggestionModulesByCourseId(course.getId(), restrictedModules);
    }
}
