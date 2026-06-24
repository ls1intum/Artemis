package de.tum.cit.aet.artemis.videosource.service;

/**
 * Immutable reference to a TUM Live (gocast) stream, carrying the two identifiers
 * needed to call the integration API:
 * <ul>
 * <li>{@code slug} — the course slug (human-readable, used in watch-page URLs and EP1 results)</li>
 * <li>{@code streamId} — the numeric gocast stream ID (required by EP2 {@code GetPlaybackToken})</li>
 * </ul>
 * <p>
 * Instances are produced by {@link TumLiveService#extractStreamRef(String)}, which parses the
 * {@code /w/{slug}/{streamId}} path segment from a TUM Live video URL using the same regex
 * already present in that service.
 * <p>
 * This is a service-layer value type (analogous to {@link ResolvedVideo}) and is intentionally
 * kept in the {@code service} package rather than {@code dto} — it is not directly serialised
 * as a REST response and carries no Jackson annotations.
 *
 * @param slug     the course slug embedded in the TUM Live URL (e.g. {@code "eidi"})
 * @param streamId the numeric stream ID embedded in the TUM Live URL (e.g. {@code 1234})
 */
public record GocastStreamRef(String slug, long streamId) {
}
