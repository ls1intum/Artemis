import { GlobalSearchResult } from 'app/openapi/model/global-search-result';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';

/** Internal type string for Iris content hits. Never displayed; used only for
 *  card rendering and click routing. Distinct from 'lecture_unit' (metadata hits),
 *  which navigates to the lecture page instead of the exact slide/timestamp. */
export const LECTURE_CONTENT_TYPE = 'lecture_content';

/**
 * Canonical serialization of the deep-link query params (keys sorted) used as a stable,
 * location-specific discriminator in the result id. Returns an empty string when there
 * are no params.
 */
function serializeQueryParams(queryParams: Record<string, string | number>): string {
    return Object.keys(queryParams)
        .sort()
        .map((key) => `${key}=${queryParams[key]}`)
        .join('&');
}

export function mapLectureContentResult(result: LectureSearchResult): GlobalSearchResult {
    const unit = result.lectureUnit;
    // A content hit is uniquely identified by its navigation destination: the deep-link path plus
    // its canonicalized query params. Video excerpts from one unit share a path and pageNumber -1,
    // so the query params (which carry the timestamp) are what keep their ids distinct.
    const location = serializeQueryParams(unit.queryParams);
    return {
        id: `lecture-content-${unit.link}${location ? `?${location}` : ''}`,
        type: LECTURE_CONTENT_TYPE,
        title: result.lectureUnit.name,
        description: result.snippet,
        metadata: {
            courseId: String(result.course.id),
            courseName: result.course.name,
            lectureId: String(result.lecture.id),
            lectureName: result.lecture.name,
            pageNumber: result.lectureUnit.pageNumber,
            sourceType: result.lectureUnit.sourceType,
            link: result.lectureUnit.link,
            queryParams: result.lectureUnit.queryParams,
            displayMeta: result.lectureUnit.displayMeta,
        },
    };
}
