package de.tum.cit.aet.artemis.atlas.service.atlasml;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
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

import de.tum.cit.aet.artemis.atlas.config.AtlasMLRestTemplateConfiguration;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyResponseDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;

/**
 * Service for communicating with the AtlasML microservice.
 * Provides methods for suggesting and saving competencies.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class AtlasMLService {

    private static final Logger log = LoggerFactory.getLogger(AtlasMLService.class);

    private final RestTemplate atlasmlRestTemplate;

    private final RestTemplate shortTimeoutAtlasmlRestTemplate;

    private final AtlasMLRestTemplateConfiguration config;

    private final CompetencyRepository competencyRepository;

    // API endpoints
    private static final String HEALTH_ENDPOINT = "/api/v1/health/";

    private static final String SUGGEST_ENDPOINT = "/api/v1/competency/suggest";

    private static final String SAVE_ENDPOINT = "/api/v1/competency/save";

    public AtlasMLService(@Qualifier("atlasmlRestTemplate") RestTemplate atlasmlRestTemplate,
            @Qualifier("shortTimeoutAtlasmlRestTemplate") RestTemplate shortTimeoutAtlasmlRestTemplate, AtlasMLRestTemplateConfiguration config,
            CompetencyRepository competencyRepository) {
        this.atlasmlRestTemplate = atlasmlRestTemplate;
        this.shortTimeoutAtlasmlRestTemplate = shortTimeoutAtlasmlRestTemplate;
        this.config = config;
        this.competencyRepository = competencyRepository;
    }

    /**
     * Checks the health status of the AtlasML microservice.
     *
     * @return true if the service is healthy, false otherwise
     */
    public boolean isHealthy() {
        try {
            log.debug("Checking AtlasML health status");
            ResponseEntity<String> response = shortTimeoutAtlasmlRestTemplate.getForEntity(config.getAtlasmlBaseUrl() + HEALTH_ENDPOINT, String.class);

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
            log.debug("Requesting competency suggestions for id: {}", request.id());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<SuggestCompetencyRequestDTO> entity = new HttpEntity<>(request, headers);

            ResponseEntity<SuggestCompetencyResponseDTO> response = atlasmlRestTemplate.exchange(config.getAtlasmlBaseUrl() + SUGGEST_ENDPOINT, HttpMethod.POST, entity,
                    SuggestCompetencyResponseDTO.class);

            log.debug("Received competency suggestions response for id: {}", request.id());
            return response.getBody();
        }
        catch (HttpClientErrorException e) {
            log.error("HTTP client error while suggesting competencies for id {}: {}", request.id(), e.getMessage());
            throw new AtlasMLServiceException("Failed to suggest competencies due to client error", e);
        }
        catch (HttpServerErrorException e) {
            log.error("HTTP server error while suggesting competencies for id {}: {}", request.id(), e.getMessage());
            throw new AtlasMLServiceException("Failed to suggest competencies due to server error", e);
        }
        catch (ResourceAccessException e) {
            log.error("Connection error while suggesting competencies for id {}: {}", request.id(), e.getMessage());
            throw new AtlasMLServiceException("Failed to suggest competencies due to connection issue", e);
        }
        catch (Exception e) {
            log.error("Unexpected error while suggesting competencies for id {}", request.id(), e);
            throw new AtlasMLServiceException("Unexpected error while suggesting competencies", e);
        }
    }

    /**
     * Saves competencies based on the provided request.
     *
     * @param request the save request containing competencies and relations
     */
    public void saveCompetencies(SaveCompetencyRequestDTO request) {
        try {
            log.debug("Saving competencies for id: {}", request.id());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<SaveCompetencyRequestDTO> entity = new HttpEntity<>(request, headers);

            atlasmlRestTemplate.exchange(config.getAtlasmlBaseUrl() + SAVE_ENDPOINT, HttpMethod.POST, entity, Void.class);

            log.debug("Received successful response for saving competencies for id: {}", request.id());
        }
        catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("HTTP error while saving competencies for id {}: {}", request.id(), e.getMessage());
            throw new AtlasMLServiceException("Failed to save competencies due to client error", e);
        }
        catch (ResourceAccessException e) {
            log.error("Connection error while saving competencies for id {}: {}", request.id(), e.getMessage());
            throw new AtlasMLServiceException("Failed to save competencies due to connection issue", e);
        }
        catch (Exception e) {
            log.error("Unexpected error while saving competencies for id {}", request.id(), e);
            throw new AtlasMLServiceException("Unexpected error while saving competencies", e);
        }
    }

    /**
     * Suggests competencies using simplified parameters.
     *
     * @param id          the identifier for the request
     * @param description the description to base suggestions on
     * @return the suggested competency IDs and their relations
     */
    public SuggestCompetencyResponseDTO suggestCompetencies(String id, String description) {
        SuggestCompetencyRequestDTO request = new SuggestCompetencyRequestDTO(id, description);
        return suggestCompetencies(request);
    }

    /**
     * Saves competencies using domain objects.
     *
     * @param id                  the identifier for the request
     * @param description         the description
     * @param competencies        the list of competencies to save
     * @param competencyRelations the list of competency relations to save
     * @return true if the save operation was successful, false otherwise
     */
    public boolean saveCompetencies(String id, String description, List<Competency> competencies, List<CompetencyRelation> competencyRelations) {
        try {
            SaveCompetencyRequestDTO request = SaveCompetencyRequestDTO.fromDomain(id, description, competencies, competencyRelations);
            saveCompetencies(request);
            return true;
        }
        catch (Exception e) {
            log.error("Failed to save competencies with domain objects for id {}", id, e);
            return false;
        }
    }

    /**
     * Suggests competencies and returns them as domain objects.
     *
     * @param id          the identifier for the request
     * @param description the description to base suggestions on
     * @return the suggested competencies as domain objects
     */
    public List<Competency> suggestCompetenciesAsDomain(String id, String description) {
        SuggestCompetencyResponseDTO response = suggestCompetencies(id, description);
        if (response.competencies() == null || response.competencies().isEmpty()) {
            return List.of();
        }

        List<Long> competencyIds = response.competencies().stream().map(Long::parseLong).toList();

        return competencyRepository.findAllById(competencyIds);
    }

    /**
     * Suggests competencies and returns them as domain objects.
     *
     * @param request the suggestion request
     * @return the suggested competencies as domain objects
     */
    public List<Competency> suggestCompetenciesAsDomain(SuggestCompetencyRequestDTO request) {
        return suggestCompetenciesAsDomain(request.id(), request.description());
    }

    /**
     * Suggests competencies with relations.
     *
     * @param id          the identifier for the request
     * @param description the description to base suggestions on
     * @return the competency suggestion result containing competencies and relations
     */
    public CompetencySuggestionResult suggestCompetenciesWithRelations(String id, String description) {
        SuggestCompetencyResponseDTO response = suggestCompetencies(id, description);

        List<Competency> competencies = List.of();
        if (response.competencies() != null && !response.competencies().isEmpty()) {
            List<Long> competencyIds = response.competencies().stream().map(Long::parseLong).toList();
            competencies = competencyRepository.findAllById(competencyIds);
        }

        List<CompetencyRelation> relations = response.toDomainCompetencyRelations();

        return new CompetencySuggestionResult(competencies, relations);
    }

    /**
     * Suggests competency IDs.
     *
     * @param id          the identifier for the request
     * @param description the description to base suggestions on
     * @return the list of suggested competency IDs
     */
    public List<String> suggestCompetencyIds(String id, String description) {
        SuggestCompetencyResponseDTO response = suggestCompetencies(id, description);
        return response.competencies() != null ? response.competencies() : List.of();
    }

    /**
     * Result class for competency suggestions with relations.
     */
    public static class CompetencySuggestionResult {

        private final List<Competency> competencies;

        private final List<CompetencyRelation> relations;

        public CompetencySuggestionResult(List<Competency> competencies, List<CompetencyRelation> relations) {
            this.competencies = competencies;
            this.relations = relations;
        }

        public List<Competency> getCompetencies() {
            return competencies;
        }

        public List<CompetencyRelation> getRelations() {
            return relations;
        }
    }
}
