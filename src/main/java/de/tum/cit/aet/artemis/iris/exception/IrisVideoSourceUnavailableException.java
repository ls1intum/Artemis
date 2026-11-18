package de.tum.cit.aet.artemis.iris.exception;

/**
 * Preparing an ingestion job failed because the unit's only content is a supported video whose source cannot be resolved right now (a TUM Live stream that
 * is not public, has no recording yet, or a TUM Live that is down). The unit has nothing else to ingest, so the preparation is retried later instead.
 */
public class IrisVideoSourceUnavailableException extends IrisInternalPyrisErrorException {

    public IrisVideoSourceUnavailableException(long lectureUnitId) {
        super("The video of lecture unit " + lectureUnitId + " cannot be resolved right now and the unit has no other content, retrying later");
    }
}
