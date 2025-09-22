package de.tum.cit.aet.artemis.atlas.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRelationsResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SuggestCompetencyResponseDTO;
import de.tum.cit.aet.artemis.atlas.service.atlasml.AtlasMLService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * API for interacting with the AtlasML microservice.
 * Provides methods for saving competencies and exercises to AtlasML for clustering and analysis.
 */
@Profile(PROFILE_CORE)
@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class AtlasMLApi extends AbstractAtlasApi {

    private final AtlasMLService atlasMLService;

    public AtlasMLApi(AtlasMLService atlasMLService) {
        this.atlasMLService = atlasMLService;
    }

    /**
     * Saves multiple competencies to AtlasML.
     *
     * @param competencies  the competencies to save
     * @param operationType the operation type (UPDATE or DELETE)
     * @return true if successful, false otherwise
     */
    public boolean saveCompetencies(List<Competency> competencies, OperationTypeDTO operationType) {
        return atlasMLService.saveCompetencies(competencies, operationType);
    }

    /**
     * Saves an exercise with its associated competencies to AtlasML.
     *
     * @param exercise      the exercise to save
     * @param operationType the operation type (UPDATE or DELETE)
     * @return true if successful, false otherwise
     */
    public boolean saveExerciseWithCompetencies(Exercise exercise, OperationTypeDTO operationType) {
        return atlasMLService.saveExerciseWithCompetencies(exercise, operationType);
    }

    /**
     * Saves an exercise with its associated competencies to AtlasML with UPDATE operation type.
     *
     * @param exercise the exercise to save
     * @return true if successful, false otherwise
     */
    public boolean saveExerciseWithCompetencies(Exercise exercise) {
        return atlasMLService.saveExerciseWithCompetencies(exercise, OperationTypeDTO.UPDATE);
    }

    /**
     * Suggests competencies based on the provided request.
     *
     * @param request the suggestion request containing description and course ID
     * @return the suggested competencies response
     */
    public SuggestCompetencyResponseDTO suggestCompetencies(SuggestCompetencyRequestDTO request) {
        return atlasMLService.suggestCompetencies(request);
    }

    /**
     * Suggests competency relations for a given course.
     *
     * @param courseId the course identifier
     * @return response DTO containing suggested relations
     */
    public SuggestCompetencyRelationsResponseDTO suggestCompetencyRelations(Long courseId) {
        return atlasMLService.suggestCompetencyRelations(courseId);
    }
}
