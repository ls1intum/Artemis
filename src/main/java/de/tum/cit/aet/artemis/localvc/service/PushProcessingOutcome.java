package de.tum.cit.aet.artemis.localvc.service;

/**
 * What became of a push to a repository after it was received.
 */
public enum PushProcessingOutcome {

    /**
     * The push was processed as usual: a build was queued or the push needed none.
     */
    PROCESSED,

    /**
     * The push was stored, but it is not built until the start date of the exercise, because a student pushed before it to an exercise that allows repository access before
     * its start date.
     */
    DEFERRED_UNTIL_START_DATE
}
