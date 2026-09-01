import { Injectable, OnDestroy, computed, inject, signal } from '@angular/core';
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

    private readonly storedCourses = signal<Course[]>([]);
    private readonly currentCourseId = signal<number | undefined>(undefined);

    readonly currentCourse = computed(() => {
        const courseId = this.currentCourseId();
        return courseId === undefined ? undefined : this.getCourse(courseId);
    });

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
        this.storedCourses.set([]);
        this.currentCourseId.set(undefined);
        this.courseUpdateSubscriptions.forEach((pair) => pair.subject.complete());
        this.courseUpdateSubscriptions.clear();
    }

    setCourses(courses?: Course[]) {
        this.storedCourses.set(courses ?? []);
    }

    getCourse(courseId: number) {
        return this.storedCourses().find((course) => course.id === courseId);
    }

    setCurrentCourse(courseId: number): void {
        this.currentCourseId.set(courseId);
    }

    clearCurrentCourse(): void {
        this.currentCourseId.set(undefined);
    }

    /**
     * Stores (or replaces) a course and notifies subscribers of {@link subscribeToCourseUpdates}.
     *
     * @param course the course to store
     */
    updateCourse(course?: Course): void {
        if (course) {
            // filter out the old course object with the same id
            this.storedCourses.update((courses) => [...courses.filter((existingCourse) => existingCourse.id !== course.id), course]);
            return this.courseUpdateSubscriptions.get(course.id!)?.subject.next(course);
        }
    }

    /**
     * Drops the stored course, so a response that carried no course does not leave the previous one readable.
     *
     * Separate from {@link updateCourse}, which cannot express this: it takes the course to store, so it has no id to
     * remove by when there is nothing to store.
     *
     * @param courseId the course to drop
     */
    removeCourse(courseId: number): void {
        this.storedCourses.update((courses) => courses.filter((existingCourse) => existingCourse.id !== courseId));
    }

    subscribeToCourseUpdates(courseId: number): Observable<Course> {
        if (!this.courseUpdateSubscriptions.has(courseId)) {
            this.courseUpdateSubscriptions.set(courseId, new SubjectObservablePair());
        }
        return this.courseUpdateSubscriptions.get(courseId)!.observable;
    }
}
