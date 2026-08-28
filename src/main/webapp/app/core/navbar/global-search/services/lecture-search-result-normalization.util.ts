import { LectureSearchResult, LectureUnitInfo } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { normalizeLectureDeepLinkQueryParams } from 'app/lecture/overview/course-lectures/lecture-deep-link.model';

export function normalizeLectureSearchResultQueryParams(result: LectureSearchResult): LectureSearchResult {
    const lectureUnit = result.lectureUnit;
    const normalizedQueryParams: Record<string, string | number> = {};
    Object.entries(normalizeLectureDeepLinkQueryParams(lectureUnit.queryParams)).forEach(([key, value]) => {
        if (typeof value === 'string' || typeof value === 'number') {
            normalizedQueryParams[key] = value;
        }
    });

    const normalizedLectureUnit: LectureUnitInfo = {
        id: lectureUnit.id,
        name: lectureUnit.name,
        link: lectureUnit.link,
        pageNumber: lectureUnit.pageNumber,
        sourceType: lectureUnit.sourceType,
        queryParams: normalizedQueryParams,
    };
    if (lectureUnit.displayMeta !== undefined) {
        normalizedLectureUnit.displayMeta = lectureUnit.displayMeta;
    }

    const normalizedResult: LectureSearchResult = {
        course: result.course,
        lecture: result.lecture,
        lectureUnit: normalizedLectureUnit,
    };
    if (result.snippet !== undefined) {
        normalizedResult.snippet = result.snippet;
    }

    return normalizedResult;
}
