package de.tum.cit.aet.artemis.iris.service.pyris;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.UserRole;

/**
 * The author role of a message as Pyris sees it.
 * <p>
 * Iris needs to tell apart who wrote each message in a thread: a student asking a follow-up, a tutor answering,
 * and its own earlier draft all have to be read differently. {@link UserRole} alone cannot express this, because
 * the Iris bot is persisted as a regular {@link UserRole#USER} and would otherwise be indistinguishable from a
 * student. This class maps to the vocabulary Pyris understands, with the bot resolved first.
 */
public final class PyrisAuthorRole {

    /** A message written by Iris itself (the bot user). */
    public static final String IRIS = "IRIS";

    public static final String INSTRUCTOR = "INSTRUCTOR";

    public static final String TUTOR = "TUTOR";

    public static final String STUDENT = "STUDENT";

    private PyrisAuthorRole() {
    }

    /**
     * Resolves the Pyris-facing author role of a message.
     *
     * @param author the message author, may be {@code null} for system-created postings
     * @param role   the author's role in the course as resolved by Artemis, may be {@code null} if unknown
     * @return {@link #IRIS} for the bot, the mapped course role otherwise, or {@code null} if it could not be resolved
     */
    @Nullable
    public static String of(@Nullable User author, @Nullable UserRole role) {
        if (author != null && author.isBot()) {
            return IRIS;
        }
        if (role == null) {
            return null;
        }
        return switch (role) {
            case INSTRUCTOR -> INSTRUCTOR;
            case TUTOR -> TUTOR;
            case USER -> STUDENT;
        };
    }
}
