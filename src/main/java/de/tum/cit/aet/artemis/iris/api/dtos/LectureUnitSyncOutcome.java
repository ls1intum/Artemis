package de.tum.cit.aet.artemis.iris.api.dtos;

/**
 * What became of an attempt to push a lecture unit's metadata or visibility to Pyris.
 *
 * <p>
 * {@link #NOT_INGESTED} is an answer rather than a failure. Pyris replies 404 for a unit it has never ingested, and no
 * number of retries changes that: only an ingestion does. Telling that apart from a transient error is what lets the
 * caller settle such a unit instead of pushing it once an hour for as long as its course stays active.
 */
public enum LectureUnitSyncOutcome {

    /**
     * Pyris accepted the update.
     */
    DISPATCHED,

    /**
     * Nothing was sent, because the unit is not eligible for Pyris under the current settings.
     */
    SKIPPED,

    /**
     * Pyris does not hold the unit, so it has no metadata or visibility to update until the unit is ingested.
     */
    NOT_INGESTED
}
