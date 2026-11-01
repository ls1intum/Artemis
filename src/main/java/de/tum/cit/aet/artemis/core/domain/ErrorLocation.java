package de.tum.cit.aet.artemis.core.domain;

/**
 * Where an error happened.
 */
public enum ErrorLocation {

    /** The server logged the error. */
    SERVER,

    /** A client reported the error to the server. */
    CLIENT
}
