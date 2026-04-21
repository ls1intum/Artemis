import { describe, expect, it } from 'vitest';
import { buildEmbedUrl, parseVideoUrl } from './video-url-parser';

describe('parseVideoUrl', () => {
    describe('YouTube', () => {
        it.each([
            ['https://www.youtube.com/watch?v=8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://www.youtube.com/watch?vi=8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://m.youtube.com/watch?v=8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://www.youtube-nocookie.com/embed/8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://www.youtube.com/embed/8iU8LPEa4o0?start=10', '8iU8LPEa4o0'],
            ['https://www.youtube.com/shorts/8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://www.youtube.com/live/8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://www.youtube.com/v/8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://www.youtube.com/e/8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://www.youtube.com/an_webp/8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://youtu.be/8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://youtu.be/8iU8LPEa4o0?t=42', '8iU8LPEa4o0'],
            ['https://y2u.be/8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://www.youtube.com/watch?v=8iU8LPEa4o0#t=10s', '8iU8LPEa4o0'],
            ['https://www.youtube.com/watch?v=8iU8LPEa4o0&list=PLabc&feature=share', '8iU8LPEa4o0'],
            // trailing slashes and whitespace tolerance
            ['https://www.youtube.com/embed/8iU8LPEa4o0/', '8iU8LPEa4o0'],
            ['https://youtu.be//8iU8LPEa4o0/', '8iU8LPEa4o0'],
            // case tolerance on path prefix (hosts are always lowercased by URL)
            ['https://www.youtube.com/Embed/8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://www.youtube.com/SHORTS/8iU8LPEa4o0', '8iU8LPEa4o0'],
            // case tolerance on hosts
            ['https://YOUTUBE.COM/watch?v=8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://YouTu.Be/8iU8LPEa4o0', '8iU8LPEa4o0'],
            // explicit default port
            ['https://www.youtube.com:443/watch?v=8iU8LPEa4o0', '8iU8LPEa4o0'],
            // plain http also accepted (old library was protocol-agnostic)
            ['http://www.youtube.com/watch?v=8iU8LPEa4o0', '8iU8LPEa4o0'],
        ])('parses %s', (url, expectedId) => {
            expect(parseVideoUrl(url)).toEqual({ provider: 'youtube', id: expectedId });
        });

        it('parses attribution_link URLs whose target stays on a YouTube host', () => {
            const url = 'https://www.youtube.com/attribution_link?a=xyz&u=%2Fwatch%3Fv%3D8iU8LPEa4o0%26feature%3Dshare';
            expect(parseVideoUrl(url)).toEqual({ provider: 'youtube', id: '8iU8LPEa4o0' });
        });

        it('rejects attribution_link targets pointing to a non-YouTube host', () => {
            // An attacker-crafted absolute `u` must not be trusted to provide a YouTube id, even though the id
            // would be harmless on its own. Enforces the host check in parseAttributionLinkTarget.
            const url = 'https://www.youtube.com/attribution_link?u=https%3A%2F%2Fevil.example%2Fwatch%3Fv%3D8iU8LPEa4o0';
            expect(parseVideoUrl(url)).toBeUndefined();
        });

        it.each([
            // non-video pages
            'https://www.youtube.com/user/somebody',
            'https://www.youtube.com/c/somebody',
            'https://www.youtube.com/channel/UCabc',
            'https://www.youtube.com/@handle',
            'https://www.youtube.com/results?search_query=foo',
            'https://www.youtube.com/playlist?list=PLabc',
            // unsupported hosts (no such YouTube subdomains in the accepted set)
            'https://music.youtube.com/watch?v=8iU8LPEa4o0',
            'https://gaming.youtube.com/watch?v=8iU8LPEa4o0',
            // `/watch/<id>` is NOT a canonical YouTube format and is not recognized by the server parser;
            // keep client/server in sync by rejecting it here.
            'https://www.youtube.com/watch/8iU8LPEa4o0',
            // empty `v` param
            'https://www.youtube.com/watch?v=',
        ])('rejects non-video or unsupported URL %s', (url) => {
            expect(parseVideoUrl(url)).toBeUndefined();
        });

        it('rejects malformed ids', () => {
            expect(parseVideoUrl('https://www.youtube.com/watch?v=tooShort')).toBeUndefined();
            expect(parseVideoUrl('https://youtu.be/way-too-long-to-be-valid')).toBeUndefined();
        });
    });

    describe('Vimeo', () => {
        it.each([
            ['https://vimeo.com/123456789', '123456789'],
            ['https://www.vimeo.com/123456789', '123456789'],
            ['https://vimeo.com/channels/staffpicks/123456789', '123456789'],
            ['https://vimeo.com/groups/someName/videos/123456789', '123456789'],
            ['https://vimeo.com/album/1234/video/123456789', '123456789'],
            ['https://vimeo.com/event/123456789', '123456789'],
            ['https://player.vimeo.com/video/123456789', '123456789'],
            ['https://player.vimeo.com/video/123456789?autoplay=1', '123456789'],
            ['https://player.vimeo.com/video/123456789/', '123456789'],
            ['https://vimeo.com/moogaloop.swf?clip_id=123456789', '123456789'],
            // case tolerance on host and path
            ['https://VIMEO.com/123456789', '123456789'],
            ['https://Player.Vimeo.com/Video/123456789', '123456789'],
        ])('parses %s', (url, expectedId) => {
            expect(parseVideoUrl(url)).toEqual({ provider: 'vimeo', id: expectedId });
        });

        it.each([
            // Landing pages with a numeric slug but no trailing video segment. The old implementation's
            // last-numeric-segment heuristic returned a wrong id here; the whitelist must reject them.
            'https://vimeo.com/channels/12345',
            'https://vimeo.com/groups/12345',
            'https://vimeo.com/album/12345',
            'https://vimeo.com/ondemand/12345',
            'https://vimeo.com/staffpicks',
        ])('rejects non-video Vimeo URL %s', (url) => {
            expect(parseVideoUrl(url)).toBeUndefined();
        });

        it('captures the unlisted video hash from the `h` query param', () => {
            expect(parseVideoUrl('https://player.vimeo.com/video/228795592?h=27bef101ce')).toEqual({
                provider: 'vimeo',
                id: '228795592',
                unlistedHash: '27bef101ce',
            });
        });

        it('captures the unlisted video hash from the trailing path segment (legacy share form)', () => {
            expect(parseVideoUrl('https://vimeo.com/228795592/27bef101ce')).toEqual({
                provider: 'vimeo',
                id: '228795592',
                unlistedHash: '27bef101ce',
            });
        });

        it('rejects hashes that contain non-alphanumerics (defense-in-depth for embed-URL interpolation)', () => {
            // `URLSearchParams.get` returns the decoded value, so `?h=abc%26x=1` yields `abc&x=1`. The hash validator
            // must reject this so we never splice attacker-controlled `&x=...` into the embed URL we build.
            const parsed = parseVideoUrl('https://player.vimeo.com/video/228795592?h=abc%26x%3D1');
            expect(parsed).toEqual({ provider: 'vimeo', id: '228795592' });
            expect(parsed?.unlistedHash).toBeUndefined();
        });
    });

    describe('unsupported / invalid', () => {
        it.each(['', 'not-a-url', 'https://example.com/video', 'https://dailymotion.com/video/x123', 'https://twitch.tv/videos/123'])('returns undefined for %s', (url) => {
            expect(parseVideoUrl(url)).toBeUndefined();
        });

        it('returns undefined for undefined input', () => {
            expect(parseVideoUrl(undefined)).toBeUndefined();
        });
    });
});

describe('parseVideoUrl ReDoS safety', () => {
    // The replaced `js-video-url-parser` library has GHSA-8fgx-wgvr-pcx8, a regex catastrophic-backtracking
    // bug where a crafted input caused exponential match time. These tests feed adversarial inputs of the same shapes
    // and require each call to complete well within a second. The budget here exists to catch *catastrophic*
    // backtracking (seconds to minutes), not to micro-benchmark linear-time parsing — it is deliberately generous
    // to stay reliable on loaded CI runners while still flagging any accidental re-introduction of a ReDoS.
    const MAX_PARSE_MS = 1000;

    const adversarial: Array<[string, string]> = [
        ['long digit-letter alternation (mirrors the original CVE payload shape)', 'https://www.youtube.com/watch?v=' + '1s'.repeat(5000) + '!'],
        ['long colon-separated digits (mirrors the original CVE payload shape)', 'https://www.youtube.com/watch?v=' + '1:'.repeat(5000) + '!'],
        ['pathological path depth', 'https://www.youtube.com/' + 'a/'.repeat(5000) + 'x'],
        ['pathological query-string length', 'https://www.youtube.com/watch?v=' + 'a'.repeat(50000)],
    ];

    it.each(adversarial)('finishes in linear time for %s', (_label, url) => {
        const start = performance.now();
        parseVideoUrl(url);
        const elapsed = performance.now() - start;
        expect(elapsed).toBeLessThan(MAX_PARSE_MS);
    });
});

describe('buildEmbedUrl', () => {
    it('builds the YouTube embed URL', () => {
        expect(buildEmbedUrl({ provider: 'youtube', id: '8iU8LPEa4o0' })).toBe('https://www.youtube.com/embed/8iU8LPEa4o0');
    });

    it('builds the Vimeo embed URL without a hash for public videos', () => {
        expect(buildEmbedUrl({ provider: 'vimeo', id: '123456789' })).toBe('https://player.vimeo.com/video/123456789');
    });

    it('appends the unlisted hash for Vimeo unlisted videos', () => {
        expect(buildEmbedUrl({ provider: 'vimeo', id: '228795592', unlistedHash: '27bef101ce' })).toBe('https://player.vimeo.com/video/228795592?h=27bef101ce');
    });

    it('round-trips a Vimeo path-form unlisted URL to the canonical player URL with `h=`', () => {
        const parsed = parseVideoUrl('https://vimeo.com/228795592/27bef101ce');
        expect(parsed).toBeDefined();
        expect(buildEmbedUrl(parsed!)).toBe('https://player.vimeo.com/video/228795592?h=27bef101ce');
    });
});
