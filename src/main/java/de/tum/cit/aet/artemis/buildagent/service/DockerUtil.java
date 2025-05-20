package de.tum.cit.aet.artemis.buildagent.service;

import java.net.ConnectException;
import java.net.SocketException;

public final class DockerUtil {

    public static boolean isDockerSocketNotAvailable(Throwable ex) {
        return ex instanceof SocketException && ex.getMessage() != null && ex.getMessage().contains("No such file or directory");
    }

    public static boolean isDockerConnectionRefused(Throwable ex) {
        Throwable cause = ex.getCause();
        return cause instanceof ConnectException && cause.getMessage().contains("Connection refused");
    }

    public static boolean isDockerNotAvailable(Exception ex) {
        if (ex.getCause() == null) {
            return false;
        }
        return isDockerSocketNotAvailable(ex.getCause()) || isDockerConnectionRefused(ex.getCause());
    }

}
