package de.tum.cit.aet.artemis.atlas.domain.science;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Immutable filter snapshot for a generated science research export, stored in a json column of the export audit.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScienceResearchExportFilter(Set<Long> courseIds, String dateFrom, String dateTo, Set<ScienceEventType> eventTypes) {
}
