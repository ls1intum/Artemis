package de.tum.cit.aet.artemis.core.config.migration;

/**
 * Thrown by a {@link MigrationEntry} that ran to the end but did not finish everything it set out to do.
 * <p>
 * It says two things at once, and both matter. The entry is <b>not</b> complete, so {@link MigrationService} must not write its changelog row and the entry has to be offered
 * again on the next start. And the application <b>is</b> usable, so startup must not be aborted: {@code MigrationRegistry} listens for {@code ApplicationReadyEvent}, which is
 * published inside {@code SpringApplication.run}, and an exception escaping a listener of it fails the whole start. A node that refuses to come up because one file could not be
 * moved would be far worse than the thing the entry was trying to fix, and because a data problem does not fix itself between two starts, it would be a crash loop rather than a
 * failure.
 * <p>
 * Only an entry that is <b>idempotent</b> may throw this, because being offered again is the whole point. An entry that cannot be run twice has to report a partial run some
 * other way.
 * <p>
 * Anything else an entry throws still aborts startup, unchanged: that is a bug in the entry rather than a condition it recognised and decided to survive.
 */
public class MigrationIncompleteException extends RuntimeException {

    /**
     * @param message what could not be done, and what an operator has to look at
     */
    public MigrationIncompleteException(String message) {
        super(message);
    }
}
