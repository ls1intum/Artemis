import { Params } from '@angular/router';

export interface LectureDeepLink {
    readonly unitId: number;
    readonly timestamp?: number;
    readonly page?: number;
}

export const LECTURE_DEEP_LINK_NAVIGATION_STATE = { lectureDeepLink: true } as const;

export function isLectureDeepLinkNavigationState(state: unknown): boolean {
    return !!state && typeof state === 'object' && (state as Record<string, unknown>)['lectureDeepLink'] === true;
}

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

export function parseLectureDeepLink(params: Params): LectureDeepLink | undefined {
    return lectureDeepLink(Number(params['unit']), Number(params['timestamp']), Number(params['page']));
}

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
