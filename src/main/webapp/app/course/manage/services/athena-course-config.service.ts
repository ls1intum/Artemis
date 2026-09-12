import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, catchError, concatMap, finalize, of, shareReplay } from 'rxjs';

/**
 * The course-level Athena feedback configuration an instructor can edit.
 */
export interface AthenaCourseConfigDTO {
    gradingFeedbackEnabled: boolean;
    formativeFeedbackEnabled: boolean;
    /**
     * The instance-wide cap on successful automatic Athena feedback requests per participation. Read-only: it comes
     * from server configuration, not from this course, and is never sent back in an update. Optional so the many
     * existing callers of this DTO (course overview toggle, onboarding wizard) that never read it can keep
     * constructing it without this field.
     */
    allowedFeedbackRequests?: number;
}

/**
 * A change to the course-level Athena configuration: only the features being switched, everything left out stays as it
 * is. Restating the other feature would send whatever this client last saw for it, undoing a change made elsewhere in
 * the meantime.
 */
export type AthenaCourseConfigUpdate = Partial<AthenaCourseConfigDTO>;

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
     * Each update names only the feature it switches, so the server can no longer store a stale value for the other
     * one. Queueing the requests of a course on top of that keeps two clicks on the same feature in the instructor's
     * click order, and lets their responses arrive in that order too, so the state a toggle ends up showing is the one
     * on the server.
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
     * Change the Athena configuration of a course.
     *
     * @param courseId the id of the course
     * @param update the features to switch; a feature left out is not changed
     */
    updateCourseConfig(courseId: number, update: AthenaCourseConfigUpdate): Observable<HttpResponse<AthenaCourseConfigDTO>> {
        const request = this.http.patch<AthenaCourseConfigDTO>(`${this.resourceUrl}/${courseId}/athena-configuration`, update, { observe: 'response' });
        const predecessor = this.queuedUpdates.get(courseId);

        // Boxed because the entry this call puts in the map only exists once the pipe below has been built, while the
        // pipe already has to know which entry to remove again.
        const entry: { queued?: Observable<unknown> } = {};

        const pending = (predecessor ? predecessor.pipe(concatMap(() => request)) : request).pipe(
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
        entry.queued = pending.pipe(catchError(() => of(undefined)));
        this.queuedUpdates.set(courseId, entry.queued);

        return pending;
    }
}
