package de.tum.in.www1.artemis.web.rest.vm;

import org.springframework.util.ObjectUtils;

import com.fasterxml.jackson.annotation.JsonInclude;

import ch.qos.logback.classic.Logger;

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
    public int hashCode() {
        int result = getName() != null ? getName().hashCode() : 0;
        result = 31 * result + (getLevel() != null ? getLevel().hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "LoggerVM{" + "name='" + name + '\'' + ", level='" + level + '\'' + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof LoggerVM loggerVM)) {
            return false;
        }

        if (!ObjectUtils.nullSafeEquals(getName(), loggerVM.getName())) {
            return false;
        }
        return ObjectUtils.nullSafeEquals(getLevel(), loggerVM.getLevel());
    }
}
