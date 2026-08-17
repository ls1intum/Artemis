import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, OnDestroy, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { EMPTY, Observable, Subscription, catchError, finalize, of, shareReplay, tap } from 'rxjs';
import { CourseExercisesForOverviewDTO } from 'app/course/shared/entities/course-exercises-for-overview-dto';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';
import { currentNavigationId } from 'app/course/overview/services/navigation-scope';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { TeamService } from 'app/exercise/team/team.service';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TeamAssignmentPayload } from 'app/exercise/shared/entities/team/team.model';

/**
 * Loads the exercise data of the course overview on demand and publishes it through the {@link CourseStorageService}.
 *
 * Exercises, participations and scores used to come with the course itself on every course entry. They are only needed
 * by the exercises tab and the statistics tab, so both go through this service instead, and a course visit that never
 * opens either pays nothing at all.
 *
 * Nothing is cached. What is held is scoped to a single router navigation, so that one tab selection needing the data
 * twice costs one request while the next selection — including re-selecting the tab you are on, which is how a student
 * refreshes — always asks the server again.
 */
@Injectable({ providedIn: 'root' })
export class CourseOverviewExercisesService implements OnDestroy {
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly courseStorageService = inject(CourseStorageService);
    private readonly router = inject(Router);
    private readonly accountService = inject(AccountService);
    private readonly alertService = inject(AlertService);
    private readonly websocketService = inject(WebsocketService);
    private readonly teamService = inject(TeamService);
    private readonly courseExerciseService = inject(CourseExerciseService);

    private readonly state = signal<{ courseId: number; navigationId: number; data: CourseExercisesForOverviewDTO } | undefined>(undefined);
    private inFlightRequest?: { courseId: number; observable: Observable<CourseExercisesForOverviewDTO> };
    private activeCourseId?: number;
    private pendingStoredCourseSubscription?: Subscription;
    private pendingStoredCourseId?: number;
    private quizExercisesSubscription?: Subscription;
    private teamAssignmentUpdateSubscription?: Subscription;
    private liveUpdatesCourseId?: number;
    private liveUpdatesGeneration = 0;

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
        this.clear();
        this.authenticationStateSubscription?.unsubscribe();
    }

    /**
     * The exercise data held for the given course during the current navigation, or undefined when there is none.
     *
     * Scoped to the navigation on purpose: the exercises tab and the statistics tab must share one response when a
     * single tab selection needs it twice, but selecting a tab again has to reach the server, so that a result which
     * arrived meanwhile or a due date an instructor moved shows up without a page reload.
     *
     * @param courseId the course to read the exercise data for
     */
    dataFor(courseId: number): CourseExercisesForOverviewDTO | undefined {
        const current = this.state();
        return current && current.navigationId === currentNavigationId(this.router) ? this.heldDataFor(courseId) : undefined;
    }

    /**
     * The exercise data held for the given course, whichever navigation fetched it.
     *
     * Only for publishing data that has already been fetched. Deciding whether to fetch must go through
     * {@link dataFor}, which additionally requires the data to belong to the current navigation.
     *
     * @param courseId the course to read the exercise data for
     */
    private heldDataFor(courseId: number): CourseExercisesForOverviewDTO | undefined {
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
        if (this.inFlightRequest && this.inFlightRequest.courseId === courseId) {
            return this.inFlightRequest.observable;
        }

        this.activateCourse(courseId);
        const observable = this.courseManagementService.findCourseExercisesForOverview(courseId).pipe(
            tap((data) => {
                // A response from a course that has since been left must never replace the active course's cache.
                if (this.activeCourseId !== courseId) {
                    return;
                }
                this.state.set({ courseId, navigationId: currentNavigationId(this.router), data });
                this.publishExercisesToStoredCourse(courseId, data);
                this.startLiveUpdates(courseId);
            }),
            catchError((error: unknown) => {
                // The global HTTP interceptor already displays structured server errors and connection failures.
                // Add a fallback for errors it deliberately does not handle, then terminate the optional tab load
                // cleanly so next-only subscribers do not route the error through RxJS's global error handler.
                if (!this.isHandledByGlobalHttpAlert(error)) {
                    this.alertService.error('artemisApp.courseOverview.exerciseLoadFailed');
                }
                return EMPTY;
            }),
            finalize(() => {
                if (this.inFlightRequest?.observable === observable) {
                    this.inFlightRequest = undefined;
                }
            }),
            shareReplay({ bufferSize: 1, refCount: true }),
        );
        this.inFlightRequest = { courseId, observable };
        return observable;
    }

    /**
     * Returns the held exercise data when it belongs to the given course *and* to the current navigation, and fetches
     * it otherwise.
     * <p>
     * Sharing is per navigation, not per course visit: the exercises tab and the statistics tab share one response when
     * a single tab selection needs it twice, but selecting either tab again fetches. Moving between the two therefore
     * costs this endpoint each time — it is the heaviest of the overview endpoints, and this is the price of the
     * guarantee that selecting a tab shows current data. If that proves too expensive, the hold has to become
     * time-based rather than navigation-based; it cannot simply be widened back to the whole visit without losing the
     * guarantee.
     *
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
        this.activeCourseId = undefined;
        this.inFlightRequest = undefined;
        this.state.set(undefined);
        this.pendingStoredCourseSubscription?.unsubscribe();
        this.pendingStoredCourseSubscription = undefined;
        this.pendingStoredCourseId = undefined;
        this.stopLiveUpdates();
    }

    /**
     * Publishes the freshly loaded exercises on the stored course.
     */
    private publishExercisesToStoredCourse(courseId: number, data: CourseExercisesForOverviewDTO): void {
        const course = this.courseStorageService.getCourse(courseId);
        if (!course) {
            this.waitForStoredCourse(courseId);
            return;
        }
        if (this.pendingStoredCourseId === courseId) {
            this.pendingStoredCourseSubscription?.unsubscribe();
            this.pendingStoredCourseSubscription = undefined;
            this.pendingStoredCourseId = undefined;
        }
        // A different object has to be stored: consumers hold the course in a signal, which only notifies when the
        // reference changes. deepClone is the documented way to produce it; the exercises it copies are thrown away on
        // the next line, so what actually survives the clone is the course's own scalars and dates.
        const updatedCourse = deepClone(course);
        updatedCourse.exercises = data.exercises;
        this.courseStorageService.updateCourse(updatedCourse);
    }

    /**
     * Waits for the lean course request when the exercise request wins the race. CourseStorageService is intentionally
     * not replaying, so this subscription is installed only after confirming that no course is stored yet.
     */
    private waitForStoredCourse(courseId: number): void {
        if (this.pendingStoredCourseId === courseId) {
            return;
        }
        this.pendingStoredCourseSubscription?.unsubscribe();
        this.pendingStoredCourseId = courseId;
        this.pendingStoredCourseSubscription = this.courseStorageService.subscribeToCourseUpdates(courseId).subscribe(() => {
            if (this.activeCourseId !== courseId) {
                return;
            }
            // Deliberately not the navigation-scoped read: by the time the lean course arrives, the exercises tab has
            // usually auto-selected an exercise, and that navigation would make the data we are here to publish look
            // absent. The merge would then be skipped and the exercises already fetched would be lost until the tab was
            // selected again, leaving a cold entry that raced this way showing an empty exercises tab.
            const data = this.heldDataFor(courseId);
            if (!data) {
                return;
            }
            // Unsubscribe before publishing because updateCourse synchronously emits on this same stream.
            this.pendingStoredCourseSubscription?.unsubscribe();
            this.pendingStoredCourseSubscription = undefined;
            this.pendingStoredCourseId = undefined;
            this.publishExercisesToStoredCourse(courseId, data);
        });
    }

    private activateCourse(courseId: number): void {
        if (this.activeCourseId === courseId) {
            return;
        }
        this.activeCourseId = courseId;
        this.state.set(undefined);
        this.pendingStoredCourseSubscription?.unsubscribe();
        this.pendingStoredCourseSubscription = undefined;
        this.pendingStoredCourseId = undefined;
        this.stopLiveUpdates();
    }

    /**
     * Keeps websocket-driven quiz visibility and team assignments in the shared cache. These subscriptions must outlive
     * the exercises component itself: a student can leave the tab, receive an update, and later return to the same
     * course visit without reloading the REST payload.
     */
    private startLiveUpdates(courseId: number): void {
        if (this.liveUpdatesCourseId === courseId) {
            return;
        }
        this.stopLiveUpdates();
        this.liveUpdatesCourseId = courseId;
        const generation = this.liveUpdatesGeneration;

        const quizChannel = `/topic/courses/${courseId}/quizExercises`;
        this.quizExercisesSubscription = this.websocketService.subscribe<QuizExercise>(quizChannel).subscribe({
            next: (quizExercise) => {
                const converted = this.courseExerciseService.convertExerciseDatesFromServer(quizExercise);
                this.updateCachedExercises(courseId, (exercises) => exercises.filter((exercise) => exercise.id !== converted.id).concat(converted));
            },
            error: () => {},
        });

        void this.subscribeToTeamAssignmentUpdates(courseId, generation);
    }

    private async subscribeToTeamAssignmentUpdates(courseId: number, generation: number): Promise<void> {
        try {
            const updates = await this.teamService.teamAssignmentUpdates;
            if (this.liveUpdatesGeneration !== generation || this.liveUpdatesCourseId !== courseId) {
                return;
            }
            this.teamAssignmentUpdateSubscription = updates.subscribe({
                next: (teamAssignment) => this.applyTeamAssignment(courseId, teamAssignment),
                error: () => {},
            });
        } catch {
            // A websocket setup failure must not turn an otherwise successful REST load into an unhandled rejection.
        }
    }

    private applyTeamAssignment(courseId: number, teamAssignment: TeamAssignmentPayload): void {
        this.updateCachedExercises(courseId, (exercises) => {
            let didUpdate = false;
            const updated = exercises.map((exercise) => {
                if (exercise.id !== teamAssignment.exerciseId) {
                    return exercise;
                }
                didUpdate = true;
                return cloneWith(exercise, { studentAssignedTeamId: teamAssignment.teamId, studentParticipations: teamAssignment.studentParticipations });
            });
            return didUpdate ? updated : exercises;
        });
    }

    private updateCachedExercises(courseId: number, update: (exercises: Exercise[]) => Exercise[]): void {
        const current = this.state();
        if (!current || current.courseId !== courseId || this.activeCourseId !== courseId) {
            return;
        }
        // Prefer the stored copy because exercise-detail and participation websocket flows can enrich it after the
        // original REST response. A quiz/team update must preserve those newer participation snapshots.
        const source = this.courseStorageService.getCourse(courseId)?.exercises ?? current.data.exercises;
        const exercises = update(source ?? []);
        if (exercises === source) {
            return;
        }
        const data = cloneWith(current.data, { exercises });
        this.state.set({ courseId, navigationId: currentNavigationId(this.router), data });
        this.publishExercisesToStoredCourse(courseId, data);
    }

    private stopLiveUpdates(): void {
        this.liveUpdatesGeneration++;
        this.liveUpdatesCourseId = undefined;
        this.quizExercisesSubscription?.unsubscribe();
        this.quizExercisesSubscription = undefined;
        this.teamAssignmentUpdateSubscription?.unsubscribe();
        this.teamAssignmentUpdateSubscription = undefined;
    }

    private isHandledByGlobalHttpAlert(error: unknown): boolean {
        if (!(error instanceof HttpErrorResponse)) {
            return false;
        }
        if (error.status === 0) {
            return true;
        }
        if (error.status === 404) {
            // AlertService deliberately suppresses all 404 responses.
            return false;
        }
        if (error.status === 400) {
            const hasApplicationErrorHeader = error.headers.keys().some((header) => header.toLowerCase().endsWith('app-error'));
            return hasApplicationErrorHeader || !!error.error?.fieldErrors?.length || !!error.error?.title;
        }
        return !!error.error?.title;
    }
}
