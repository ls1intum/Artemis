import { Params } from '@angular/router';

/**
 * A request to jump to a place inside a lecture unit, e.g. from an Iris citation or a global search result.
 *
 * A deep link is a command, not state: clicking the same citation twice has to jump twice, even when the unit is
 * already open at exactly that page or timestamp. Its values therefore say nothing about whether it still has to be
 * executed — {@link requestId} does. Every parsed link gets a fresh id, so consumers react to a new request instead of
 * to a changed value and a repeated jump is never mistaken for "nothing to do".
 */
export interface LectureDeepLink {
    /** Id of the lecture unit to open. */
    readonly unitId: number;
    /** Video position in seconds to seek to, if the unit has a video. */
    readonly timestamp?: number;
    /** 1-based PDF page to show, if the unit has a PDF attachment. */
    readonly page?: number;
    /** Identity of this request. Never reused, so a repeated jump is a new request. */
    readonly requestId: number;
}

/** The query parameters that carry a deep link into the lecture details page. */
export const LECTURE_DEEP_LINK_QUERY_PARAMS = ['unit', 'timestamp', 'page'] as const;

let nextRequestId = 0;

/**
 * Parses the deep link out of the lecture page's query parameters.
 *
 * Values that cannot be honoured (a negative timestamp, a page below one) are dropped rather than making the whole
 * link invalid: the unit itself is still a useful jump target. Returns undefined when the parameters name no unit.
 */
export function parseLectureDeepLink(params: Params): LectureDeepLink | undefined {
    const unitId = Number(params['unit']);
    if (!Number.isInteger(unitId) || unitId <= 0) {
        return undefined;
    }

    const timestamp = Number(params['timestamp']);
    const page = Number(params['page']);

    return {
        unitId,
        timestamp: Number.isFinite(timestamp) && timestamp >= 0 ? timestamp : undefined,
        page: Number.isInteger(page) && page > 0 ? page : undefined,
        requestId: ++nextRequestId,
    };
}
