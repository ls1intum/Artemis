package de.tum.cit.aet.artemis.assessment.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.FeatureBonusAward;

/**
 * A feature bonus award as tutors see it while assessing a participation.
 *
 * @param id            the id of the award
 * @param title         the feature the points are awarded for
 * @param justification why the feature is worth the points, if the tutor gave a reason
 * @param points        the awarded points
 * @param createdBy     the login of the tutor who awarded the points
 * @param createdDate   when the points were awarded
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureBonusAwardDTO(long id, String title, String justification, double points, String createdBy, Instant createdDate) {

    /**
     * @param award the stored award
     * @return the award as tutors see it
     */
    public static FeatureBonusAwardDTO of(FeatureBonusAward award) {
        return new FeatureBonusAwardDTO(award.getId(), award.getTitle(), award.getJustification(), award.getPoints(), award.getCreatedBy(), award.getCreatedDate());
    }
}
