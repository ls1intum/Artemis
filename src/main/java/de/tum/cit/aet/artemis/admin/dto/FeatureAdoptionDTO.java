package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * How many entities have one optional feature switched on.
 * <p>
 * Read together with the call counts. A feature with high adoption and no calls is configured and ignored; one with low
 * adoption and heavy calls from the few who enabled it is worth promoting. Neither is visible from traffic alone.
 *
 * @param module the Artemis module the feature belongs to
 * @param key    the feature, unique within the module
 * @param count  how many entities have it enabled
 * @param total  how many entities of that kind exist
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureAdoptionDTO(String module, String key, long count, long total) {
}
