package de.tum.cit.aet.artemis.exercise.service.sharing;

import org.springframework.context.annotation.Profile;

@Profile("sharing")
public class SharingException extends Exception {

    public SharingException(String message) {
        super(message);
    }

}
