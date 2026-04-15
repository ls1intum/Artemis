package de.tum.cit.aet.artemis.videosource.service;

import de.tum.cit.aet.artemis.videosource.domain.VideoSourceType;

/**
 * The result of resolving a raw video source URL: the (possibly transformed) URL and its identified type.
 * <p>
 * Created by {@link VideoSourceResolver}. Shared across consumers so that resolution logic is not duplicated.
 *
 * @param url  the resolved URL (may be the original if no transformation was needed); may be null if the input was null
 * @param type the identified video source type, or {@code null} if the source is unknown
 */
public record ResolvedVideo(String url, VideoSourceType type) {
}
