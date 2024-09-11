package de.tum.cit.aet.artemis.config.migration;

public abstract class MigrationEntry {

    public abstract void execute();

    /**
     * @return Author of the entry. Either full name or GitHub name.
     */
    public abstract String author();

    /**
     * Format YYYYMMDD_HHmmss
     *
     * @return Current time in given format
     */
    public abstract String date();
}
