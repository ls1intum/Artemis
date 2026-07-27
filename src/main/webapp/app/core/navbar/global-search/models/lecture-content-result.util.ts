import { GlobalSearchResult } from 'app/openapi/model/global-search-result';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';

/** Internal type string for Iris content hits. Never displayed; used only for
 *  card rendering and click routing. Distinct from 'lecture_unit' (metadata hits),
 *  which navigates to the lecture page instead of the exact slide/timestamp. */
export const LECTURE_CONTENT_TYPE = 'lecture_content';

export function mapLectureContentResult(result: LectureSearchResult): GlobalSearchResult {
    return {
        id: `lecture-content-${result.lectureUnit.id}-${result.lectureUnit.pageNumber}`,
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
