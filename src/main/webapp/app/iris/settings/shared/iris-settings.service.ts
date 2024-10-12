import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { IrisCourseSettings, IrisExerciseSettings, IrisGlobalSettings } from 'app/entities/iris/settings/iris-settings.model';
import { IrisVariant } from 'app/entities/iris/settings/iris-variant';
import { IrisSubSettingsType } from 'app/entities/iris/settings/iris-sub-settings.model';

/**
 * Service for calling the Iris settings endpoints on the server
 */
@Injectable({ providedIn: 'root' })
export class IrisSettingsService {
    public resourceUrl = 'api';

    constructor(private http: HttpClient) {}

    /**
     * Get the global Iris settings
     */
    getGlobalSettings(): Observable<IrisGlobalSettings | undefined> {
        return this.http
            .get<IrisGlobalSettings>(`${this.resourceUrl}/iris/global-iris-settings`, { observe: 'response' })
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
     * Get the combined Iris settings for a course
     * @param courseId the id of the course
     */
    getCombinedCourseSettings(courseId: number): Observable<IrisCourseSettings | undefined> {
        return this.http
            .get<IrisCourseSettings>(`${this.resourceUrl}/courses/${courseId}/iris-settings`, { observe: 'response' })
            .pipe(map((res: HttpResponse<IrisCourseSettings>) => res.body ?? undefined));
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
     * Get the combined Iris settings for an exercise
     * @param exerciseId the id of the exercise
     */
    getCombinedExerciseSettings(exerciseId: number): Observable<IrisExerciseSettings | undefined> {
        return this.http
            .get<IrisExerciseSettings>(`${this.resourceUrl}/exercises/${exerciseId}/iris-settings`, { observe: 'response' })
            .pipe(map((res: HttpResponse<IrisExerciseSettings>) => res.body ?? undefined));
    }

    /**
     * Update the global Iris settings
     * @param settings the settings to set
     */
    setGlobalSettings(settings: IrisGlobalSettings): Observable<HttpResponse<IrisGlobalSettings>> {
        return this.http.put<IrisGlobalSettings>(`${this.resourceUrl}/admin/iris/global-iris-settings`, settings, { observe: 'response' });
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
        return this.http
            .get<IrisVariant[]>(`${this.resourceUrl}/iris/variants/${feature}`, { observe: 'response' })
            .pipe(map((res: HttpResponse<IrisVariant[]>) => res.body ?? []));
    }
}
