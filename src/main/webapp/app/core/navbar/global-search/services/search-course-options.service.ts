import { Service, Signal, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, catchError, of, shareReplay } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MenuCourse } from 'app/core/navbar/global-search/models/search-menu.util';
import { GlobalSearchApi } from 'app/openapi/api/global-search-api';
import { CourseForSearchFilter } from 'app/openapi/model/course-for-search-filter';

/**
 * The courses the `course:` filter can offer.
 *
 * Read from the server rather than from `CourseStorageService`, which only the student course dashboard fills in bulk:
 * everywhere else that store holds at most the single course the current page happened to load, so the course filter
 * offered an arbitrary subset of the user's courses, or none at all, depending on where the palette was opened from.
 *
 * The endpoint lists every course an unscoped search covers, past courses included, so the filter can narrow to any of
 * them; each row carries an active flag the menu ranks by. It returns id, title and that flag and no course content. The
 * response is fetched at most once and shared by every later subscriber, so opening the course filter repeatedly costs
 * one request in total and never opening it costs none.
 */
@Service()
export class SearchCourseOptionsService {
    private readonly searchApi = inject(GlobalSearchApi);
    private readonly accountService = inject(AccountService);

    /** The in-flight or completed request, kept so it is issued once and replayed to everyone after it. */
    private courses?: Observable<CourseForSearchFilter[]>;

    /**
     * Incremented whenever the signed-in user changes. A cached course list belongs to the user it was read
     * for, so consumers compare this against the value they read at request time and drop anything older:
     * signing out and in without reloading would otherwise leave the next user filtering by the previous
     * user's courses, which they cannot even open.
     */
    private readonly userGeneration = signal(0);

    private currentUserId?: number;

    constructor() {
        this.currentUserId = this.accountService.userIdentity()?.id;
        this.accountService
            .getAuthenticationState()
            .pipe(takeUntilDestroyed())
            .subscribe((user) => {
                if (this.currentUserId !== user?.id) {
                    this.currentUserId = user?.id;
                    this.courses = undefined;
                    this.userGeneration.update((generation) => generation + 1);
                }
            });
    }

    /** The current user generation; a caller stores it with any data it caches from {@link getCourses}. */
    get generation(): Signal<number> {
        return this.userGeneration.asReadonly();
    }

    /**
     * The courses the current user can filter by.
     *
     * @return the courses, or an empty list if they could not be read
     */
    getCourses(): Observable<MenuCourse[]> {
        if (this.courses) {
            return this.courses;
        }
        const generation = this.userGeneration();
        this.courses = this.searchApi.getCourseFilterOptions().pipe(
            catchError(() => {
                // Retry on the next read, but never let a previous user's late failure evict the current user's request.
                if (generation === this.userGeneration()) {
                    this.courses = undefined;
                }
                return of<CourseForSearchFilter[]>([]);
            }),
            shareReplay({ bufferSize: 1, refCount: false }),
        );
        return this.courses;
    }
}
