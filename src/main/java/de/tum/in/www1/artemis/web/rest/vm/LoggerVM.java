package de.tum.in.www1.artemis.web.rest.vm;

import ch.qos.logback.classic.Logger;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * View Model object for storing a Logback logger.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LoggerVM {

    private String name;

    private String level;

    public LoggerVM(Logger logger) {
        this.name = logger.getName();
        this.level = logger.getEffectiveLevel().toString();
    }

    public LoggerVM() {
        // Empty public constructor used by Jackson.
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    @Override
    public String toString() {
        return "LoggerVM{" + "name='" + name + '\'' + ", level='" + level + '\'' + '}';
    }
}
