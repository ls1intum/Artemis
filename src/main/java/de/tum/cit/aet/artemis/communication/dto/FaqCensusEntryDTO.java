package de.tum.cit.aet.artemis.communication.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What the Iris index holds for one FAQ, as reported by the Pyris FAQ census.
 *
 * @param courseId         the course of the FAQ
 * @param faqId            the FAQ
 * @param ownedRowCount    rows written for this Artemis instance; more than one means duplicates
 * @param legacyRowCount   rows written before FAQ rows carried a base URL, whose owner is unknown; they are replaced when the FAQ is pushed,
 *                             but never deleted as orphans
 * @param ownedFingerprint the content fingerprint of the owned rows, or {@code null} when there are none, they predate fingerprints, or
 *                             they disagree
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqCensusEntryDTO(long courseId, long faqId, int ownedRowCount, int legacyRowCount, @Nullable String ownedFingerprint) {
}
