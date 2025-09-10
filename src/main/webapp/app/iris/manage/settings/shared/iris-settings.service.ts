import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { finalize, map, shareReplay, tap } from 'rxjs/operators';
import { IrisCourseSettings, IrisExerciseSettings, IrisGlobalSettings } from 'app/iris/shared/entities/settings/iris-settings.model';
import { IrisVariant } from 'app/iris/shared/entities/settings/iris-variant';
import { IrisSubSettingsType } from 'app/iris/shared/entities/settings/iris-sub-settings.model';

/**
 * Service for calling the Iris settings endpoints on the server
 */
@Injectable({ providedIn: 'root' })
export class IrisSettingsService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/iris';

    private courseSettingsCache = new Map<number, IrisCourseSettings>();
    private exerciseSettingsCache = new Map<number, IrisExerciseSettings>();
    private pendingCourseRequests = new Map<number, Observable<IrisCourseSettings | undefined>>();
    private pendingExerciseRequests = new Map<number, Observable<IrisExerciseSettings | undefined>>();
    private courseCacheTimestamps = new Map<number, number>();
    private exerciseCacheTimestamps = new Map<number, number>();
    private static readonly CACHE_DURATION = 5 * 60 * 1000; // 5 minutes

    /**
     * Get the global Iris settings
     */
    getGlobalSettings(): Observable<IrisGlobalSettings | undefined> {
        return this.http
            .get<IrisGlobalSettings>(`${this.resourceUrl}/global-iris-settings`, { observe: 'response' })
            .pipe(map((res: HttpResponse<IrisGlobalSettings>) => res.body ?? undefined));
    }

    /**
     * Get the uncombined Iris settings for a course
     * @param courseId the id of the course
     */
    getUncombinedCourseSettings(courseId: number): Observable<IrisCourseSettings | undefined> {
        return this.http
            .get<IrisCourseSettings>(`${this.resourceUrl}/courses/${courseId}/raw-iris-settings`, { observe: 'response' })
            .pipe(map((res: HttpResponse<IrisCourseSettings>) => res.body ?? undefined));
    }

    /**
     * Get the combined Iris settings for a course.
     * Uses caching to avoid unnecessary API calls for 5 minutes and ensures
     * that simultaneous requests for the same course ID reuse the same pending request.
     *
     * @param courseId the id of the course
     */
    getCombinedCourseSettings(courseId: number): Observable<IrisCourseSettings | undefined> {
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

        const request$ = this.http.get<IrisCourseSettings>(`${this.resourceUrl}/courses/${courseId}/iris-settings`, { observe: 'response' }).pipe(
            map((res: HttpResponse<IrisCourseSettings>) => res.body ?? undefined),
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
     * Get the uncombined Iris settings for an exercise
     * @param exerciseId the id of the exercise
     */
    getUncombinedExerciseSettings(exerciseId: number): Observable<IrisExerciseSettings | undefined> {
        return this.http
            .get<IrisExerciseSettings>(`${this.resourceUrl}/exercises/${exerciseId}/raw-iris-settings`, { observe: 'response' })
            .pipe(map((res: HttpResponse<IrisExerciseSettings>) => res.body ?? undefined));
    }

    /**
     * Get the combined Iris settings for an exercise.
     * Uses caching to avoid unnecessary API calls for 5 minutes and ensures
     * that simultaneous requests for the same exercise ID reuse the same pending request.
     *
     * @param exerciseId the id of the exercise
     */
    getCombinedExerciseSettings(exerciseId: number): Observable<IrisExerciseSettings | undefined> {
        const now = Date.now();
        const cached = this.exerciseSettingsCache.get(exerciseId);
        const timestamp = this.exerciseCacheTimestamps.get(exerciseId);

        if (cached && timestamp && now - timestamp < IrisSettingsService.CACHE_DURATION) {
            return of(cached);
        }

        const pending = this.pendingExerciseRequests.get(exerciseId);
        if (pending) {
            return pending;
        }

        const request$ = this.http.get<IrisExerciseSettings>(`${this.resourceUrl}/exercises/${exerciseId}/iris-settings`, { observe: 'response' }).pipe(
            map((res: HttpResponse<IrisExerciseSettings>) => res.body ?? undefined),
            tap((settings) => {
                if (settings) {
                    this.exerciseSettingsCache.set(exerciseId, settings);
                    this.exerciseCacheTimestamps.set(exerciseId, Date.now());
                }
            }),
            finalize(() => this.pendingExerciseRequests.delete(exerciseId)),
            shareReplay(1),
        );

        this.pendingExerciseRequests.set(exerciseId, request$);
        return request$;
    }

    /**
     * Update the global Iris settings
     * @param settings the settings to set
     */
    setGlobalSettings(settings: IrisGlobalSettings): Observable<HttpResponse<IrisGlobalSettings>> {
        return this.http.put<IrisGlobalSettings>(`${this.resourceUrl}/admin/global-iris-settings`, settings, { observe: 'response' });
    }

    /**
     * Update the Iris settings for a course
     * @param courseId the id of the course
     * @param settings the settings to set
     */
    setCourseSettings(courseId: number, settings: IrisCourseSettings): Observable<HttpResponse<IrisCourseSettings>> {
        return this.http.put<IrisCourseSettings>(`${this.resourceUrl}/courses/${courseId}/raw-iris-settings`, settings, { observe: 'response' });
    }

    /**
     * Update the Iris settings for an exercise
     * @param exerciseId the id of the exercise
     * @param settings the settings to set
     */
    setExerciseSettings(exerciseId: number, settings: IrisExerciseSettings): Observable<HttpResponse<IrisExerciseSettings>> {
        return this.http.put<IrisExerciseSettings>(`${this.resourceUrl}/exercises/${exerciseId}/raw-iris-settings`, settings, { observe: 'response' });
    }

    /**
     * Get the available variants for a feature
     */
    getVariantsForFeature(feature: IrisSubSettingsType): Observable<IrisVariant[] | undefined> {
        return this.http.get<IrisVariant[]>(`${this.resourceUrl}/variants/${feature}`, { observe: 'response' }).pipe(map((res: HttpResponse<IrisVariant[]>) => res.body ?? []));
    }
}
