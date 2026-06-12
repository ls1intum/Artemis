import { Injectable, OnDestroy, inject } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { Course } from 'app/course/shared/entities/course.model';
import { SubjectObservablePair } from 'app/foundation/util/rxjs.utils';
import { AccountService } from 'app/core/auth/account.service';

/**
 * This service is used to store {@link Course} objects for the currently logged-in user.
 * The methods {@link CourseManagementService#findAllForDashboard} and {@link CourseManagementService#findOneForDashboard} retrieve one or multiple {@link Course} objects and save them in this service.
 * This way, multiple components that need a course can access it without having to retrieve it again from the server.
 * Some components update the course object and can use the {@link updateCourse} method to make the changes available to the entire application.
 * Components that need to be notified about these changes can use the {@link subscribeToCourseUpdates} method.
 */
@Injectable({ providedIn: 'root' })
export class CourseStorageService implements OnDestroy {
    private readonly accountService = inject(AccountService);

    private storedCourses: Course[] = [];

    /**
     * Ids of courses whose stored object contains the full details from the single-course for-dashboard call
     * (as opposed to the slim version from the course list, which e.g. misses exams and lectures).
     */
    private readonly fullyLoadedCourseIds = new Set<number>();

    private readonly courseUpdateSubscriptions: Map<number, SubjectObservablePair<Course>> = new Map();

    private currentUserId?: number;
    private authenticationStateSubscription: Subscription;

    constructor() {
        this.currentUserId = this.accountService.userIdentity()?.id;
        this.authenticationStateSubscription = this.accountService.getAuthenticationState().subscribe((user) => {
            if (this.currentUserId !== user?.id) {
                this.currentUserId = user?.id;
                this.resetState();
            }
        });
    }

    ngOnDestroy(): void {
        this.authenticationStateSubscription?.unsubscribe();
    }

    /**
     * Clears all stored courses and update subscriptions. Called on logout / user change so the next user
     * does not see the previous user's courses cached. Existing subject observers receive an end-of-life
     * `complete` so they unwind cleanly instead of being silently dropped.
     */
    private resetState(): void {
        this.storedCourses = [];
        this.fullyLoadedCourseIds.clear();
        this.courseUpdateSubscriptions.forEach((pair) => pair.subject.complete());
        this.courseUpdateSubscriptions.clear();
    }

    setCourses(courses?: Course[]) {
        this.storedCourses = courses ?? [];
        // The course list only contains slim courses (e.g. without exams and lectures), which replace any stored full course
        this.fullyLoadedCourseIds.clear();
    }

    getCourse(courseId: number) {
        return this.storedCourses.find((course) => course.id === courseId);
    }

    /**
     * Stores (or replaces) a course and notifies subscribers of {@link subscribeToCourseUpdates}.
     *
     * @param course       the course to store
     * @param isFullCourse whether the course contains the full details from the single-course for-dashboard call.
     *                         For any other course object the fully-loaded marker is dropped, as its completeness is unknown
     *                         (see {@link isCourseFullyLoaded}).
     */
    updateCourse(course?: Course, isFullCourse = false): void {
        if (course) {
            // filter out the old course object with the same id
            this.storedCourses = this.storedCourses.filter((existingCourse) => existingCourse.id !== course.id);
            this.storedCourses.push(course);
            if (course.id) {
                if (isFullCourse) {
                    this.fullyLoadedCourseIds.add(course.id);
                } else {
                    this.fullyLoadedCourseIds.delete(course.id);
                }
            }
            return this.courseUpdateSubscriptions.get(course.id!)?.subject.next(course);
        }
    }

    /**
     * Whether the stored course contains the full details from the single-course for-dashboard call.
     * Access decisions (e.g. in the CourseOverviewGuard) must only rely on fully loaded courses;
     * the slim course from the course list would produce wrong results (e.g. it always has empty exams).
     *
     * @param courseId the id of the course to check
     */
    isCourseFullyLoaded(courseId: number): boolean {
        return this.fullyLoadedCourseIds.has(courseId);
    }

    subscribeToCourseUpdates(courseId: number): Observable<Course> {
        if (!this.courseUpdateSubscriptions.has(courseId)) {
            this.courseUpdateSubscriptions.set(courseId, new SubjectObservablePair());
        }
        return this.courseUpdateSubscriptions.get(courseId)!.observable;
    }
}
