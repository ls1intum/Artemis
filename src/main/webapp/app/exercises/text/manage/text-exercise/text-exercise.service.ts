import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';

import { TextExercise } from 'app/entities/text-exercise.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextPlagiarismResult } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismResult';
import { PlagiarismOptions } from 'app/exercises/shared/plagiarism/types/PlagiarismOptions';

export type EntityResponseType = HttpResponse<TextExercise>;
export type EntityArrayResponseType = HttpResponse<TextExercise[]>;
export type SubmissionComparisonDTO = {
    submissions: Array<TextSubmission>;
    distanceMetrics: Map<string, number>;
};

@Injectable({ providedIn: 'root' })
export class TextExerciseService {
    private resourceUrl = SERVER_API_URL + 'api/text-exercises';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    /**
     * Store a new text exercise on the server.
     * @param textExercise that should be stored of type {TextExercise}
     */
    create(textExercise: TextExercise): Observable<EntityResponseType> {
        const copy = this.exerciseService.convertDateFromClient(textExercise);
        return this.http
            .post<TextExercise>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Imports a text exercise by cloning the entity itself plus example solutions and example submissions
     *
     * @param adaptedSourceTextExercise The exercise that should be imported, including adapted values for the
     * new exercise. E.g. with another title than the original exercise. Old values that should get discarded
     * (like the old ID) will be handled by the server.
     */
    import(adaptedSourceTextExercise: TextExercise) {
        return this.http
            .post<TextExercise>(`${this.resourceUrl}/import/${adaptedSourceTextExercise.id}`, adaptedSourceTextExercise, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Updates an existing text exercise.
     * @param textExercise that should be updated of type {TextExercise}
     * @param req optional request options
     */
    update(textExercise: TextExercise, req?: any): Observable<EntityResponseType> {
        const options = createRequestOption(req);
        const copy = this.exerciseService.convertDateFromClient(textExercise);
        return this.http
            .put<TextExercise>(this.resourceUrl, copy, { params: options, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Finds the text exercise of the given id.
     * @param id of text exercise of type {number}
     */
    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<TextExercise>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.exerciseService.convertDateFromServer(res)));
    }

    /**
     * Queries all text exercises for the given request options.
     * @param req optional request options
     */
    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<TextExercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.exerciseService.convertDateArrayFromServer(res)));
    }

    /**
     * Deletes the text exercise with the given id.
     * @param id of the text exercise of type {number}
     */
    delete(id: number): Observable<HttpResponse<{}>> {
        return this.http.delete(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    /**
     * Check for plagiarism
     *
     * @param exerciseId of the text exercise
     */
    checkPlagiarism(exerciseId: number): Observable<HttpResponse<Array<SubmissionComparisonDTO>>> {
        return this.http.get<Array<SubmissionComparisonDTO>>(`${this.resourceUrl}/${exerciseId}/check-plagiarism`, { observe: 'response' });
    }

    /**
     * Check plagiarism with JPlag
     *
     * @param exerciseId
     * @param options
     */
    checkPlagiarismJPlag(exerciseId: number, options?: PlagiarismOptions): Observable<TextPlagiarismResult> {
        return this.http
            .get<TextPlagiarismResult>(`${this.resourceUrl}/${exerciseId}/check-plagiarism`, {
                observe: 'response',
                params: {
                    strategy: 'JPlag',
                    ...options?.toParams(),
                },
            })
            .pipe(map((response: HttpResponse<TextPlagiarismResult>) => response.body!));
    }
}
