package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

/**
 * Result of synchronously executing a {@link PyrisCommandDTO}, returned to Pyris so the agent tool knows whether its action actually happened.
 *
 * @param applied whether the command was carried out on the client (e.g. the combined view was still open and the client navigated)
 */
public record PyrisCommandResultDTO(boolean applied) {

    public static PyrisCommandResultDTO success() {
        return new PyrisCommandResultDTO(true);
    }

    public static PyrisCommandResultDTO notApplied() {
        return new PyrisCommandResultDTO(false);
    }
}
