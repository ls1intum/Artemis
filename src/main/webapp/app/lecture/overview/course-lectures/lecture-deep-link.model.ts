import { Params } from '@angular/router';

/**
 * A request to jump to a place inside a lecture unit, e.g. from an Iris citation or a global search result.
 *
 * A deep link is a command, not state: clicking the same citation twice has to jump twice, even when the unit already
 * shows that page or timestamp. Its identity, not its values, says whether it still has to be executed — every request
 * is a freshly allocated object. Consumers therefore have to pass a link on as it is, never copy or value-compare it,
 * and any signal holding one has to keep the default identity equality.
 */
export interface LectureDeepLink {
    /** Id of the lecture unit to open. */
    readonly unitId: number;
    /** Video position in seconds to seek to, if the unit has a video. */
    readonly timestamp?: number;
    /** 1-based PDF page to show, if the unit has a PDF attachment. */
    readonly page?: number;
}

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
    };
}

/**
 * Writes a deep link into the query parameters that carry it into the lecture page, for the one case that needs them:
 * a jump that opens a lecture the user is not on, where the parameters ride along with a real navigation.
 */
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
