package de.tum.cit.aet.artemis.hyperion.api.dtos;

/** Releases a shared repository-copy or snapshot reservation on its calling thread. */
public record ParticipationReservation(Runnable release) implements AutoCloseable {

    @Override
    public void close() {
        release.run();
    }
}
