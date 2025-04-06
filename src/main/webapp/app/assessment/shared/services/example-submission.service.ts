import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { ExerciseService } from 'app/exercise/exercise.service';
import { map } from 'rxjs/operators';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { StringCountService } from 'app/text/overview/string-count.service';

export type EntityResponseType = HttpResponse<ExampleSubmission>;

@Injectable({ providedIn: 'root' })
export class ExampleSubmissionService {
    private http = inject(HttpClient);
    private stringCountService = inject(StringCountService);

    private resourceUrl = 'api/assessment';

    /**
     * Creates an example submission
     * @param exampleSubmission Example submission to create
     * @param exerciseId Id of the exercise to which it belongs
     */
    create(exampleSubmission: ExampleSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(exampleSubmission);
        return this.http
            .post<ExampleSubmission>(`${this.resourceUrl}/exercises/${exerciseId}/example-submissions`, copy, {
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
            .put<ExampleSubmission>(`${this.resourceUrl}/exercises/${exerciseId}/example-submissions`, copy, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    /**
     * Prepare an example submission for assessment
     * @param exerciseId Id of the exercise to which it belongs
     * @param exampleSubmissionId Id of the example submission to prepare
     */
    prepareForAssessment(exerciseId: number, exampleSubmissionId: number): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}/exercises/${exerciseId}/example-submissions/${exampleSubmissionId}/prepare-assessment`, {}, { observe: 'response' });
    }

    /**
     * Gets an example submission
     * @param exampleSubmissionId Id of example submission to get
     */
    get(exampleSubmissionId: number): Observable<EntityResponseType> {
        return this.http
            .get<ExampleSubmission>(`${this.resourceUrl}/example-submissions/${exampleSubmissionId}`, { observe: 'response' })
            .pipe(map((res: HttpResponse<ExampleSubmission>) => this.convertResponse(res)));
    }

    /**
     * Deletes an example submission
     * @param exampleSubmissionId Id of example submission to delete
     */
    delete(exampleSubmissionId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/example-submissions/${exampleSubmissionId}`, { observe: 'response' });
    }

    /**
     * Imports an example submission
     * @param submissionId the id od the submission to be imported as an example submission
     * @param exerciseId the id of the corresponding exercise
     */
    import(submissionId: number, exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .post<ExampleSubmission>(
                `${this.resourceUrl}/exercises/${exerciseId}/example-submissions/import/${submissionId}`,
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
            jsonCopy.exercise = ExerciseService.convertExerciseDatesFromClient(jsonCopy.exercise);
            jsonCopy.exercise = ExerciseService.setBonusPointsConstrainedByIncludedInOverallScore(jsonCopy.exercise!);
            jsonCopy.exercise.categories = ExerciseService.stringifyExerciseCategories(jsonCopy.exercise);
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
