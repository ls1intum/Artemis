package de.tum.cit.aet.artemis.iris.domain.message;

/**
 * Durable provenance tag on an {@link IrisMessage}. {@code null} (the default) means a normal user-initiated
 * message. The only value marks a message produced by the proactive struggle-intervention pipeline, so it can be
 * included in the chat overview and rendered differently in the history sent to Pyris. It does NOT affect the chat
 * rate limit: a proactive response counts like any other, the same way the legacy proactive triggers (a failed
 * build, a stalled progress trajectory) have always counted.
 */
public enum IrisMessageOrigin {
    PROACTIVE_STRUGGLE
}
