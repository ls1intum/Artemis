package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.globalsearch.domain.IngestionEventKind;

/**
 * Everything the activity feed renders in one call: the recent events themselves plus the rolling
 * per-kind totals shown as summary tiles above them.
 *
 * @param events             the most recent events, newest first
 * @param countsByKind       how many events of each kind happened inside {@link #summaryWindowHours}
 * @param summaryWindowHours the width of the window {@link #countsByKind} was counted over
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IngestionActivityDTO(List<IngestionEventDTO> events, Map<IngestionEventKind, Long> countsByKind, int summaryWindowHours) {
}
