package de.tum.cit.aet.artemis.iris.service.pyris.dto.competency;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO representing the data required from the client to start a Pyris competency extraction job.
 *
 * @param courseDescription   the course description (might have been edited in the client)
 * @param currentCompetencies the current competencies (might be unsaved in the client)
 */
// @formatter:off
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCompetencyExtractionInputDTO(
        String courseDescription,
        PyrisCompetencyRecommendationDTO[] currentCompetencies
) {}
// @formatter:on
