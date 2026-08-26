import { Params } from '@angular/router';

/**
 * A request to jump to a place inside a lecture unit, e.g. from an Iris citation or a global search result.
 *
 * A deep link is a command, not state: its identity, not its values, says whether it still has to be executed, and
 * every request is a freshly allocated object. Consumers must therefore pass one on as it is — never copy or
 * value-compare it, and never give a signal holding it a custom `equal`, or repeated jumps are swallowed.
 */
export interface LectureDeepLink {
    readonly unitId: number;
    /** Video position in seconds, if the unit has a video. */
    readonly timestamp?: number;
    /** 1-based PDF page, if the unit has a PDF attachment. */
    readonly page?: number;
}

/**
 * Builds a deep link, dropping the parts that cannot be honoured.
 *
 * Every jump goes through here whichever way it reached the app, so a citation with a broken page number is judged like
 * a hand-edited URL. A bad timestamp or page is dropped rather than voiding the link — the unit is still worth opening.
 */
export function lectureDeepLink(unitId: number, timestamp?: number, page?: number): LectureDeepLink | undefined {
    if (!Number.isInteger(unitId) || unitId <= 0) {
        return undefined;
    }

    return {
        unitId,
        timestamp: timestamp !== undefined && Number.isFinite(timestamp) && timestamp >= 0 ? timestamp : undefined,
        page: page !== undefined && Number.isInteger(page) && page > 0 ? page : undefined,
    };
}

/** Reads a deep link out of the lecture page's query parameters, which arrive as strings. */
export function parseLectureDeepLink(params: Params): LectureDeepLink | undefined {
    return lectureDeepLink(Number(params['unit']), Number(params['timestamp']), Number(params['page']));
}

/** Writes a deep link into query parameters, for the one case that needs them: a jump that opens another lecture. */
export function lectureDeepLinkQueryParams(deepLink: LectureDeepLink): Params {
    const params: Params = { unit: deepLink.unitId };
    if (deepLink.timestamp !== undefined) {
        params.timestamp = deepLink.timestamp;
    }
    if (deepLink.page !== undefined) {
        params.page = deepLink.page;
    }

    return params;
}
