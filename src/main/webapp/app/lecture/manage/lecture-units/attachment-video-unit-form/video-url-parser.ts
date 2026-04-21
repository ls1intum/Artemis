/**
 * Video URL parser for YouTube and Vimeo.
 *
 * Replaces the unmaintained `js-video-url-parser` (CVE GHSA-93p6-54v5-593v, a ReDoS in its time-string regexes).
 * Inspired by https://github.com/radiovisual/get-video-id, rewritten in TypeScript and using the native `URL` API
 * to keep parsing linear and avoid regex pitfalls.
 *
 * ReDoS safety: the only regexes used here are `^[A-Za-z0-9_-]{11}$` and `^\d+$`. Both are fully anchored and
 * contain no nested quantifiers or ambiguous alternation, so matching time is linear in the input length. All
 * structural parsing (host, path segments, query params) goes through the browser's native `URL` API, which does
 * not perform user-code backtracking. Do NOT introduce patterns of the form `(X+)+`, `(X|X)+`, or similar when
 * extending this file.
 */

/** Supported video hosting providers. */
export type VideoProvider = 'youtube' | 'vimeo';

/** Parsed representation of a video URL. */
export interface ParsedVideo {
    /** The provider the video is hosted on. */
    provider: VideoProvider;
    /** The canonical video id (YouTube: 11-char shortcode; Vimeo: numeric id as string). */
    id: string;
    /**
     * Vimeo unlisted-video hash (`h=` query parameter on player URLs).
     * Required for playback of unlisted videos, so we capture and preserve it through the embed round-trip.
     * Undefined for public videos and for all YouTube URLs.
     */
    unlistedHash?: string;
}

/**
 * Matches a canonical YouTube video id: exactly 11 URL-safe base64 characters.
 * Anchored + fixed length → no backtracking, ReDoS-safe.
 */
const YOUTUBE_ID_PATTERN = /^[A-Za-z0-9_-]{11}$/;

/**
 * Matches a non-empty all-digits string.
 * Anchored + single unnested quantifier → linear time, ReDoS-safe.
 */
const NUMERIC_PATTERN = /^\d+$/;

/** YouTube watch/embed hosts. `youtube-nocookie.com` is the privacy-preserving variant used by the embed player. */
const YOUTUBE_HOSTS = new Set(['youtube.com', 'www.youtube.com', 'm.youtube.com', 'music.youtube.com', 'gaming.youtube.com', 'youtube-nocookie.com', 'www.youtube-nocookie.com']);

/** Short-link hosts that carry the video id directly as the first path segment (e.g. `https://youtu.be/<id>`). */
const YOUTUBE_SHORT_HOSTS = new Set(['youtu.be', 'y2u.be']);

/**
 * First path segment of YouTube URLs that expose a video id as the second segment.
 * Covers `/watch/<id>`, `/embed/<id>`, `/shorts/<id>`, `/live/<id>`, `/v/<id>`, `/vi/<id>`, `/e/<id>`, `/an_webp/<id>`.
 */
const YOUTUBE_VIDEO_PATH_PREFIXES = new Set(['embed', 'shorts', 'live', 'v', 'vi', 'e', 'an_webp', 'watch']);

/**
 * First path segment of YouTube URLs that are NOT individual videos (channel pages, playlists, search, etc.).
 * These are rejected explicitly so a random 11-char-looking segment in such a path is not mistaken for a video id.
 */
const YOUTUBE_NON_VIDEO_PATH_PREFIXES = new Set(['user', 'c', 'channel', 'playlist', 'results', 'feed']);

/** Vimeo canonical and player hosts. */
const VIMEO_HOSTS = new Set(['vimeo.com', 'www.vimeo.com', 'player.vimeo.com']);

/**
 * Parses a video URL and returns the provider and video id if recognized.
 *
 * Supported YouTube formats:
 *  - `https://www.youtube.com/watch?v=<id>` (and `?vi=<id>`)
 *  - `https://www.youtube.com/embed/<id>` (also on `youtube-nocookie.com`)
 *  - `https://www.youtube.com/shorts/<id>`, `/live/<id>`, `/v/<id>`, `/vi/<id>`, `/e/<id>`, `/an_webp/<id>`
 *  - `https://youtu.be/<id>`, `https://y2u.be/<id>`
 *  - `https://www.youtube.com/attribution_link?u=/watch%3Fv%3D<id>`
 *  - Any of the above with extra query params (`t`, `list`, `feature`, ...), trailing slashes, or `#t=` hashes.
 *
 * Supported Vimeo formats:
 *  - `https://vimeo.com/<numericId>` (optionally followed by `/<unlistedHash>`)
 *  - `https://vimeo.com/channels/<name>/<numericId>`
 *  - `https://vimeo.com/groups/<group>/videos/<numericId>`
 *  - `https://vimeo.com/album/<album>/video/<numericId>`
 *  - `https://vimeo.com/event/<numericId>`
 *  - `https://player.vimeo.com/video/<numericId>` (with optional `?h=<hash>` unlisted token)
 *  - `https://vimeo.com/moogaloop.swf?clip_id=<numericId>`
 *
 * Channel pages, playlists, user handles (`@handle`) and malformed ids are rejected.
 *
 * @param rawUrl The URL to parse. May be undefined for convenience (returns undefined).
 * @returns The parsed video, or undefined if the URL is not a supported video URL.
 */
export function parseVideoUrl(rawUrl: string | undefined): ParsedVideo | undefined {
    if (!rawUrl) {
        return undefined;
    }
    // Delegate protocol/host/path/query parsing to the native URL API — this avoids hand-rolled regex over raw URL strings,
    // which is where the replaced library introduced its ReDoS vulnerability.
    let url: URL;
    try {
        url = new URL(rawUrl);
    } catch {
        return undefined;
    }

    const youtubeId = parseYouTubeId(url);
    if (youtubeId) {
        return { provider: 'youtube', id: youtubeId };
    }
    const vimeoId = parseVimeoId(url);
    if (vimeoId) {
        // Capture the unlisted hash so the embed URL we later build stays playable for unlisted videos.
        const unlistedHash = url.searchParams.get('h') ?? undefined;
        return { provider: 'vimeo', id: vimeoId, ...(unlistedHash ? { unlistedHash } : {}) };
    }
    return undefined;
}

/**
 * Builds an iframe-ready embed URL for a parsed video.
 *
 * Output formats:
 *  - YouTube: `https://www.youtube.com/embed/<id>`
 *  - Vimeo:   `https://player.vimeo.com/video/<id>` (with `?h=<hash>` appended for unlisted videos)
 *
 * These formats are identical to what `js-video-url-parser.create({ format: 'embed' })` produced, so values already
 * persisted in the database (e.g. `https://www.youtube.com/embed/NWNufWyVcT0`) remain byte-identical after re-transform.
 *
 * @param parsed A video previously returned by {@link parseVideoUrl}.
 * @returns The canonical embed URL for the given video.
 */
export function buildEmbedUrl(parsed: ParsedVideo): string {
    switch (parsed.provider) {
        case 'youtube':
            return `https://www.youtube.com/embed/${parsed.id}`;
        case 'vimeo': {
            // Preserve the unlisted-video hash. Without `h=`, embeds of unlisted Vimeo videos return a 403.
            const hashSuffix = parsed.unlistedHash ? `?h=${parsed.unlistedHash}` : '';
            return `https://player.vimeo.com/video/${parsed.id}${hashSuffix}`;
        }
    }
}

/**
 * Splits a URL pathname into non-empty segments, collapsing any leading, trailing, or duplicate slashes.
 * Keeps the parser tolerant of user-pasted URLs with stray `/` characters.
 */
function pathSegments(url: URL): string[] {
    return url.pathname.split('/').filter((segment) => segment.length > 0);
}

/**
 * Extracts the YouTube video id from a URL, or undefined if the URL is not a recognized YouTube video URL.
 * Tries, in order: short-link hosts → `v`/`vi` query params → attribution_link unwrapping → known path prefixes.
 * Rejects channel/playlist/search/handle URLs even if they happen to contain 11-char-looking segments.
 */
function parseYouTubeId(url: URL): string | undefined {
    const host = url.hostname.toLowerCase();

    // Short links: the whole pathname (after stripping slashes) IS the id, e.g. `youtu.be/NWNufWyVcT0`.
    if (YOUTUBE_SHORT_HOSTS.has(host)) {
        const [first] = pathSegments(url);
        return validYouTubeId(first);
    }

    if (!YOUTUBE_HOSTS.has(host)) {
        return undefined;
    }

    // The two canonical query-parameter forms: `?v=<id>` (watch page) and `?vi=<id>` (older inline-player URLs).
    const queryId = url.searchParams.get('v') ?? url.searchParams.get('vi');
    if (queryId) {
        const id = validYouTubeId(queryId);
        if (id) {
            return id;
        }
    }

    // `attribution_link` wraps a real watch URL in a `u` query param (percent-encoded). Unwrap it once and try again.
    if (url.pathname === '/attribution_link') {
        const inner = url.searchParams.get('u');
        if (inner) {
            try {
                // The wrapped value is typically relative (e.g. `/watch?v=<id>`); the base URL is only used to parse it.
                const innerUrl = new URL(inner, 'https://www.youtube.com');
                const innerId = innerUrl.searchParams.get('v');
                if (innerId) {
                    return validYouTubeId(innerId);
                }
            } catch {
                // Ignore malformed attribution targets and fall through to path-based matching below.
            }
        }
    }

    const segments = pathSegments(url);
    if (segments.length === 0) {
        return undefined;
    }

    // Explicitly reject non-video pages. Without this, a path like `/user/<11-char-name>` could be misread as a video id.
    const head = segments[0];
    if (YOUTUBE_NON_VIDEO_PATH_PREFIXES.has(head) || head.startsWith('@')) {
        return undefined;
    }

    // Known `/<prefix>/<id>` forms (embed, shorts, live, v, vi, e, an_webp, watch).
    if (YOUTUBE_VIDEO_PATH_PREFIXES.has(head) && segments.length >= 2) {
        return validYouTubeId(segments[1]);
    }

    return undefined;
}

/**
 * Returns the candidate if it matches the canonical YouTube id shape (11 URL-safe base64 chars), otherwise undefined.
 * Acts as a defensive filter: YouTube IDs are a fixed-length shape, and rejecting anything else prevents us from
 * confidently returning garbage for URLs that happen to have a path/query segment in the right position.
 */
function validYouTubeId(candidate: string | undefined): string | undefined {
    if (!candidate) {
        return undefined;
    }
    return YOUTUBE_ID_PATTERN.test(candidate) ? candidate : undefined;
}

/**
 * Extracts the Vimeo numeric video id from a URL, or undefined if the URL is not a recognized Vimeo video URL.
 * Vimeo video ids are always numeric and appear as a path segment. For compound paths like
 * `/groups/<group>/videos/<id>` or `/album/<album>/video/<id>`, the video id is always the LAST numeric segment,
 * so we scan the path from the end.
 */
function parseVimeoId(url: URL): string | undefined {
    const host = url.hostname.toLowerCase();
    if (!VIMEO_HOSTS.has(host)) {
        return undefined;
    }

    // Scan from the end so compound paths (e.g. `/groups/1234/videos/567890`) return the video id, not an intermediate id.
    const segments = pathSegments(url);
    for (let i = segments.length - 1; i >= 0; i--) {
        if (NUMERIC_PATTERN.test(segments[i])) {
            return segments[i];
        }
    }

    // Legacy Flash-era URL form: `https://vimeo.com/moogaloop.swf?clip_id=<id>`.
    const clipId = url.searchParams.get('clip_id');
    if (clipId && NUMERIC_PATTERN.test(clipId)) {
        return clipId;
    }

    return undefined;
}
