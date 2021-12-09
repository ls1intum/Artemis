package de.tum.in.www1.artemis.migration;

public class ExitException extends SecurityException {

    public final int status;

    public ExitException(int status) {
        super();
        this.status = status;
    }
}
