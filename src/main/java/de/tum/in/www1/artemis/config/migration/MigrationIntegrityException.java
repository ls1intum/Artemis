package de.tum.in.www1.artemis.config.migration;

public class MigrationIntegrityException extends RuntimeException {

    public MigrationIntegrityException() {
        super("The integrity of the migration registry was violated. Check the logs for further information.");
    }
}
