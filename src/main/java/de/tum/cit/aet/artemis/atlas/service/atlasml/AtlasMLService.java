package de.tum.cit.aet.artemis.atlas.service.atlasml;

import java.util.List;

import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyResponseDTO;

/**
 * Service interface for communicating with the AtlasML microservice.
 * Provides methods for suggesting and saving competencies.
 */
public interface AtlasMLService {

    /**
     * Checks the health status of the AtlasML microservice.
     *
     * @return true if the service is healthy, false otherwise
     */
    boolean isHealthy();

    /**
     * Suggests competencies based on the provided request.
     *
     * @param request the suggestion request containing id and description
     * @return the suggested competency IDs and their relations
     */
    SuggestCompetencyResponseDTO suggestCompetencies(SuggestCompetencyRequestDTO request);

    /**
     * Saves competencies based on the provided request.
     *
     * @param request the save request containing competencies and relations
     * @return true if the save operation was successful, false otherwise
     */
    void saveCompetencies(SaveCompetencyRequestDTO request);

    /**
     * Suggests competencies using simplified parameters.
     *
     * @param id          the identifier for the request
     * @param description the description to base suggestions on
     * @return the suggested competency IDs and their relations
     */
    SuggestCompetencyResponseDTO suggestCompetencies(String id, String description);

    /**
     * Saves competencies using simplified parameters.
     *
     * @param id                  the identifier for the request
     * @param description         the description
     * @param competencies        the list of competencies to save
     * @param competencyRelations the list of competency relations to save
     * @return true if the save operation was successful, false otherwise
     */
    boolean saveCompetencies(String id, String description, List<Competency> competencies, List<CompetencyRelation> competencyRelations);

    /**
     * Suggests competencies and returns the full domain objects fetched from the database.
     *
     * @param id          the identifier for the request
     * @param description the description to base suggestions on
     * @return a list of full Competency objects
     */
    List<Competency> suggestCompetenciesAsDomain(String id, String description);

    /**
     * Suggests competencies and returns the full domain objects fetched from the database.
     *
     * @param request the suggestion request
     * @return a list of full Competency objects
     */
    List<Competency> suggestCompetenciesAsDomain(SuggestCompetencyRequestDTO request);

    /**
     * Suggests competencies and returns both competencies and relations as domain objects.
     *
     * @param id          the identifier for the request
     * @param description the description to base suggestions on
     * @return a pair containing competencies and relations as domain objects
     */
    CompetencySuggestionResult suggestCompetenciesWithRelations(String id, String description);

    /**
     * Suggests competencies and returns the raw ID responses.
     *
     * @param id          the identifier for the request
     * @param description the description to base suggestions on
     * @return the suggested competency IDs
     */
    List<String> suggestCompetencyIds(String id, String description);

    /**
     * Result class for competency suggestions with relations.
     */
    class CompetencySuggestionResult {

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
