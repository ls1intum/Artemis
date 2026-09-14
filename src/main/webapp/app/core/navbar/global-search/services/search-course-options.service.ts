import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, shareReplay } from 'rxjs';
import { MenuCourse } from 'app/core/navbar/global-search/models/search-menu.util';

/** One row of `GET api/course/courses/for-dropdown`. It also carries an icon, which the value menu has no use for. */
interface CourseDropdownDTO {
    id?: number;
    title?: string;
}

const COURSES_FOR_DROPDOWN_URL = 'api/course/courses/for-dropdown';

/**
 * The courses the `course:` filter can offer.
 *
 * Read from the server rather than from `CourseStorageService`, which only the student course dashboard fills in bulk:
 * everywhere else that store holds at most the single course the current page happened to load, so the course filter
 * offered an arbitrary subset of the user's courses, or none at all, depending on where the palette was opened from.
 *
 * The endpoint is the lightest one that answers the question, returning id, title and icon and no course content. The
 * response is fetched at most once and shared by every later subscriber, so opening the course filter repeatedly costs
 * one request in total and never opening it costs none.
 */
@Injectable({ providedIn: 'root' })
export class SearchCourseOptionsService {
    private readonly http = inject(HttpClient);

    /** The in-flight or completed request, kept so it is issued once and replayed to everyone after it. */
    private courses?: Observable<MenuCourse[]>;

    /**
     * The courses the current user can filter by.
     *
     * @return the courses, or an empty list if they could not be read
     */
    getCourses(): Observable<MenuCourse[]> {
        this.courses ??= this.http.get<CourseDropdownDTO[]>(COURSES_FOR_DROPDOWN_URL).pipe(
            catchError(() => {
                // A failure must not be replayed for the rest of the session: drop the cached request so the next
                // time the course menu is opened it tries again, and degrade to the store fallback meanwhile.
                this.courses = undefined;
                return of<MenuCourse[]>([]);
            }),
            shareReplay({ bufferSize: 1, refCount: false }),
        );
        return this.courses;
    }
}
