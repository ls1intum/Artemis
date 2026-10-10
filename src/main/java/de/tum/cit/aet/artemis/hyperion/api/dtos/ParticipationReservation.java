package de.tum.cit.aet.artemis.hyperion.api.dtos;

/** A Hyperion participation slot that its owner must release. */
public record ParticipationReservation(Runnable release) implements AutoCloseable {

    @Override
    public void close() {
        release.run();
    }
}
