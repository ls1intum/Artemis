import { Service, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { finalize, map, shareReplay, tap } from 'rxjs/operators';
import { IrisCourseSettingsDTO, IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { IrisSettingsApi } from 'app/openapi/api/iris-settings-api';

/**
 * Service for managing Iris course-level settings.
 * Replaces the legacy three-tier (Global → Course → Exercise) settings system.
 */
@Service()
export class IrisSettingsService {
    private api = inject(IrisSettingsApi);

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

        const request$ = this.api.getCourseSettings(courseId).pipe(
            map((settings) => settings ?? undefined),
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
        return this.api.updateCourseSettings(courseId, settings).pipe(
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
