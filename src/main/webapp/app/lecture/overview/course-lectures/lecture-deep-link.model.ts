import { Params } from '@angular/router';

/** A request to jump to a place inside a lecture unit, e.g. from an Iris citation or a global search result. */
export interface LectureDeepLink {
    readonly unitId: number;
    /** Video position in seconds, if the unit has a video. */
    readonly timestamp?: number;
    /** 1-based PDF page, if the unit has a PDF attachment. */
    readonly page?: number;
}

/** Builds a deep link, dropping invalid timestamp/page values while keeping a valid unit target. */
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

/** Writes a deep link into lecture query parameters. */
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

/** Normalizes lecture deep-link query parameters while preserving unrelated query parameters. */
export function normalizeLectureDeepLinkQueryParams(params: Params): Params {
    const deepLink = parseLectureDeepLink(params);
    if (!deepLink) {
        return params;
    }

    const normalizedParams: Params = {};
    Object.entries(params).forEach(([key, value]) => {
        if (key !== 'unit' && key !== 'timestamp' && key !== 'page') {
            normalizedParams[key] = value;
        }
    });

    Object.entries(lectureDeepLinkQueryParams(deepLink)).forEach(([key, value]) => {
        normalizedParams[key] = value;
    });

    return normalizedParams;
}
