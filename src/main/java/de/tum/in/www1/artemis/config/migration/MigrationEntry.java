package de.tum.in.www1.artemis.config.migration;

import java.io.Serializable;

public abstract class MigrationEntry implements Serializable {

    public abstract void execute();

    /**
     * @return Author of the entry. Either full name or GitHub name.
     */
    public abstract String author();

    /**
     * Format YYYYMMDD_HHmmss
     * @return Current time in given format
     */
    public abstract String date();
}
