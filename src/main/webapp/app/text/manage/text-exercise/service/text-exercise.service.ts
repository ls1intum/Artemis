import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { ExerciseServicable, ExerciseService } from 'app/exercise/services/exercise.service';
import { PlagiarismOptions } from 'app/plagiarism/shared/entities/PlagiarismOptions';
import { TutorEffort } from 'app/assessment/shared/entities/tutor-effort.model';
import { PlagiarismResultDTO } from 'app/plagiarism/shared/entities/PlagiarismResultDTO';

export type EntityResponseType = HttpResponse<TextExercise>;
export type EntityArrayResponseType = HttpResponse<TextExercise[]>;

@Injectable({ providedIn: 'root' })
export class TextExerciseService implements ExerciseServicable<TextExercise> {
    private http = inject(HttpClient);
    private exerciseService = inject(ExerciseService);

    private resourceUrl = 'api/text/text-exercises';

    /**
     * Store a new text exercise on the server.
     * @param textExercise that should be stored of type {TextExercise}
     */
    create(textExercise: TextExercise): Observable<EntityResponseType> {
        let copy = ExerciseService.convertExerciseDatesFromClient(textExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<TextExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Imports a text exercise by cloning the entity itself plus example solutions and example submissions
     *
     * @param adaptedSourceTextExercise The exercise that should be imported, including adapted values for the
     * new exercise. E.g. with another title than the original exercise. Old values that should get discarded
     * (like the old ID) will be handled by the server.
     */
    import(adaptedSourceTextExercise: TextExercise) {
        let copy = ExerciseService.convertExerciseDatesFromClient(adaptedSourceTextExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .post<TextExercise>(`${this.resourceUrl}/import/${adaptedSourceTextExercise.id}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Updates an existing text exercise.
     * @param textExercise that should be updated of type {TextExercise}
     * @param req optional request options
     */
    update(textExercise: TextExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = ExerciseService.convertExerciseDatesFromClient(textExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .put<TextExercise>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Finds the text exercise of the given exerciseId.
     * @param exerciseId of text exercise of type {number}
     * @param withPlagiarismDetectionConfig true if plagiarism detection context should be fetched with the exercise
     */
    find(exerciseId: number, withPlagiarismDetectionConfig: boolean = false): Observable<EntityResponseType> {
        return this.http
            .get<TextExercise>(`${this.resourceUrl}/${exerciseId}`, { observe: 'response', params: { withPlagiarismDetectionConfig: withPlagiarismDetectionConfig } })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Queries all text exercises for the given request options.
     * @param req optional request options
     */
    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<TextExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.processExerciseEntityArrayResponse(res)));
    }

    /**
     * Deletes the text exercise with the given id.
     * @param exerciseId of the text exercise of type {number}
     */
    delete(exerciseId: number): Observable<HttpResponse<any>> {
        return this.http.delete(`${this.resourceUrl}/${exerciseId}`, { observe: 'response' });
    }

    /**
     * Check plagiarism with JPlag
     *
     * @param exerciseId
     * @param options
     */
    checkPlagiarism(exerciseId: number, options?: PlagiarismOptions): Observable<PlagiarismResultDTO> {
        return this.http
            .get<PlagiarismResultDTO>(`${this.resourceUrl}/${exerciseId}/check-plagiarism`, {
                observe: 'response',
                params: {
                    ...options?.toParams(),
                },
            })
            .pipe(map((response: HttpResponse<PlagiarismResultDTO>) => response.body!));
    }

    /**
     * Get the latest plagiarism result for the exercise with the given ID.
     *
     * @param exerciseId
     */
    getLatestPlagiarismResult(exerciseId: number): Observable<PlagiarismResultDTO> {
        return this.http
            .get<PlagiarismResultDTO>(`${this.resourceUrl}/${exerciseId}/plagiarism-result`, {
                observe: 'response',
            })
            .pipe(map((response: HttpResponse<PlagiarismResultDTO>) => response.body!));
    }

    /**
     * Re-evaluates and updates an existing text exercise.
     *
     * @param textExercise that should be updated of type {TextExercise}
     * @param req optional request options
     */
    reevaluateAndUpdate(textExercise: TextExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        let copy = ExerciseService.convertExerciseDatesFromClient(textExercise);
        copy = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(copy);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return this.http
            .put<TextExercise>(`${this.resourceUrl}/${textExercise.id}/re-evaluate`, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.processExerciseEntityResponse(res)));
    }

    /**
     * Retrieves the tutor effort in assessing a specific text exercise
     * @param exerciseId the id of the exercise to check for
     * @param courseId the id of the course to check for
     */
    public calculateTutorEffort(exerciseId: number, courseId: number): Observable<TutorEffort[]> {
        return this.http
            .get<TutorEffort[]>(`api/assessment/courses/${courseId}/exercises/${exerciseId}/tutor-effort`, { observe: 'response' })
            .pipe(map((res: HttpResponse<TutorEffort[]>) => res.body!));
    }
}
