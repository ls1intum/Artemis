import {
    LECTURE_DEEP_LINK_NAVIGATION_STATE,
    isLectureDeepLinkNavigationState,
    lectureDeepLink,
    lectureDeepLinkQueryParams,
    normalizeLectureDeepLinkQueryParams,
    parseLectureDeepLink,
} from './lecture-deep-link.model';

describe('lecture deep links', () => {
    it.each([undefined, null, '', '   ', '\t\n', 'invalid', -1, Infinity, NaN, true, []])('ignores absent or invalid timestamps (%j)', (timestamp) => {
        expect(parseLectureDeepLink({ unit: '7', timestamp, page: '4' })).toEqual({ unitId: 7, timestamp: undefined, page: 4, combined: undefined });
        expect(normalizeLectureDeepLinkQueryParams({ unit: '7', timestamp, page: '4', postId: '5' })).toEqual({ unit: 7, page: 4, postId: '5' });
    });

    it.each([0, '0', ' 0 ', 30.5, '30.5'])('preserves valid timestamps (%j)', (timestamp) => {
        const deepLink = parseLectureDeepLink({ unit: '7', timestamp });
        expect(deepLink?.timestamp).toBe(Number(timestamp));
        expect(lectureDeepLinkQueryParams(deepLink!)).toEqual({ unit: 7, timestamp: Number(timestamp) });
    });

    it.each([0, -1, 1.5, NaN])('rejects an invalid unit id (%j)', (unitId) => {
        expect(lectureDeepLink(unitId)).toBeUndefined();
    });

    it.each([0, -1, 1.5, Infinity])('omits invalid page numbers (%j)', (page) => {
        expect(lectureDeepLinkQueryParams(lectureDeepLink(7, undefined, page)!)).toEqual({ unit: 7 });
    });

    it.each([true, 'true'])('preserves combined-view requests (%j)', (combined) => {
        const deepLink = parseLectureDeepLink({ unit: '7', timestamp: '0', page: '4', combined });
        expect(deepLink).toEqual({ unitId: 7, timestamp: 0, page: 4, combined: true });
        expect(lectureDeepLinkQueryParams(deepLink!)).toEqual({ unit: 7, timestamp: 0, page: 4, combined: true });
    });

    it('leaves query parameters without a valid unit unchanged', () => {
        const params = { unit: 'invalid', timestamp: '', unrelated: 'kept' };
        expect(normalizeLectureDeepLinkQueryParams(params)).toBe(params);
    });

    it('creates a new request identity for repeated targets', () => {
        const params = { unit: '7', page: '4' };
        const first = parseLectureDeepLink(params);
        const second = parseLectureDeepLink(params);
        expect(second).toEqual(first);
        expect(second).not.toBe(first);
    });

    it('recognizes only explicitly marked navigation state', () => {
        expect(isLectureDeepLinkNavigationState(LECTURE_DEEP_LINK_NAVIGATION_STATE)).toBe(true);
        for (const state of [undefined, null, true, 'lectureDeepLink', {}, { lectureDeepLink: false }]) {
            expect(isLectureDeepLinkNavigationState(state)).toBe(false);
        }
    });
});
