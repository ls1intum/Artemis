import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, OnDestroy, inject } from '@angular/core';
import { captureException } from '@sentry/angular';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { EMPTY, Observable, ReplaySubject, Subject, Subscription } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';

export enum EntityType {
    COURSE = 'COURSE',
    EXERCISE = 'EXERCISE',
    LECTURE = 'LECTURE',
    COMPETENCY = 'COMPETENCY',
    DIAGRAM = 'DIAGRAM',
    ORGANIZATION = 'ORGANIZATION',
    EXAM = 'EXAM',
    TUTORIAL_GROUP = 'TUTORIAL_GROUP',
}

const FETCH_FALLBACK_TIMEOUT = 3000;

/**
 * Provides titles for entities, currently used by breadcrumbs
 */
@Injectable({ providedIn: 'root' })
export class EntityTitleService implements OnDestroy {
    private http = inject(HttpClient);
    private readonly accountService = inject(AccountService);

    private readonly titleSubjects = new Map<string, { subject: Subject<string>; timeout?: ReturnType<typeof setTimeout> }>();

    /**
     * Incremented on every {@link resetState} run. Captured by {@link fetchTitle} when it issues
     * an HTTP request, so a late response cannot repopulate the cache with the previous user's
     * title after a logout / user change.
     */
    private stateGeneration = 0;

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
     * Clears the cached title subjects and any pending fetch timeouts. Called on logout / user change
     * so the next user does not see the previous user's entity titles in breadcrumbs.
     */
    private resetState(): void {
        this.stateGeneration++;
        this.titleSubjects.forEach(({ subject, timeout }) => {
            if (timeout) {
                clearTimeout(timeout);
            }
            subject.complete();
        });
        this.titleSubjects.clear();
    }

    /**
     * Returns an observable that will provide the title of the entity.
     * The observable will yield the title immediately if it has already been set. Otherwise, it will wait until the title is provided.
     * Fires a request to fetch the title after FETCH_FALLBACK_TIMEOUT ms if the title is not provided from elsewhere until that time.
     *
     * @param type the entity type
     * @param ids the ids that identify the entity. Mostly one ID, for exercise hints provide the exercise id as second item in the array.
     */
    public getTitle(type: EntityType, ids: number[]): Observable<string> {
        // We want to be very defensive here, therefore we wrap everything in a try/catch and return EMPTY if an error occurs
        try {
            if (!type || !ids?.length || ids.some((id) => !id && id !== 0)) {
                captureException(new Error(`Supplied invalid parameters to getTitle() of EntityTitleService: Type=${type}, ids=${ids}`));
                return EMPTY;
            }

            const mapKey = EntityTitleService.createMapKey(type, ids);

            const { subject } = this.titleSubjects.computeIfAbsent(mapKey, () => ({
                timeout: setTimeout(() => this.fetchTitle(type, ids), FETCH_FALLBACK_TIMEOUT),
                subject: new ReplaySubject<string>(1),
            }));

            return subject.asObservable();
        } catch (e) {
            captureException(e);
            return EMPTY;
        }
    }

    /**
     * Set the title of the provided entity.
     * Will not set the title if falsy values are passed.
     *
     * @param type the type of the entity
     * @param ids the ids that identify the entity. Mostly one ID, for exercise hints provide the exercise id as second item in the array.
     * @param title the title of the entity
     */
    public setTitle(type: EntityType, ids: (number | undefined)[], title: string | undefined) {
        // We want to be very defensive here, therefore we wrap everything in a try/catch
        try {
            if (!ids?.length || ids.some((id) => !id && id !== 0) || !title) {
                captureException(new Error(`Supplied invalid parameters to setTitle() of EntityTitleService: Type=${type}, ids=${ids}, title=${title}`));
                return;
            }

            const mapKey = EntityTitleService.createMapKey(type, ids as number[]);

            const { subject, timeout } = this.titleSubjects.computeIfAbsent(mapKey, () => ({
                subject: new ReplaySubject<string>(1),
            }));

            subject.next(title!);

            if (timeout) {
                clearTimeout(timeout);
            }
        } catch (e) {
            captureException(e);
        }
    }

    public setExerciseTitle(exercise: Exercise) {
        // we only want to show the exercise group name as exercise name to the students for exam exercises.
        // for tutors and more privileged users, we want to show the exercise title
        if (exercise.exerciseGroup && !exercise?.isAtLeastTutor) {
            this.setTitle(EntityType.EXERCISE, [exercise.id], exercise.exerciseGroup.title);
        } else {
            this.setTitle(EntityType.EXERCISE, [exercise.id], exercise.title);
        }
    }

    /**
     * Fetches the title of the given entity from the server.
     *
     * @param type the type of the entity
     * @param ids the ids that identify the entity. Mostly one ID, for exercise hints provide the exercise id as second item in the array.
     */
    private fetchTitle(type: EntityType, ids: number[]): void {
        let resourceUrl = 'api/';
        switch (type) {
            case EntityType.COURSE:
                resourceUrl += 'course/courses';
                break;
            case EntityType.EXERCISE:
                resourceUrl += 'exercise/exercises';
                break;
            case EntityType.LECTURE:
                resourceUrl += 'lecture/lectures';
                break;
            case EntityType.COMPETENCY:
                resourceUrl += 'atlas/competencies';
                break;
            case EntityType.DIAGRAM:
                resourceUrl += 'modeling/apollon-diagrams';
                break;
            case EntityType.EXAM:
                resourceUrl += 'exam/exams';
                break;
            case EntityType.ORGANIZATION:
                resourceUrl += 'core/organizations';
                break;
            case EntityType.TUTORIAL_GROUP:
                resourceUrl += 'tutorialgroup/tutorial-groups';
                break;
        }

        const generation = this.stateGeneration;
        this.http.get(`${resourceUrl}/${ids[0]}/title`, { observe: 'response', responseType: 'text' }).subscribe((response: HttpResponse<string>) => {
            // Skip the write-back if a logout / user change happened between the request being issued
            // and the response arriving — otherwise we would repopulate the cleared cache (and via
            // setTitle's computeIfAbsent, seed a fresh subject) with the previous user's title.
            if (this.stateGeneration !== generation) return;
            if (response.body) {
                this.setTitle(type, ids, response.body);
            }
        });
    }

    /**
     * Builds a single string ID from the type and numeric IDs.
     *
     * @param type the type of the entity
     * @param ids the ids that identify the entity. Mostly one ID, for exercise hints provide the exercise id as second item in the array.
     */
    private static createMapKey(type: EntityType, ids: number[]) {
        return `${type}-${ids.join('-')}`;
    }
}
