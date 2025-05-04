package de.tum.cit.aet.artemis.buildagent.service;

import java.net.SocketException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DockerUtil {

    private static final Logger log = LoggerFactory.getLogger(DockerUtil.class);

    public static boolean isDockerNotAvailable(Exception ex) {
        log.info("Checking if Docker is not available: {} {} \n {}", ex.getCause().getMessage(), ex.getCause().getClass().getCanonicalName(), ex.getCause());
        return ex.getCause() != null && ex.getCause() instanceof SocketException && ex.getCause().getMessage() != null
                && ex.getCause().getMessage().contains("No such file or directory");
    }

}
