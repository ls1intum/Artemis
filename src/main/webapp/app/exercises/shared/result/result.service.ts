import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';
import * as moment from 'moment';
import { isMoment } from 'moment';
import { Result } from '../../../entities/result.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { Feedback } from 'app/entities/feedback.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

export type EntityResponseType = HttpResponse<Result>;
export type EntityArrayResponseType = HttpResponse<Result[]>;

export interface IResultService {
    find: (id: number) => Observable<EntityResponseType>;
    findBySubmissionId: (submissionId: number) => Observable<EntityResponseType>;
    getResultsForExercise: (courseId: number, exerciseId: number, req?: any) => Observable<EntityArrayResponseType>;
    getLatestResultWithFeedbacks: (particpationId: number) => Observable<HttpResponse<Result>>;
    getFeedbackDetailsForResult: (resultId: number) => Observable<HttpResponse<Feedback[]>>;
    delete: (id: number) => Observable<HttpResponse<void>>;
}

@Injectable({ providedIn: 'root' })
export class ResultService implements IResultService {
    private exerciseResourceUrl = SERVER_API_URL + 'api/exercises';
    private resultResourceUrl = SERVER_API_URL + 'api/results';
    private submissionResourceUrl = SERVER_API_URL + 'api/submissions';
    private participationResourceUrl = SERVER_API_URL + 'api/participations';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    /**
     * GET et the "id" result.
     * @param resultId - Id of the result
     */
    find(resultId: number): Observable<EntityResponseType> {
        return this.http
            .get<Result>(`${this.resultResourceUrl}/${resultId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    /**
     * Get the result for a submission id
     * @param submissionId - Id of the submission
     */
    findBySubmissionId(submissionId: number): Observable<EntityResponseType> {
        return this.http
            .get<Result>(`${this.resultResourceUrl}/submission/${submissionId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    /**
     * Get the successful results for an exercise, ordered ascending by build completion date.
     * @param exerciseId - The id of the exercise for which to retrieve the results
     * @param req - Request Options
     */
    getResultsForExercise(exerciseId: number, req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Result[]>(`${this.exerciseResourceUrl}/${exerciseId}/results`, {
                params: options,
                observe: 'response',
            })
            .map((res: EntityArrayResponseType) => this.convertArrayResponse(res));
    }

    /**
     * Get the build result details from CI service for the "id" result.
     * @param resultId - Id of the result to retrieve
     */
    getFeedbackDetailsForResult(resultId: number): Observable<HttpResponse<Feedback[]>> {
        return this.http.get<Feedback[]>(`${this.resultResourceUrl}/${resultId}/details`, { observe: 'response' });
    }

    /**
     * Get the latest result with feedbacks for the given participation.
     * @param particpationId - Id of the participation for which to retrieve the latest result.
     */
    getLatestResultWithFeedbacks(particpationId: number): Observable<HttpResponse<Result>> {
        return this.http.get<Result>(`${this.participationResourceUrl}/${particpationId}/latest-result`, { observe: 'response' });
    }

    /**
     * Delete the "id" result.
     * @param resultId - Id of the result to delete
     */
    delete(resultId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resultResourceUrl}/${resultId}`, { observe: 'response' });
    }

    /**
     * Create a new example result for the provided submission ID.
     *
     * @param submissionId The ID of the example submission for which a result should get created
     * @return The newly created (and empty) example result
     */
    createNewExampleResult(submissionId: number): Observable<HttpResponse<Result>> {
        return this.http.post<Result>(`${this.submissionResourceUrl}/${submissionId}/example-result`, null, { observe: 'response' });
    }

    public convertDateFromClient(result: Result): Result {
        const copy: Result = Object.assign({}, result, {
            completionDate:
                // Result completionDate is a moment object -> toJSON.
                result.completionDate != null && isMoment(result.completionDate)
                    ? result.completionDate.toJSON()
                    : // Result completionDate would be a valid date -> keep string.
                    result.completionDate && moment(result.completionDate).isValid()
                    ? result.completionDate
                    : // No valid date -> remove date.
                      null,
        });
        return copy;
    }

    /**
     * Convert all dates inside of the Response Result Array
     * @param res - Reply Body
     */
    protected convertArrayResponse(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((result: Result) => {
                result.completionDate = result.completionDate != null ? moment(result.completionDate) : null;
                result.participation = this.convertParticipationDateFromServer(result.participation! as StudentParticipation);
            });
        }
        return res;
    }

    /**
     * Create all Server-Times to Client Times
     * @param res - Reply Body
     */
    public convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.completionDate = res.body.completionDate != null ? moment(res.body.completionDate) : null;
            res.body.participation = this.convertParticipationDateFromServer(res.body.participation! as StudentParticipation);
        }
        return res;
    }

    /**
     * Convert participation date from server to client time.
     * @param participation - Participation that contains the date
     */
    convertParticipationDateFromServer(participation: StudentParticipation) {
        if (participation) {
            participation.initializationDate = participation.initializationDate != null ? moment(participation.initializationDate) : null;
            if (participation.exercise) {
                participation.exercise = this.exerciseService.convertExerciseDateFromServer(participation.exercise);
            }
        }
        return participation;
    }
}
