package de.tum.in.www1.artemis.migration.entries;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;

public class TestChangeEntry20211215_231800 extends MigrationEntry {

    @Override
    public void execute() {

    }

    /**
     * @return Author of the entry. Either full name or GitHub name.
     */
    @Override
    public String author() {
        return "Some Author";
    }

    /**
     * Format YYYYMMDD_HHmmss
     *
     * @return Current time in given format
     */
    @Override
    public String date() {
        return "20211215_231800";
    }
}
