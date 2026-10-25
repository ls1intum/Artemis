package de.tum.cit.aet.artemis.assessment.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The conversion of feature bonus points into a bonus on the final grade a course uses; both values are unset while the course
 * does not use the feature bonus.
 *
 * @param percentPerPoint how many percent of the final grade one point is worth
 * @param maxPercent      the highest bonus in percent
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseFeatureBonusSettingsDTO(@Nullable Double percentPerPoint, @Nullable Double maxPercent) {
}
