import { buildEmbedUrl, parseVideoUrl } from './video-url-parser';

describe('parseVideoUrl', () => {
    describe('YouTube', () => {
        it.each([
            ['https://www.youtube.com/watch?v=8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://www.youtube.com/watch?vi=8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://m.youtube.com/watch?v=8iU8LPEa4o0', '8iU8LPEa4o0'],
            ['https://music.youtube.com/watch?v=8iU8LPEa4o0&list=RDMM', '8iU8LPEa4o0'],
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
            // trailing slashes
            ['https://www.youtube.com/embed/8iU8LPEa4o0/', '8iU8LPEa4o0'],
            ['https://youtu.be//8iU8LPEa4o0/', '8iU8LPEa4o0'],
        ])('parses %s', (url, expectedId) => {
            expect(parseVideoUrl(url)).toEqual({ provider: 'youtube', id: expectedId });
        });

        it('parses attribution_link URLs', () => {
            const url = 'https://www.youtube.com/attribution_link?a=xyz&u=%2Fwatch%3Fv%3D8iU8LPEa4o0%26feature%3Dshare';
            expect(parseVideoUrl(url)).toEqual({ provider: 'youtube', id: '8iU8LPEa4o0' });
        });

        it.each([
            'https://www.youtube.com/user/somebody',
            'https://www.youtube.com/c/somebody',
            'https://www.youtube.com/channel/UCabc',
            'https://www.youtube.com/@handle',
            'https://www.youtube.com/results?search_query=foo',
            'https://www.youtube.com/playlist?list=PLabc',
        ])('rejects non-video path %s', (url) => {
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
            ['https://vimeo.com/123456789/abcdef1234', '123456789'],
            ['https://vimeo.com/channels/staffpicks/123456789', '123456789'],
            ['https://vimeo.com/groups/1234/videos/123456789', '123456789'],
            ['https://vimeo.com/album/1234/video/123456789', '123456789'],
            ['https://vimeo.com/event/123456789', '123456789'],
            ['https://player.vimeo.com/video/123456789', '123456789'],
            ['https://player.vimeo.com/video/123456789?autoplay=1', '123456789'],
            ['https://player.vimeo.com/video/123456789/', '123456789'],
            ['https://vimeo.com/moogaloop.swf?clip_id=123456789', '123456789'],
        ])('parses %s', (url, expectedId) => {
            expect(parseVideoUrl(url)).toEqual({ provider: 'vimeo', id: expectedId });
        });

        it('rejects non-numeric vimeo paths', () => {
            expect(parseVideoUrl('https://vimeo.com/staffpicks')).toBeUndefined();
        });

        it('captures the unlisted video hash from the `h` query param', () => {
            expect(parseVideoUrl('https://player.vimeo.com/video/228795592?h=27bef101ce')).toEqual({
                provider: 'vimeo',
                id: '228795592',
                unlistedHash: '27bef101ce',
            });
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
    // The replaced `js-video-url-parser` library was patched for CVE GHSA-93p6-54v5-593v, a regex catastrophic-backtracking
    // bug where a crafted input caused exponential match time. These tests feed adversarial inputs of the same shapes and
    // require each call to complete in well under a second, guarding against any future regex that accidentally regresses.
    const MAX_PARSE_MS = 100;

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

    it('builds the Vimeo embed URL', () => {
        expect(buildEmbedUrl({ provider: 'vimeo', id: '123456789' })).toBe('https://player.vimeo.com/video/123456789');
    });
});
