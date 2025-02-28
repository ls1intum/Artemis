package de.tum.cit.aet.artemis.buildagent.service;

import java.net.SocketException;

public final class DockerUtil {

    public static boolean isDockerNotAvailable(Exception ex) {
        return ex.getCause() != null && ex.getCause() instanceof SocketException && ex.getCause().getMessage() != null
                && ex.getCause().getMessage().contains("No such file or directory");
    }

}
