import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExampleParticipation } from 'app/exercise/shared/entities/participation/example-participation.model';
import { map } from 'rxjs/operators';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { StringCountService } from 'app/text/overview/service/string-count.service';

export type EntityResponseType = HttpResponse<ExampleParticipation>;

/**
 * DTO for creating and updating example participations.
 */
export interface ExampleParticipationInputDTO {
    id?: number;
    exerciseId: number;
    usedForTutorial?: boolean;
    assessmentExplanation?: string;
    textSubmissionText?: string;
    modelingSubmissionModel?: string;
    modelingExplanationText?: string;
}

@Injectable({ providedIn: 'root' })
export class ExampleParticipationService {
    private http = inject(HttpClient);
    private stringCountService = inject(StringCountService);

    private resourceUrl = 'api/assessment';

    /**
     * Creates an example participation
     * @param exampleParticipation Example participation to create
     * @param exerciseId Id of the exercise to which it belongs
     */
    create(exampleParticipation: ExampleParticipation, exerciseId: number): Observable<EntityResponseType> {
        const dto = this.toInputDTO(exampleParticipation, exerciseId);
        return this.http
            .post<ExampleParticipation>(`${this.resourceUrl}/exercises/${exerciseId}/example-participations`, dto, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    /**
     * Updates an example participation
     * @param exampleParticipation Example participation to update
     * @param exerciseId Id of the exercise to which it belongs
     */
    update(exampleParticipation: ExampleParticipation, exerciseId: number): Observable<EntityResponseType> {
        const dto = this.toInputDTO(exampleParticipation, exerciseId);
        return this.http
            .put<ExampleParticipation>(`${this.resourceUrl}/exercises/${exerciseId}/example-participations`, dto, {
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    /**
     * Prepare an example participation for assessment
     * @param exerciseId Id of the exercise to which it belongs
     * @param exampleParticipationId Id of the example participation to prepare
     */
    prepareForAssessment(exerciseId: number, exampleParticipationId: number): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}/exercises/${exerciseId}/example-participations/${exampleParticipationId}/prepare-assessment`, {}, { observe: 'response' });
    }

    /**
     * Gets an example participation
     * @param exampleParticipationId Id of example participation to get
     */
    get(exampleParticipationId: number): Observable<EntityResponseType> {
        return this.http
            .get<ExampleParticipation>(`${this.resourceUrl}/example-participations/${exampleParticipationId}`, { observe: 'response' })
            .pipe(map((res: HttpResponse<ExampleParticipation>) => this.convertResponse(res)));
    }

    /**
     * Deletes an example participation
     * @param exampleParticipationId Id of example participation to delete
     */
    delete(exampleParticipationId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/example-participations/${exampleParticipationId}`, { observe: 'response' });
    }

    /**
     * Imports an example participation
     * @param submissionId the id of the submission to be imported as an example participation
     * @param exerciseId the id of the corresponding exercise
     */
    import(submissionId: number, exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .post<ExampleParticipation>(
                `${this.resourceUrl}/exercises/${exerciseId}/example-participations/import/${submissionId}`,
                {},
                {
                    observe: 'response',
                },
            )
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: ExampleParticipation = this.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to ExampleParticipation.
     */
    private convertItemFromServer(exampleParticipation: ExampleParticipation): ExampleParticipation {
        return Object.assign({}, exampleParticipation);
    }

    /**
     * Convert an ExampleParticipation to an input DTO for sending to the server.
     */
    private toInputDTO(exampleParticipation: ExampleParticipation, exerciseId: number): ExampleParticipationInputDTO {
        const submission = this.getSubmission(exampleParticipation);
        const dto: ExampleParticipationInputDTO = {
            id: exampleParticipation.id,
            exerciseId: exerciseId,
            usedForTutorial: exampleParticipation.usedForTutorial,
            assessmentExplanation: exampleParticipation.assessmentExplanation,
        };

        // Add submission-specific content
        if (submission) {
            if ('text' in submission) {
                dto.textSubmissionText = (submission as TextSubmission).text;
            } else if ('model' in submission) {
                const modelingSubmission = submission as ModelingSubmission;
                dto.modelingSubmissionModel = modelingSubmission.model;
                dto.modelingExplanationText = modelingSubmission.explanationText;
            }
        }

        return dto;
    }

    /**
     * Gets the first submission from an example participation.
     * ExampleParticipation inherits from Participation which has a submissions array,
     * but for example participations there is typically just one submission.
     */
    getSubmission(exampleParticipation: ExampleParticipation): Submission | undefined {
        if (exampleParticipation.submissions && exampleParticipation.submissions.length > 0) {
            return exampleParticipation.submissions[0];
        }
        return undefined;
    }

    /**
     * Calculates the number of elements for the example participation's submission
     *
     * @param submission associated with the example participation
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
