package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface Completable {

    /**
     * Whether the participant has completed the object
     * @param user
     * @return True if completed, else false
     */
    boolean isCompletedFor(User user);

    /**
     * Get the date when the object has been completed by the participant
     * @param user
     * @return The datetime when the object was first completed or null
     */
    Optional<ZonedDateTime> getCompletionDate(User user);

}
