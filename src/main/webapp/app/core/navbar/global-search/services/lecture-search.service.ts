import { Service, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { Observable, map } from 'rxjs';
import { LectureSearchResult } from 'app/core/navbar/global-search/models/lecture-search-result.model';
import { GlobalSearchLectureRequest } from 'app/openapi/model/global-search-lecture-request';
import { PyrisLectureSearchResult } from 'app/openapi/model/pyris-lecture-search-result';

@Service()
export class LectureSearchService {
    private readonly http = inject(HttpClient);

    /**
     * Searches lecture content (slides and video transcripts) through Iris.
     *
     * @param query            the search term
     * @param limit            the maximum number of hits
     * @param courseIds        the courses to search, or undefined to search everything the user can reach
     * @param excludeCourseIds the courses to hide from the search
     * @param silent           true when the caller hides a failure itself, so no global error alert is shown for it
     * @return the matching content hits
     */
    search(query: string, limit = 10, courseIds?: number[], excludeCourseIds?: number[], silent = false): Observable<LectureSearchResult[]> {
        const body: GlobalSearchLectureRequest = { query, limit };
        if (courseIds?.length) {
            body.courseIds = courseIds;
        }
        if (excludeCourseIds?.length) {
            body.excludeCourseIds = excludeCourseIds;
        }
        // The pinned generator does not support per-call HttpContext.
        return this.http
            .post<PyrisLectureSearchResult[]>('api/iris/lecture-search', body, { context: new HttpContext().set(SKIP_HTTP_ERROR_ALERT, silent) })
            .pipe(map((results) => results.map(mapLectureSearchHit)));
    }
}

function mapLectureSearchHit({ course, lecture, lectureUnit, snippet }: PyrisLectureSearchResult): LectureSearchResult {
    if (
        course?.id === undefined ||
        course.name === undefined ||
        lecture?.id === undefined ||
        lecture.name === undefined ||
        lectureUnit?.id === undefined ||
        lectureUnit.name === undefined ||
        lectureUnit.link === undefined ||
        lectureUnit.pageNumber === undefined ||
        lectureUnit.sourceType === undefined
    ) {
        throw new Error('Iris returned an incomplete lecture search hit');
    }
    return {
        course: { id: course.id, name: course.name },
        lecture: { id: lecture.id, name: lecture.name },
        lectureUnit: {
            id: lectureUnit.id,
            name: lectureUnit.name,
            link: lectureUnit.link,
            pageNumber: lectureUnit.pageNumber,
            sourceType: lectureUnit.sourceType,
            // An empty map is omitted by Jackson. These parameters only contain navigation values.
            queryParams: lectureUnit.queryParams ?? {},
            displayMeta: lectureUnit.displayMeta,
        },
        snippet,
    };
}
