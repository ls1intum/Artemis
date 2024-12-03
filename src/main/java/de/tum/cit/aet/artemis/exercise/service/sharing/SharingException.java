package de.tum.cit.aet.artemis.exercise.service.sharing;

import org.springframework.context.annotation.Profile;

/**
 * Sharing Exception during import or export to sharing platform.
 */
@Profile("sharing")
public class SharingException extends Exception {

    public SharingException(String message) {
        super(message);
    }

}
