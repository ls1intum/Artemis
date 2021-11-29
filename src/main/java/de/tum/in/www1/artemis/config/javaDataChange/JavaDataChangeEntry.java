package de.tum.in.www1.artemis.config.javaDataChange;

import java.io.Serializable;

public abstract class JavaDataChangeEntry implements Serializable {

    public abstract void execute();

    // force entries to define an author
    public abstract String author();

    /**
     * Format YYYYMMDD-HHmmss
     * @return Current time in given format
     */
    public abstract String date();
}
