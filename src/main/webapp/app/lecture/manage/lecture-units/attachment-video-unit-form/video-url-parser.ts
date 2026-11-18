/**
 * Video URL parser for YouTube and Vimeo.
 *
 * Replaces the unmaintained `js-video-url-parser` (GHSA-8fgx-wgvr-pcx8, a ReDoS in its time-string regexes,
 * with no upstream fix released as of 0.5.1).
 * Inspired by https://github.com/radiovisual/get-video-id, rewritten in TypeScript and using the native `URL` API
 * to keep parsing linear and avoid regex pitfalls.
 *
 * ReDoS safety: the only regexes used here are `^[A-Za-z0-9_-]{11}$`, `^\d+$`, and `^[A-Za-z0-9]+$`. All are fully
 * anchored and contain no nested quantifiers or ambiguous alternation, so matching time is linear in the input
 * length. All structural parsing (host, path segments, query params) goes through the browser's native `URL` API,
 * which does not perform user-code backtracking. Do NOT introduce patterns of the form `(X+)+`, `(X|X)+`, or
 * similar when extending this file.
 *
 * Client / server contract: the Java `YouTubeUrlService` on the server independently extracts the YouTube video
 * id from the persisted `videoSource`. The YouTube host list below MUST remain a subset of the server's
 * accepted-host set, and `buildEmbedUrl` MUST produce `https://www.youtube.com/embed/<id>` so the server parser
 * can round-trip. If you add a host here, add it to `YouTubeUrlService.YOUTUBE_HOSTS` in the same PR.
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
     * Vimeo unlisted-video hash. Appears as the `h=` query parameter on player URLs, or as a trailing path segment
     * on bare vimeo.com URLs (`vimeo.com/<id>/<hash>`). Required for playback of unlisted videos, so we capture and
     * preserve it through the embed round-trip. Undefined for public videos and for all YouTube URLs.
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

/**
 * Matches a Vimeo unlisted-video hash: lowercase alphanumerics only.
 * Used to reject anything that isn't obviously a Vimeo hash before embedding it unescaped into a URL.
 * Anchored + single unnested quantifier → linear time, ReDoS-safe.
 */
const VIMEO_HASH_PATTERN = /^[A-Za-z0-9]+$/;

/**
 * YouTube hosts we accept. This list MUST remain a subset of the server-side `YouTubeUrlService.YOUTUBE_HOSTS`
 * (Java); otherwise the client would validate URLs that the viewer's `youtubeVideoId` extraction silently rejects.
 * `music.youtube.com` and `gaming.youtube.com` are intentionally excluded.
 */
const YOUTUBE_HOSTS = new Set(['youtube.com', 'www.youtube.com', 'm.youtube.com', 'youtube-nocookie.com', 'www.youtube-nocookie.com']);

/** Short-link hosts that carry the video id directly as the first path segment (e.g. `https://youtu.be/<id>`). */
const YOUTUBE_SHORT_HOSTS = new Set(['youtu.be', 'y2u.be']);

/**
 * First path segment of YouTube URLs that expose a video id as the second segment.
 * Covers `/embed/<id>`, `/shorts/<id>`, `/live/<id>`, `/v/<id>`, `/vi/<id>`, `/e/<id>`, `/an_webp/<id>`.
 *
 * Note: `/watch/<id>` is NOT included. The canonical watch URL is `/watch?v=<id>` (handled by the query-param
 * lookup), and the server parser does not recognize the `/watch/<id>` shape, so accepting it on the client would
 * create a helper/viewer mismatch.
 */
const YOUTUBE_VIDEO_PATH_PREFIXES = new Set(['embed', 'shorts', 'live', 'v', 'vi', 'e', 'an_webp']);

/** Vimeo canonical and player hosts. */
const VIMEO_HOSTS = new Set(['vimeo.com', 'www.vimeo.com', 'player.vimeo.com']);

/**
 * Parses a video URL and returns the provider, video id, and (for unlisted Vimeo videos) unlisted-hash.
 *
 * Supported YouTube formats:
 *  - `https://www.youtube.com/watch?v=<id>` (and `?vi=<id>`)
 *  - `https://www.youtube.com/embed/<id>` (also on `youtube-nocookie.com`)
 *  - `https://www.youtube.com/shorts/<id>`, `/live/<id>`, `/v/<id>`, `/vi/<id>`, `/e/<id>`, `/an_webp/<id>`
 *  - `https://youtu.be/<id>`, `https://y2u.be/<id>`
 *  - `https://www.youtube.com/attribution_link?u=/watch%3Fv%3D<id>` (the wrapped target must itself be on a YouTube host)
 *  - Any of the above with extra query params (`t`, `list`, `feature`, ...), trailing slashes, or `#t=` hashes.
 *
 * Supported Vimeo formats:
 *  - `https://vimeo.com/<numericId>` (optionally followed by `/<unlistedHash>` as a path segment)
 *  - `https://vimeo.com/channels/<name>/<numericId>`
 *  - `https://vimeo.com/groups/<group>/videos/<numericId>`
 *  - `https://vimeo.com/album/<album>/video/<numericId>`
 *  - `https://player.vimeo.com/video/<numericId>` (with optional `?h=<hash>` unlisted token)
 *  - `https://vimeo.com/moogaloop.swf?clip_id=<numericId>`
 *
 * Channel pages, playlists, group/album landing pages (no trailing video segment), user handles (`@handle`),
 * and malformed ids are rejected.
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
    const vimeo = parseVimeo(url);
    if (vimeo) {
        // Explicit field construction (no object spread) per the project's TypeScript guidelines.
        return vimeo.unlistedHash ? { provider: 'vimeo', id: vimeo.id, unlistedHash: vimeo.unlistedHash } : { provider: 'vimeo', id: vimeo.id };
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
            // We restrict the hash to `[A-Za-z0-9]+` in the parser, so direct interpolation here is safe.
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
    // `searchParams.get` returns the first value — multiple `v=`s picks the first, which matches browser behavior.
    const queryId = url.searchParams.get('v') ?? url.searchParams.get('vi');
    if (queryId) {
        const id = validYouTubeId(queryId);
        if (id) {
            return id;
        }
    }

    // `attribution_link` wraps a real watch URL in a `u` query param (percent-encoded). Unwrap it once and try again.
    if (url.pathname === '/attribution_link') {
        const unwrapped = parseAttributionLinkTarget(url);
        if (unwrapped) {
            return unwrapped;
        }
    }

    const segments = pathSegments(url);
    if (segments.length === 0) {
        return undefined;
    }

    // Lowercase the prefix for case-insensitive matching. Hostnames are already lowercased by URL,
    // but path segments aren't — be tolerant of e.g. `/Embed/<id>` from hand-edited URLs.
    const head = segments[0].toLowerCase();

    // Reject non-video pages explicitly. Without this, a path like `/user/<11-char-name>` could be misread as a video id.
    // The list is not exhaustive; the whitelist below is the primary defense.
    if (head === 'user' || head === 'c' || head === 'channel' || head === 'playlist' || head === 'results' || head === 'feed' || head.startsWith('@')) {
        return undefined;
    }

    // Known `/<prefix>/<id>` forms (embed, shorts, live, v, vi, e, an_webp).
    if (YOUTUBE_VIDEO_PATH_PREFIXES.has(head) && segments.length >= 2) {
        return validYouTubeId(segments[1]);
    }

    return undefined;
}

/**
 * Unwraps a YouTube `/attribution_link?u=...` URL. The `u` parameter carries a relative path like
 * `/watch?v=<id>`; we resolve it against youtube.com, then confirm the resolved URL stays on a YouTube host
 * before extracting the id. This prevents an attacker-supplied absolute `u=https://evil.com/watch?v=<id>` from
 * contributing an id to our pipeline.
 */
function parseAttributionLinkTarget(url: URL): string | undefined {
    const inner = url.searchParams.get('u');
    if (!inner) {
        return undefined;
    }
    try {
        // The wrapped value is typically relative (e.g. `/watch?v=<id>`); the base URL handles that case and also
        // surfaces the host for absolute URLs so we can re-check it.
        const innerUrl = new URL(inner, 'https://www.youtube.com');
        if (!YOUTUBE_HOSTS.has(innerUrl.hostname.toLowerCase())) {
            return undefined;
        }
        const innerId = innerUrl.searchParams.get('v');
        return innerId ? validYouTubeId(innerId) : undefined;
    } catch {
        // Ignore malformed attribution targets.
        return undefined;
    }
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
 * Extracts the Vimeo numeric video id (and optional unlisted-hash) from a URL.
 * Uses a path-shape whitelist rather than a "last numeric segment" scan, because the latter would return spurious
 * ids for landing pages like `/channels/<numericSlug>` or `/ondemand/<numericSlug>`.
 */
function parseVimeo(url: URL): { id: string; unlistedHash?: string } | undefined {
    const host = url.hostname.toLowerCase();
    if (!VIMEO_HOSTS.has(host)) {
        return undefined;
    }

    const segments = pathSegments(url);
    const queryHash = normalizeHash(url.searchParams.get('h'));

    // player.vimeo.com: the canonical embed shape is /video/<id>, and the hash travels in the `h=` query param.
    if (host === 'player.vimeo.com') {
        if (segments.length >= 2 && segments[0].toLowerCase() === 'video' && NUMERIC_PATTERN.test(segments[1])) {
            return withHash(segments[1], queryHash);
        }
        return undefined;
    }

    // vimeo.com / www.vimeo.com — whitelist each accepted path shape explicitly.
    if (segments.length === 0) {
        return undefined;
    }
    const head = segments[0].toLowerCase();

    // /<id>   or   /<id>/<unlistedHash>
    if (NUMERIC_PATTERN.test(segments[0])) {
        const pathHash = segments.length >= 2 ? normalizeHash(segments[1]) : undefined;
        return withHash(segments[0], queryHash ?? pathHash);
    }

    // /channels/<name>/<id>
    if (head === 'channels' && segments.length >= 3 && NUMERIC_PATTERN.test(segments[2])) {
        return withHash(segments[2], queryHash);
    }

    // /groups/<name>/videos/<id>
    if (head === 'groups' && segments.length >= 4 && segments[2].toLowerCase() === 'videos' && NUMERIC_PATTERN.test(segments[3])) {
        return withHash(segments[3], queryHash);
    }

    // /album/<album>/video/<id>
    if (head === 'album' && segments.length >= 4 && segments[2].toLowerCase() === 'video' && NUMERIC_PATTERN.test(segments[3])) {
        return withHash(segments[3], queryHash);
    }

    // Note: `/event/<id>` is intentionally NOT accepted. Vimeo live events use a separate embedding
    // mechanism (event-specific iframe from the event dashboard / Live API); `player.vimeo.com/video/<id>`
    // with an event id fails to embed. Users who need to embed a live event should paste the dashboard-
    // provided iframe URL directly into `videoSource`.

    // Legacy Flash-era URL form: `https://vimeo.com/moogaloop.swf?clip_id=<id>`.
    if (head === 'moogaloop.swf') {
        const clipId = url.searchParams.get('clip_id');
        if (clipId && NUMERIC_PATTERN.test(clipId)) {
            return withHash(clipId, queryHash);
        }
    }

    return undefined;
}

/**
 * Validates a Vimeo hash candidate. Returns the candidate if it matches `[A-Za-z0-9]+`, otherwise undefined.
 * This keeps `buildEmbedUrl` safe to interpolate without percent-encoding, and rejects anything that clearly
 * isn't a Vimeo unlisted-hash (e.g. percent-decoded query-string fragments like `abc&x=1`).
 */
function normalizeHash(candidate: string | null | undefined): string | undefined {
    if (!candidate) {
        return undefined;
    }
    return VIMEO_HASH_PATTERN.test(candidate) ? candidate : undefined;
}

function withHash(id: string, unlistedHash: string | undefined): { id: string; unlistedHash?: string } {
    return unlistedHash ? { id, unlistedHash } : { id };
}
