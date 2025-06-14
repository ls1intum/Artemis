package de.tum.cit.aet.artemis.buildagent.service;

import java.net.ConnectException;
import java.net.SocketException;

public final class DockerUtil {

    public static boolean isDockerSocketNotAvailable(Throwable throwable) {
        return throwable instanceof SocketException && throwable.getMessage() != null && throwable.getMessage().contains("No such file or directory");
    }

    public static boolean isDockerConnectionRefused(Throwable throwable) {
        Throwable cause = throwable.getCause();
        return cause instanceof ConnectException && cause.getMessage().contains("Connection refused");
    }

    public static boolean isDockerNotAvailable(Throwable throwable) {
        if (throwable.getCause() == null) {
            return false;
        }
        return isDockerSocketNotAvailable(throwable.getCause()) || isDockerConnectionRefused(throwable.getCause());
    }

}
