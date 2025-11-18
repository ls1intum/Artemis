package de.tum.cit.aet.artemis.atlas.service.atlasml;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.config.AtlasMLRestTemplateConfiguration;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRelationsResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;

/**
 * Service for communicating with the AtlasML microservice.
 * Provides methods for suggesting and saving competencies.
 */
@Conditional(AtlasEnabled.class)
@Service
@Lazy
public class AtlasMLService {

    private static final Logger log = LoggerFactory.getLogger(AtlasMLService.class);

    private final RestTemplate atlasmlRestTemplate;

    private final RestTemplate shortTimeoutAtlasmlRestTemplate;

    private final AtlasMLRestTemplateConfiguration config;

    private final CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    private final FeatureToggleService featureToggleService;

    // API endpoints
    private static final String HEALTH_ENDPOINT = "/api/v1/health/";

    private static final String SUGGEST_ENDPOINT = "/api/v1/competency/suggest";

    private static final String SAVE_ENDPOINT = "/api/v1/competency/save";

    private static final String SUGGEST_RELATIONS_ENDPOINT = "/api/v1/competency/relations/suggest/%s";

    public AtlasMLService(@Qualifier("atlasmlRestTemplate") RestTemplate atlasmlRestTemplate,
            @Qualifier("shortTimeoutAtlasmlRestTemplate") RestTemplate shortTimeoutAtlasmlRestTemplate, AtlasMLRestTemplateConfiguration config,
            CompetencyExerciseLinkRepository competencyExerciseLinkRepository, FeatureToggleService featureToggleService) {
        this.atlasmlRestTemplate = atlasmlRestTemplate;
        this.shortTimeoutAtlasmlRestTemplate = shortTimeoutAtlasmlRestTemplate;
        this.config = config;
        this.competencyExerciseLinkRepository = competencyExerciseLinkRepository;
        this.featureToggleService = featureToggleService;
    }

    /**
     * Checks the health status of the AtlasML microservice.
     *
     * @return true if the service is healthy, false otherwise
     */
    public boolean isHealthy() {
        try {
            log.debug("Checking AtlasML health status");
            HttpHeaders headers = buildHeadersWithAuth();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = shortTimeoutAtlasmlRestTemplate.exchange(config.getAtlasmlBaseUrl() + HEALTH_ENDPOINT, HttpMethod.GET, entity, String.class);

            boolean isHealthy = response.getStatusCode().is2xxSuccessful();
            log.debug("AtlasML health check result: {}", isHealthy);
            return isHealthy;
        }
        catch (HttpClientErrorException | HttpServerErrorException e) {
            log.warn("AtlasML health check failed with HTTP error: {}", e.getMessage());
            return false;
        }
        catch (ResourceAccessException e) {
            log.warn("AtlasML health check failed due to connection issue: {}", e.getMessage());
            return false;
        }
        catch (Exception e) {
            log.error("Unexpected error during AtlasML health check", e);
            return false;
        }
    }

    /**
     * Suggests competencies based on the provided request.
     *
     * @param request the suggestion request containing id and description
     * @return the suggested competency IDs and their relations
     */
    public SuggestCompetencyResponseDTO suggestCompetencies(SuggestCompetencyRequestDTO request) {
        try {
            log.debug("Requesting competency suggestions for id: {}", request.description());
            HttpHeaders headers = buildHeadersWithAuth();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<SuggestCompetencyRequestDTO> entity = new HttpEntity<>(request, headers);

            // Get the raw response as String first to handle empty array responses
            // TODO: please directly convert the response: the REST Template can handle empty responses
            ResponseEntity<String> response = atlasmlRestTemplate.exchange(config.getAtlasmlBaseUrl() + SUGGEST_ENDPOINT, HttpMethod.POST, entity, String.class);

            String responseBody = response.getBody();

            // Handle empty array response
            if (responseBody != null && responseBody.trim().equals("[]")) {
                return new SuggestCompetencyResponseDTO(List.of());
            }

            // Parse the response as SuggestCompetencyResponseDTO
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                SuggestCompetencyResponseDTO result = objectMapper.readValue(responseBody, SuggestCompetencyResponseDTO.class);
                return result;
            }
            catch (Exception parseException) {
                throw new AtlasMLServiceException("Failed to parse AtlasML response", parseException);
            }
        }
        catch (HttpClientErrorException e) {
            throw new AtlasMLServiceException("Failed to suggest competencies due to client error", e);
        }
        catch (HttpServerErrorException e) {
            throw new AtlasMLServiceException("Failed to suggest competencies due to server error", e);
        }
        catch (ResourceAccessException e) {
            throw new AtlasMLServiceException("Failed to suggest competencies due to connection issue", e);
        }
        catch (Exception e) {
            throw new AtlasMLServiceException("Unexpected error while suggesting competencies", e);
        }
    }

    /**
     * Suggest randomly generated competency relations for a course from AtlasML.
     *
     * @param courseId the course identifier
     * @return response DTO containing suggested relations
     */
    public SuggestCompetencyRelationsResponseDTO suggestCompetencyRelations(Long courseId) {
        try {
            log.debug("Requesting competency relation suggestions for courseId: {}", courseId);

            HttpHeaders headers = buildHeadersWithAuth();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = config.getAtlasmlBaseUrl() + String.format(SUGGEST_RELATIONS_ENDPOINT, courseId);
            ResponseEntity<String> response = atlasmlRestTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            String responseBody = response.getBody();

            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(responseBody, SuggestCompetencyRelationsResponseDTO.class);
        }
        catch (HttpClientErrorException e) {
            throw new AtlasMLServiceException("Failed to suggest competency relations due to client error", e);
        }
        catch (HttpServerErrorException e) {
            throw new AtlasMLServiceException("Failed to suggest competency relations due to server error", e);
        }
        catch (ResourceAccessException e) {
            throw new AtlasMLServiceException("Failed to suggest competency relations due to connection issue", e);
        }
        catch (Exception e) {
            throw new AtlasMLServiceException("Unexpected error while suggesting competency relations", e);
        }
    }

    /**
     * Checks if the AtlasML feature is enabled.
     *
     * @param operationName the name of the operation for logging purposes
     * @return true if the feature is enabled, false otherwise
     */
    private boolean isAtlasMLFeatureEnabled(String operationName) {
        if (!featureToggleService.isFeatureEnabled(Feature.AtlasML)) {
            log.debug("AtlasML feature is disabled, skipping {}", operationName);
            return false;
        }
        return true;
    }

    /**
     * Saves competencies based on the provided request.
     *
     * @param request the save request containing competencies and relations
     */
    public void saveCompetencies(SaveCompetencyRequestDTO request) {
        if (!isAtlasMLFeatureEnabled("save operation")) {
            return;
        }

        try {
            String requestId = request.competencies() != null && !request.competencies().isEmpty() ? "competencies[" + request.competencies().size() + "]"
                    : (request.exercise() != null ? request.exercise().id().toString() : "unknown");
            log.debug("Saving competencies for id: {}", requestId);

            HttpHeaders headers = buildHeadersWithAuth();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<SaveCompetencyRequestDTO> entity = new HttpEntity<>(request, headers);

            // Get the raw response as String first to handle any potential response parsing issues
            ResponseEntity<String> response = atlasmlRestTemplate.exchange(config.getAtlasmlBaseUrl() + SAVE_ENDPOINT, HttpMethod.POST, entity, String.class);

            String responseBody = response.getBody();
            log.debug("Received raw response for save request id {}: {}", requestId, responseBody);

            // Check if the response indicates success (empty response or success message)
            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Received successful response for saving competencies for id: {}", requestId);
            }
            else {
                log.warn("Received non-successful response for saving competencies for id {}: {}", requestId, responseBody);
            }
        }
        catch (HttpClientErrorException | HttpServerErrorException e) {
            String requestId = request.competencies() != null && !request.competencies().isEmpty() ? "competencies[" + request.competencies().size() + "]"
                    : (request.exercise() != null ? request.exercise().id().toString() : "unknown");
            log.error("HTTP error while saving request for id {}: {}", requestId, e.getMessage());
            throw new AtlasMLServiceException("Failed to save competencies due to client error", e);
        }
        catch (ResourceAccessException e) {
            String requestId = request.competencies() != null && !request.competencies().isEmpty() ? "competencies[" + request.competencies().size() + "]"
                    : (request.exercise() != null ? request.exercise().id().toString() : "unknown");
            log.error("Connection error while saving request for id {}: {}", requestId, e.getMessage());
            throw new AtlasMLServiceException("Failed to save competencies due to connection issue", e);
        }
        catch (Exception e) {
            String requestId = request.competencies() != null && !request.competencies().isEmpty() ? "competencies[" + request.competencies().size() + "]"
                    : (request.exercise() != null ? request.exercise().id().toString() : "unknown");
            log.error("Unexpected error while saving request for id {}", requestId, e);
            throw new AtlasMLServiceException("Unexpected error while saving competencies", e);
        }
    }

    // Removed single-entity save methods; always use list-based saveCompetencies

    /**
     * Saves multiple competencies using domain objects.
     *
     * @param competencies  the competencies to save
     * @param operationType the operation type (UPDATE or DELETE)
     * @return true if the save operation was successful, false otherwise
     */
    public boolean saveCompetencies(List<Competency> competencies, @NotNull OperationTypeDTO operationType) {
        if (!isAtlasMLFeatureEnabled("competencies save operation")) {
            return true; // Return true to indicate operation was "successful" (not executed due to feature flag)
        }

        if (competencies == null || competencies.isEmpty()) {
            log.debug("No competencies to save, skipping operation");
            return true;
        }

        try {
            SaveCompetencyRequestDTO request = SaveCompetencyRequestDTO.fromCompetencies(competencies, operationType);
            saveCompetencies(request);
            return true;
        }
        catch (Exception e) {
            final String opStr = operationType != null ? operationType.value().toLowerCase() : "update";
            log.error("Failed to {} {} competencies", opStr, competencies.size(), e);
            return false;
        }
    }

    /**
     * Saves an exercise with competencies.
     *
     * @param exerciseId    the exercise identifier
     * @param title         the exercise title
     * @param description   the exercise description
     * @param competencyIds the list of competency IDs associated with the exercise
     * @param courseId      the course identifier
     * @param operationType the operation type (UPDATE or DELETE)
     * @return true if the save operation was successful, false otherwise
     */
    public boolean saveExercise(Long exerciseId, String title, String description, List<Long> competencyIds, Long courseId, @NotNull OperationTypeDTO operationType) {
        if (!isAtlasMLFeatureEnabled("exercise save operation")) {
            return true; // Return true to indicate operation was "successful" (not executed due to feature flag)
        }

        try {
            SaveCompetencyRequestDTO request = SaveCompetencyRequestDTO.fromExercise(exerciseId, title, description, competencyIds, courseId, operationType);
            saveCompetencies(request);
            return true;
        }
        catch (Exception e) {
            final String opStr = operationType != null ? operationType.value().toLowerCase() : "update";
            log.error("Failed to {} exercise with id {}", opStr, exerciseId, e);
            return false;
        }
    }

    /**
     * Saves an exercise with its associated competencies by looking up the exercise-competency relationships.
     *
     * @param exercise      the exercise to save
     * @param operationType the operation type (UPDATE or DELETE)
     * @return true if the save operation was successful, false otherwise
     */
    public boolean saveExerciseWithCompetencies(Exercise exercise, @NotNull OperationTypeDTO operationType) {
        if (!isAtlasMLFeatureEnabled("exercise with competencies save operation")) {
            return true; // Return true to indicate operation was "successful" (not executed due to feature flag)
        }

        try {
            // Get all competency links for this exercise
            List<CompetencyExerciseLink> links = competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exercise.getId());

            // Extract competency IDs
            List<Long> competencyIds = links.stream().map(link -> link.getCompetency().getId()).toList();

            // Build description per exercise type
            String description = exercise.getProblemStatement();
            if (exercise instanceof QuizExercise quizExercise) {
                StringBuilder descriptionBuilder = new StringBuilder();
                if (quizExercise.getQuizQuestions() != null) {
                    for (QuizQuestion question : quizExercise.getQuizQuestions()) {
                        String title = question.getTitle();
                        String text = question.getText();
                        if (title != null && !title.isBlank()) {
                            descriptionBuilder.append(title).append('\n');
                        }
                        if (text != null && !text.isBlank()) {
                            descriptionBuilder.append(text).append("\n\n");
                        }
                    }
                }
                description = descriptionBuilder.toString().trim();
            }
            if (description == null) {
                description = ""; // AtlasML API expects a non-null description
            }

            Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
            return saveExercise(exercise.getId(), exercise.getTitle(), description, competencyIds, courseId, operationType);
        }
        catch (Exception e) {
            log.error("Failed to {} exercise with competencies for exercise id {}", operationType.value().toLowerCase(), exercise.getId(), e);
            return false;
        }
    }

    /**
     * Saves an exercise with its associated competencies using exercise ID lookup.
     *
     * @param exerciseId    the exercise ID
     * @param operationType the operation type (UPDATE or DELETE)
     * @return true if the save operation was successful, false otherwise
     */
    public boolean saveExerciseWithCompetenciesById(Long exerciseId, @NotNull OperationTypeDTO operationType) {
        if (!isAtlasMLFeatureEnabled("exercise with competencies save operation")) {
            return true; // Return true to indicate operation was "successful" (not executed due to feature flag)
        }

        try {
            // Get all competency links for this exercise
            List<CompetencyExerciseLink> links = competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exerciseId);

            if (links.isEmpty()) {
                log.debug("No competency links found for exercise {}, skipping AtlasML notification", exerciseId);
                return true;
            }

            // Extract competency IDs
            List<Long> competencyIds = links.stream().map(link -> link.getCompetency().getId()).toList();

            // Use the first link to get exercise details
            Exercise exercise = links.getFirst().getExercise();

            // Ensure description is never null - use problemStatement or fallback to empty string
            String description = exercise.getProblemStatement();
            if (description == null || description.trim().isEmpty()) {
                description = ""; // AtlasML API expects a non-null description
            }

            Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
            return saveExercise(exerciseId, exercise.getTitle(), description, competencyIds, courseId, operationType);
        }
        catch (Exception e) {
            log.error("Failed to {} exercise with competencies for exercise id {}", operationType.value().toLowerCase(), exerciseId, e);
            return false;
        }
    }

    private HttpHeaders buildHeadersWithAuth() {
        HttpHeaders headers = new HttpHeaders();
        String token = config.getAtlasmlAuthToken();
        if (token != null && !token.isBlank()) {
            String value = token.startsWith("Bearer ") ? token : "Bearer " + token;
            headers.set("Authorization", value);
        }
        return headers;
    }
}
