import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';
import { IrisModel } from 'app/entities/iris/settings/iris-model';

type EntityResponseType = HttpResponse<IrisSettings>;

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
    getGlobalSettings(): Observable<IrisSettings | undefined> {
        return this.http
            .get<IrisSettings>(`${this.resourceUrl}/iris/global-iris-settings`, { observe: 'response' })
            .pipe(map((res: HttpResponse<IrisSettings>) => res.body ?? undefined));
    }

    /**
     * Get the uncombined Iris settings for a course
     * @param courseId the id of the course
     */
    getUncombinedCourseSettings(courseId: number): Observable<IrisSettings | undefined> {
        return this.http
            .get<IrisSettings>(`${this.resourceUrl}/courses/${courseId}/raw-iris-settings`, { observe: 'response' })
            .pipe(map((res: HttpResponse<IrisSettings>) => res.body ?? undefined));
    }

    /**
     * Get the combined Iris settings for a course
     * @param courseId the id of the course
     */
    getCombinedCourseSettings(courseId: number): Observable<IrisSettings | undefined> {
        return this.http
            .get<IrisSettings>(`${this.resourceUrl}/courses/${courseId}/iris-settings`, { observe: 'response' })
            .pipe(map((res: HttpResponse<IrisSettings>) => res.body ?? undefined));
    }

    /**
     * Get the uncombined Iris settings for a programming exercise
     * @param exerciseId the id of the programming exercise
     */
    getUncombinedProgrammingExerciseSettings(exerciseId: number): Observable<IrisSettings | undefined> {
        return this.http
            .get<IrisSettings>(`${this.resourceUrl}/programming-exercises/${exerciseId}/raw-iris-settings`, { observe: 'response' })
            .pipe(map((res: HttpResponse<IrisSettings>) => res.body ?? undefined));
    }

    /**
     * Get the combined Iris settings for a programming exercise
     * @param exerciseId the id of the programming exercise
     */
    getCombinedProgrammingExerciseSettings(exerciseId: number): Observable<IrisSettings | undefined> {
        return this.http
            .get<IrisSettings>(`${this.resourceUrl}/programming-exercises/${exerciseId}/iris-settings`, { observe: 'response' })
            .pipe(map((res: HttpResponse<IrisSettings>) => res.body ?? undefined));
    }

    /**
     * Update the global Iris settings
     * @param settings the settings to set
     */
    setGlobalSettings(settings: IrisSettings): Observable<EntityResponseType> {
        return this.http.put<IrisSettings>(`${this.resourceUrl}/admin/iris/global-iris-settings`, settings, { observe: 'response' });
    }

    /**
     * Update the Iris settings for a course
     * @param courseId the id of the course
     * @param settings the settings to set
     */
    setCourseSettings(courseId: number, settings: IrisSettings): Observable<EntityResponseType> {
        return this.http.put<IrisSettings>(`${this.resourceUrl}/courses/${courseId}/raw-iris-settings`, settings, { observe: 'response' });
    }

    /**
     * Update the Iris settings for a programming exercise
     * @param exerciseId the id of the programming exercise
     * @param settings the settings to set
     */
    setProgrammingExerciseSettings(exerciseId: number, settings: IrisSettings): Observable<EntityResponseType> {
        return this.http.put<IrisSettings>(`${this.resourceUrl}/programming-exercises/${exerciseId}/raw-iris-settings`, settings, { observe: 'response' });
    }

    /**
     * Get the global Iris settings
     */
    getIrisModels(): Observable<IrisModel[] | undefined> {
        return this.http.get<IrisModel[]>(`${this.resourceUrl}/iris/models`, { observe: 'response' }).pipe(map((res: HttpResponse<IrisModel[]>) => res.body ?? []));
    }
}
