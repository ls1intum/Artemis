package de.tum.cit.aet.artemis.exercise.dto;

/**
 * Controls how images are delivered in the rendered problem statement.
 * <ul>
 * <li>{@link #INLINE} embeds images as Base64 data URIs directly in the HTML (self-contained, works without auth).</li>
 * <li>{@link #URL} keeps images as absolute URLs that require authentication to load (smallest response size).</li>
 * </ul>
 */
public enum ImageMode {
    INLINE, URL
}
