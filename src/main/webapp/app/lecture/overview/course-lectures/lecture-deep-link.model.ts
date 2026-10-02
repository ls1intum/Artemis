import { Params } from '@angular/router';

/** A lecture target whose object identity distinguishes repeated requests to the same position. */
export interface LectureDeepLink {
    readonly unitId: number;
    readonly timestamp?: number;
    readonly page?: number;
    readonly combined?: boolean;
}

export const LECTURE_DEEP_LINK_NAVIGATION_STATE = { lectureDeepLink: true } as const;

/** Distinguishes an explicit citation/search request from unrelated query-parameter changes. */
export function isLectureDeepLinkNavigationState(state: unknown): boolean {
    return !!state && typeof state === 'object' && (state as Record<string, unknown>)['lectureDeepLink'] === true;
}

/** Creates a fresh request, rejecting invalid units and omitting invalid media positions. */
export function lectureDeepLink(unitId: number, timestamp?: number, page?: number, combined = false): LectureDeepLink | undefined {
    if (!Number.isInteger(unitId) || unitId <= 0) {
        return undefined;
    }

    return {
        unitId,
        timestamp: timestamp !== undefined && Number.isFinite(timestamp) && timestamp >= 0 ? timestamp : undefined,
        page: page !== undefined && Number.isInteger(page) && page > 0 ? page : undefined,
        combined: combined || undefined,
    };
}

/** Parses route/search parameters, treating blank timestamps as absent while preserving zero. */
export function parseLectureDeepLink(params: Params): LectureDeepLink | undefined {
    const timestamp = params['timestamp'];
    const parsedTimestamp = typeof timestamp === 'number' || (typeof timestamp === 'string' && timestamp.trim() !== '') ? Number(timestamp) : undefined;
    return lectureDeepLink(Number(params['unit']), parsedTimestamp, Number(params['page']), params['combined'] === true || params['combined'] === 'true');
}

/** Serializes a validated request without adding absent media positions to the URL. */
export function lectureDeepLinkQueryParams(deepLink: LectureDeepLink): Params {
    const params: Params = { unit: deepLink.unitId };
    if (deepLink.timestamp !== undefined) {
        params.timestamp = deepLink.timestamp;
    }
    if (deepLink.page !== undefined) {
        params.page = deepLink.page;
    }
    if (deepLink.combined) {
        params.combined = true;
    }

    return params;
}

/** Normalizes a valid lecture target while retaining unrelated query parameters. */
export function normalizeLectureDeepLinkQueryParams(params: Params): Params {
    const deepLink = parseLectureDeepLink(params);
    if (!deepLink) {
        return params;
    }

    const normalizedParams: Params = {};
    Object.entries(params).forEach(([key, value]) => {
        if (key !== 'unit' && key !== 'timestamp' && key !== 'page' && key !== 'combined') {
            normalizedParams[key] = value;
        }
    });

    Object.entries(lectureDeepLinkQueryParams(deepLink)).forEach(([key, value]) => {
        normalizedParams[key] = value;
    });

    return normalizedParams;
}
