import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { IrisCourseSettings, IrisExerciseSettings, IrisGlobalSettings } from 'app/entities/iris/settings/iris-settings.model';
import { IrisModel } from 'app/entities/iris/settings/iris-model';

/**
 * Service for calling the Iris settings endpoints on the server
 */
@Injectable({ providedIn: 'root' })
export class IrisSettingsService {
    private http = inject(HttpClient);

    public resourceUrl = 'api';

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
     * Get the uncombined Iris settings for a programming exercise
     * @param exerciseId the id of the programming exercise
     */
    getUncombinedProgrammingExerciseSettings(exerciseId: number): Observable<IrisExerciseSettings | undefined> {
        return this.http
            .get<IrisExerciseSettings>(`${this.resourceUrl}/programming-exercises/${exerciseId}/raw-iris-settings`, { observe: 'response' })
            .pipe(map((res: HttpResponse<IrisExerciseSettings>) => res.body ?? undefined));
    }

    /**
     * Get the combined Iris settings for a programming exercise
     * @param exerciseId the id of the programming exercise
     */
    getCombinedProgrammingExerciseSettings(exerciseId: number): Observable<IrisExerciseSettings | undefined> {
        return this.http
            .get<IrisExerciseSettings>(`${this.resourceUrl}/programming-exercises/${exerciseId}/iris-settings`, { observe: 'response' })
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
     * Update the Iris settings for a programming exercise
     * @param exerciseId the id of the programming exercise
     * @param settings the settings to set
     */
    setProgrammingExerciseSettings(exerciseId: number, settings: IrisExerciseSettings): Observable<HttpResponse<IrisExerciseSettings>> {
        return this.http.put<IrisExerciseSettings>(`${this.resourceUrl}/programming-exercises/${exerciseId}/raw-iris-settings`, settings, { observe: 'response' });
    }

    /**
     * Get the global Iris settings
     */
    getIrisModels(): Observable<IrisModel[] | undefined> {
        return this.http.get<IrisModel[]>(`${this.resourceUrl}/iris/models`, { observe: 'response' }).pipe(map((res: HttpResponse<IrisModel[]>) => res.body ?? []));
    }
}
