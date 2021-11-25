package de.tum.in.www1.artemis.config.javaDataChange;

public abstract class JavaDataChangeEntry {

    public abstract void execute();

    // force entries to define an author
    public abstract String author();
}
