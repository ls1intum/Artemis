import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { finalize, map, shareReplay, tap } from 'rxjs/operators';
import { IrisCourseSettingsDTO, IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';

/**
 * Service for managing Iris course-level settings.
 * Replaces the legacy three-tier (Global → Course → Exercise) settings system.
 */
@Injectable({ providedIn: 'root' })
export class IrisSettingsService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/iris';

    // Simplified caching for course settings only
    private courseSettingsCache = new Map<number, IrisCourseSettingsWithRateLimitDTO>();
    private pendingCourseRequests = new Map<number, Observable<IrisCourseSettingsWithRateLimitDTO | undefined>>();
    private courseCacheTimestamps = new Map<number, number>();
    private static readonly CACHE_DURATION = 5 * 60 * 1000; // 5 minutes

    /**
     * Get the Iris settings for a course.
     * Uses caching to avoid unnecessary API calls for 5 minutes and ensures
     * that simultaneous requests for the same course ID reuse the same pending request.
     *
     * @param courseId the id of the course
     */
    getCourseSettingsWithRateLimit(courseId: number): Observable<IrisCourseSettingsWithRateLimitDTO | undefined> {
        const now = Date.now();
        const cached = this.courseSettingsCache.get(courseId);
        const timestamp = this.courseCacheTimestamps.get(courseId);

        if (cached && timestamp && now - timestamp < IrisSettingsService.CACHE_DURATION) {
            return of(cached);
        }

        const pending = this.pendingCourseRequests.get(courseId);
        if (pending) {
            return pending;
        }

        const request$ = this.http.get<IrisCourseSettingsWithRateLimitDTO>(`${this.resourceUrl}/courses/${courseId}/iris-settings`, { observe: 'response' }).pipe(
            map((res: HttpResponse<IrisCourseSettingsWithRateLimitDTO>) => res.body ?? undefined),
            tap((settings) => {
                if (settings) {
                    this.courseSettingsCache.set(courseId, settings);
                    this.courseCacheTimestamps.set(courseId, Date.now());
                }
            }),
            finalize(() => this.pendingCourseRequests.delete(courseId)),
            shareReplay(1),
        );

        this.pendingCourseRequests.set(courseId, request$);
        return request$;
    }

    /**
     * Update the Iris settings for a course.
     * Invalidates the cache on success.
     *
     * @param courseId the id of the course
     * @param settings the settings to update
     */
    updateCourseSettings(courseId: number, settings: IrisCourseSettingsDTO): Observable<HttpResponse<IrisCourseSettingsWithRateLimitDTO>> {
        return this.http.put<IrisCourseSettingsWithRateLimitDTO>(`${this.resourceUrl}/courses/${courseId}/iris-settings`, settings, { observe: 'response' }).pipe(
            tap(() => {
                // Invalidate cache on successful update
                this.courseSettingsCache.delete(courseId);
                this.courseCacheTimestamps.delete(courseId);
            }),
        );
    }

    /**
     * Invalidates the cache for a specific course.
     * Useful when settings are updated externally.
     *
     * @param courseId the id of the course
     */
    invalidateCacheForCourse(courseId: number): void {
        this.courseSettingsCache.delete(courseId);
        this.courseCacheTimestamps.delete(courseId);
    }

    /**
     * Clears all cached settings.
     */
    clearCache(): void {
        this.courseSettingsCache.clear();
        this.courseCacheTimestamps.clear();
        this.pendingCourseRequests.clear();
    }
}
