import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { map } from 'rxjs/operators';
import { Submission } from 'app/entities/submission.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';

export type EntityResponseType = HttpResponse<ExampleSubmission>;

@Injectable({ providedIn: 'root' })
export class ExampleSubmissionService {
    constructor(private http: HttpClient, private exerciseService: ExerciseService, private stringCountService: StringCountService) {}

    /**
     * Creates an example submission
     * @param exampleSubmission Example submission to create
     * @param exerciseId Id of the exercise to which it belongs
     */
    create(exampleSubmission: ExampleSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(exampleSubmission);
        return this.http
            .post<ExampleSubmission>(`api/exercises/${exerciseId}/example-submissions`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    /**
     * Updates an example submission
     * @param exampleSubmission Example submission to update
     * @param exerciseId Id of the exercise to which it belongs
     */
    update(exampleSubmission: ExampleSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(exampleSubmission);
        return this.http
            .put<ExampleSubmission>(`api/exercises/${exerciseId}/example-submissions`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    /**
     * Gets an example submission
     * @param exampleSubmissionId Id of example submission to get
     */
    get(exampleSubmissionId: number): Observable<EntityResponseType> {
        return this.http
            .get<ExampleSubmission>(`api/example-submissions/${exampleSubmissionId}`, { observe: 'response' })
            .pipe(map((res: HttpResponse<ExampleSubmission>) => this.convertResponse(res)));
    }

    /**
     * Deletes an example submission
     * @param exampleSubmissionId Id of example submission to delete
     */
    delete(exampleSubmissionId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`api/example-submissions/${exampleSubmissionId}`, { observe: 'response' });
    }

    /**
     * Imports an example submission
     * @param submissionId the id od the submission to be imported as an example submission
     * @param exerciseId the id of the corresponding exercise
     */
    import(submissionId: number, exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .post<ExampleSubmission>(
                `api/exercises/${exerciseId}/example-submissions/import/${submissionId}`,
                {},
                {
                    observe: 'response',
                },
            )
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: ExampleSubmission = this.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to ExampleSubmission.
     */
    private convertItemFromServer(exampleSubmission: ExampleSubmission): ExampleSubmission {
        return Object.assign({}, exampleSubmission);
    }

    /**
     * Convert a ExampleSubmission to a JSON which can be sent to the server.
     */
    private convert(exampleSubmission: ExampleSubmission): ExampleSubmission {
        const jsonCopy = Object.assign({}, exampleSubmission);
        if (jsonCopy.exercise) {
            jsonCopy.exercise = this.exerciseService.convertDateFromClient(jsonCopy.exercise);
            jsonCopy.exercise = this.exerciseService.setBonusPointsConstrainedByIncludedInOverallScore(jsonCopy.exercise!);
            jsonCopy.exercise.categories = this.exerciseService.stringifyExerciseCategories(jsonCopy.exercise);
        }
        return jsonCopy;
    }

    /**
     * Calculates the number of elements for the example submission
     *
     * @param submission associated with the example submission
     * @param exercise   needed to decide submission type
     * @returns number of words for text submission, or number of element for the modeling submission
     */
    getSubmissionSize(submission?: Submission, exercise?: Exercise): number {
        if (submission && exercise && exercise.type === ExerciseType.TEXT) {
            return this.stringCountService.countWords((submission as TextSubmission).text);
        } else if (submission && exercise && exercise.type === ExerciseType.MODELING) {
            const umlModel = JSON.parse((submission as ModelingSubmission).model!);
            return umlModel ? umlModel.elements?.length + umlModel.relationships?.length : 0;
        }
        return 0;
    }
}
