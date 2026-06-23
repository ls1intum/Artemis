package de.tum.cit.aet.artemis.videosource.service;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

/**
 * The result of resolving a raw video source URL: the (possibly transformed) URL, its identified type, and (for YouTube) the extracted video ID.
 * <p>
 * Created by {@link VideoSourceResolverService}. Shared across consumers so that resolution logic is not duplicated.
 *
 * @param url            the resolved URL (may be the original if no transformation was needed); {@code null} if the input was {@code null}
 * @param type           the identified video source type; {@code null} if the source is unknown or input was blank/null
 * @param youtubeVideoId the extracted YouTube video ID; non-null only when {@code type == YOUTUBE}, {@code null} for all other types
 */
public record ResolvedVideo(@Nullable String url, @Nullable VideoSourceType type, @Nullable String youtubeVideoId) {
}
