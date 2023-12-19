package de.tum.in.www1.artemis.service.connectors.athena;

import java.util.List;

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

    private final Logger log = LoggerFactory.getLogger(AthenaModuleService.class);

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

    public List<String> getAthenaProgrammingModulesForCourse(Course course) throws NetworkingException {
        List<String> availableProgrammingModules = getAthenaModules().stream().filter(module -> "programming".equals(module.type)).map(module -> module.name).toList();
        if (!course.getRestrictedAthenaModulesAccess()) {
            // filter out restricted modules
            availableProgrammingModules = availableProgrammingModules.stream().filter(moduleName -> !restrictedModules.contains(moduleName)).toList();
        }
        return availableProgrammingModules;
    }

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

    public void checkHasAccessToAthenaModule(Exercise exercise, Course course, String entityName) throws BadRequestAlertException {
        if (!course.getRestrictedAthenaModulesAccess() && restrictedModules.contains(exercise.getFeedbackSuggestionModule())) {
            // Course does not have access to the restricted Athena modules
            throw new BadRequestAlertException("The exercise has no access to the selected Athena module", entityName, "noAccessToAthenaModule");
        }
    }

    public void revokeAccessToRestrictedFeedbackSuggestionModules(Course course) {
        exerciseRepository.revokeAccessToRestrictedFeedbackSuggestionModulesByCourseId(course.getId(), restrictedModules);
    }
}
