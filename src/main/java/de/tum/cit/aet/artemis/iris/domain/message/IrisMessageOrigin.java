package de.tum.cit.aet.artemis.iris.domain.message;

/**
 * Durable provenance tag on an {@link IrisMessage}. {@code null} (the default) means a normal user-initiated
 * message. The only value marks a message produced by the proactive struggle-intervention pipeline, which is
 * excluded from the chat rate limit (spec §5.5, §10).
 */
public enum IrisMessageOrigin {
    PROACTIVE_STRUGGLE
}
