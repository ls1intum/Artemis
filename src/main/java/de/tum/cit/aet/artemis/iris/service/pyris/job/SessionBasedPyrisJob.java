package de.tum.cit.aet.artemis.iris.service.pyris.job;

/**
 * An interface Pyris job that is associated with a session.
 */
public interface SessionBasedPyrisJob extends PyrisJob {

    long sessionId();
}
