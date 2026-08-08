import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Observable, Subscription, of, tap } from 'rxjs';
import { CourseExercisesForOverviewDTO } from 'app/course/shared/entities/course-exercises-for-overview-dto';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { AccountService } from 'app/core/auth/account.service';

/**
 * Loads the exercise data of the course overview on demand and publishes it through the {@link CourseStorageService}.
 *
 * Exercises, participations and scores used to come with the course itself on every course entry. They are only needed
 * by the exercises tab and the statistics tab, so both go through this service instead: whichever is opened first pays
 * for the load, the other reuses it, and a course visit that never opens either pays nothing at all.
 *
 * Like {@link CourseAvailableTabsService} this is per-visit state, not a cache: one course at a time, keyed by course id
 * so switching courses refetches, dropped by {@link clear} when the course container is destroyed and on logout / user
 * change.
 */
@Injectable({ providedIn: 'root' })
export class CourseOverviewExercisesService implements OnDestroy {
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly accountService = inject(AccountService);

    private readonly state = signal<{ courseId: number; data: CourseExercisesForOverviewDTO } | undefined>(undefined);

    private currentUserId?: number;
    private readonly authenticationStateSubscription: Subscription;

    constructor() {
        this.currentUserId = this.accountService.userIdentity()?.id;
        this.authenticationStateSubscription = this.accountService.getAuthenticationState().subscribe((user) => {
            if (this.currentUserId !== user?.id) {
                this.currentUserId = user?.id;
                this.clear();
            }
        });
    }

    ngOnDestroy(): void {
        this.authenticationStateSubscription?.unsubscribe();
    }

    /**
     * The exercise data held for the given course, or undefined when it has not been loaded for it.
     * @param courseId the course to read the exercise data for
     */
    dataFor(courseId: number): CourseExercisesForOverviewDTO | undefined {
        const current = this.state();
        // Guard on `current` itself: with optional chaining alone, an undefined courseId would compare equal to the
        // undefined of an empty state and wrongly report a hit
        return current && current.courseId === courseId ? current.data : undefined;
    }

    /**
     * Fetches the exercise data from the server, stores the scores and merges the exercises into the stored course, so
     * everything subscribed to {@link CourseStorageService#subscribeToCourseUpdates} picks them up.
     * @param courseId the course to fetch the exercise data for
     */
    load(courseId: number): Observable<CourseExercisesForOverviewDTO> {
        return this.courseManagementService.findCourseExercisesForOverview(courseId).pipe(
            tap((data) => {
                this.state.set({ courseId, data });
                this.publishExercisesToStoredCourse(courseId, data);
            }),
        );
    }

    /**
     * Returns the held exercise data when it belongs to the given course and fetches it otherwise, so the exercises tab
     * and the statistics tab together cost a single request per course visit.
     * @param courseId the course to get the exercise data for
     */
    loadIfNeeded(courseId: number): Observable<CourseExercisesForOverviewDTO> {
        const data = this.dataFor(courseId);
        return data ? of(data) : this.load(courseId);
    }

    /**
     * Drops the held exercise data so the next course visit fetches it again.
     */
    clear(): void {
        this.state.set(undefined);
    }

    /**
     * Publishes the freshly loaded exercises on the stored course.
     *
     * A new top-level course object is required: consumers hold the course in a signal, and a signal only notifies when
     * the reference changes. The copy is deliberately shallow rather than a `deepClone` — `exercises` is replaced
     * wholesale with the array just received from the server, so nothing is aliased into the previous course object,
     * and deep-cloning every exercise with its participations, submissions and results would be pure waste.
     */
    private publishExercisesToStoredCourse(courseId: number, data: CourseExercisesForOverviewDTO): void {
        const course = this.courseStorageService.getCourse(courseId);
        if (!course) {
            return;
        }
        this.courseStorageService.updateCourse({ ...course, exercises: data.exercises });
    }
}
