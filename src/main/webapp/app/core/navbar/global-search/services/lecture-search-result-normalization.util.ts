import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { normalizeLectureDeepLinkQueryParams } from 'app/lecture/overview/course-lectures/lecture-deep-link.model';

export function normalizeLectureSearchResultQueryParams(result: LectureSearchResult): LectureSearchResult {
    return cloneWith(result, {
        lectureUnit: cloneWith(result.lectureUnit, {
            queryParams: normalizeLectureDeepLinkQueryParams(result.lectureUnit.queryParams),
        }),
    });
}
