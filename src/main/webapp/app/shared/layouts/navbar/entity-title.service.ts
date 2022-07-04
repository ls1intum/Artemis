import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { captureException } from '@sentry/browser';
import { EMPTY, Observable, ReplaySubject, Subject } from 'rxjs';

export enum EntityType {
    COURSE = 'COURSE',
    EXERCISE = 'EXERCISE',
    LECTURE = 'LECTURE',
    HINT = 'HINT',
    DIAGRAM = 'DIAGRAM',
    ORGANIZATION = 'ORGANIZATION',
    EXAM = 'EXAM',
}

const FETCH_FALLBACK_TIMEOUT = 3000;

/**
 * Provides titles for entities, currently used by breadcrumbs
 */
@Injectable({ providedIn: 'root' })
export class EntityTitleService {
    private readonly titleSubjects = new Map<string, { subject: Subject<string>; timeout?: ReturnType<typeof setTimeout> }>();

    constructor(private http: HttpClient) {}

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

    /**
     * Fetches the title of the given entity from the server.
     *
     * @param type the type of the entity
     * @param ids the ids that identify the entity. Mostly one ID, for exercise hints provide the exercise id as second item in the array.
     * @private
     */
    private fetchTitle(type: EntityType, ids: number[]): void {
        let resourceUrl = 'api/';
        switch (type) {
            case EntityType.COURSE:
                resourceUrl += 'courses';
                break;
            case EntityType.EXERCISE:
                resourceUrl += 'exercises';
                break;
            case EntityType.LECTURE:
                resourceUrl += 'lectures';
                break;
            case EntityType.HINT:
                resourceUrl += `programming-exercises/${ids[1]}/exercise-hints`;
                break;
            case EntityType.DIAGRAM:
                resourceUrl += 'apollon-diagrams';
                break;
            case EntityType.EXAM:
                resourceUrl += 'exams';
                break;
            case EntityType.ORGANIZATION:
                resourceUrl += 'organizations';
                break;
        }

        this.http.get(`${SERVER_API_URL}${resourceUrl}/${ids[0]}/title`, { observe: 'response', responseType: 'text' }).subscribe((response: HttpResponse<string>) => {
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
     * @private
     */
    private static createMapKey(type: EntityType, ids: number[]) {
        return `${type}-${ids.join('-')}`;
    }
}
