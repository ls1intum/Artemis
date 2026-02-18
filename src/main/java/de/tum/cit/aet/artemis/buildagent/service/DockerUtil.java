package de.tum.cit.aet.artemis.buildagent.service;

import java.net.ConnectException;
import java.net.SocketException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class DockerUtil {

    private DockerUtil() {
        // Utility class
    }

    public static boolean isDockerSocketNotAvailable(Throwable throwable) {
        return throwable instanceof SocketException && throwable.getMessage() != null && throwable.getMessage().contains("No such file or directory");
    }

    public static boolean isDockerConnectionRefused(Throwable throwable) {
        return throwable instanceof ConnectException && throwable.getMessage() != null && throwable.getMessage().contains("Connection refused");
    }

    /**
     * Checks whether the given throwable (or any cause in its exception chain)
     * indicates that Docker is not available, e.g. because the Docker socket
     * does not exist or the connection was refused.
     *
     * @param throwable the exception to inspect
     * @return {@code true} if Docker unavailability is detected anywhere in the cause chain
     */
    public static boolean isDockerNotAvailable(Throwable throwable) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        while (current != null && visited.add(current)) {
            if (isDockerSocketNotAvailable(current) || isDockerConnectionRefused(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
