package de.tum.cit.aet.artemis.deimos.dto;

/**
 * The outcome reported for a participation that was classified.
 * <p>
 * Ordered by precedence: when several apply, the first one wins. A deterministic finding outranks the model, because
 * it does not depend on the model having read the forged content correctly, and an incomplete check only ever replaces
 * {@link #BENIGN}, because a model that flagged a participation despite missing evidence has still flagged it.
 */
public enum DeimosVerdict {

    /**
     * The submission contains text that imitates the markers fencing the untrusted data in the prompt. Detected
     * deterministically over the full snapshot contents, independently of the model and of the payload limits.
     */
    MANIPULATION_ATTEMPT,

    /**
     * The model classified the participation as malicious.
     */
    MALICIOUS,

    /**
     * Part of the evidence was not examined: it was left out to stay within a limit, a submission could not be read, or
     * a binary file or link was added or changed. If the model was asked, it found nothing malicious, but its benign
     * classification only covers what it actually received. A participation that changed nothing but binary files and
     * links receives this verdict without a request to the model.
     */
    INCOMPLETELY_CHECKED,

    /**
     * The model classified the participation as benign and received the complete evidence.
     */
    BENIGN
}
