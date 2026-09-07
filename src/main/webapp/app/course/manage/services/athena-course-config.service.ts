import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, catchError, concatMap, finalize, of, shareReplay } from 'rxjs';

/**
 * The course-level Athena feedback configuration an instructor can edit.
 */
export interface AthenaCourseConfigDTO {
    gradingFeedbackEnabled: boolean;
    formativeFeedbackEnabled: boolean;
}

/**
 * Reads and writes the course-level Athena configuration.
 *
 * The toggles live on the course overview and in the onboarding wizard and save immediately, so they use this
 * dedicated endpoint rather than the whole-course update: a stale course settings form must not be able to overwrite
 * what was just toggled.
 */
@Injectable({ providedIn: 'root' })
export class AthenaCourseConfigService {
    private http = inject(HttpClient);

    private readonly resourceUrl = 'api/course/courses';

    /**
     * The last update queued per course, while that course has one in flight.
     *
     * Every update writes a complete configuration, so two of them racing would let the older snapshot be stored last
     * and silently undo the newer switch. Queueing them per course keeps the write order the instructor's click order,
     * and lets the responses arrive in that order too, so the state a toggle ends up showing is the one on the server.
     */
    private readonly queuedUpdates = new Map<number, Observable<unknown>>();

    /**
     * Get the Athena configuration of a course.
     *
     * @param courseId the id of the course
     */
    getCourseConfig(courseId: number): Observable<AthenaCourseConfigDTO> {
        return this.http.get<AthenaCourseConfigDTO>(`${this.resourceUrl}/${courseId}/athena-configuration`);
    }

    /**
     * Update the Athena configuration of a course.
     *
     * @param courseId the id of the course
     * @param config the configuration to store
     */
    updateCourseConfig(courseId: number, config: AthenaCourseConfigDTO): Observable<HttpResponse<AthenaCourseConfigDTO>> {
        const request = this.http.put<AthenaCourseConfigDTO>(`${this.resourceUrl}/${courseId}/athena-configuration`, config, { observe: 'response' });
        const predecessor = this.queuedUpdates.get(courseId);

        // Boxed because the entry this call puts in the map only exists once the pipe below has been built, while the
        // pipe already has to know which entry to remove again.
        const entry: { queued?: Observable<unknown> } = {};

        const update = (predecessor ? predecessor.pipe(concatMap(() => request)) : request).pipe(
            finalize(() => {
                // Only the last update of a course empties its queue; a newer one has already replaced this entry.
                if (this.queuedUpdates.get(courseId) === entry.queued) {
                    this.queuedUpdates.delete(courseId);
                }
            }),
            // One shared subscription, so the request is sent once no matter how many callers wait for it, and the
            // updates queued behind it still run when the caller that started it goes away.
            shareReplay({ bufferSize: 1, refCount: false }),
        );

        // A rejected update must not cancel the ones behind it, so the successor waits on a chain that only completes.
        entry.queued = update.pipe(catchError(() => of(undefined)));
        this.queuedUpdates.set(courseId, entry.queued);

        return update;
    }
}
