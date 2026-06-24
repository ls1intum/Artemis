package de.tum.cit.aet.artemis.videosource.domain;

/**
 * Lifecycle status of a {@link GocastCourseBinding}.
 * <ul>
 * <li>{@link #PENDING} — the binding has been created by the instructor but the service account has
 * not yet been approved as a course admin on the gocast side (approval link not yet visited).</li>
 * <li>{@link #ACTIVE} — the service account is a confirmed course admin (verified via EP7
 * {@code GetBindingStatus}); streams can be listed and playback tokens requested.</li>
 * <li>{@link #REVOKED} — the binding was explicitly revoked by the instructor; the service account
 * may have been removed as admin on the gocast side.</li>
 * </ul>
 */
public enum GocastBindingStatus {
    PENDING, ACTIVE, REVOKED
}
