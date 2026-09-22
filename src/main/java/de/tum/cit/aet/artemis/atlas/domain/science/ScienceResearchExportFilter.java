package de.tum.cit.aet.artemis.atlas.domain.science;

import java.util.Set;

/**
 * Immutable filter snapshot for a generated science research export.
 */
public record ScienceResearchExportFilter(Set<Long> courseIds, String dateFrom, String dateTo, Set<ScienceEventType> eventTypes) {
}
