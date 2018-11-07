import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import * as moment from 'moment';

import { Result } from './result.model';
import { createRequestOption } from 'app/shared';
import { Feedback } from 'app/entities/feedback';
import { Participation } from 'app/entities/participation';
import { ExerciseService } from 'app/entities/exercise';

export type EntityResponseType = HttpResponse<Result>;
export type EntityArrayResponseType = HttpResponse<Result[]>;

@Injectable({ providedIn: 'root' })
export class ResultService {
    private courseResourceUrl = SERVER_API_URL + 'api/courses';
    private resultResourceUrl = SERVER_API_URL + 'api/results';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    create(result: Result): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(result);
        // NOTE: we deviate from the standard URL scheme to avoid conflicts with a different POST request on results
        return this.http
            .post<Result>(SERVER_API_URL + 'api/manual-results', copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    update(result: Result): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(result);
        return this.http
            .put<Result>(this.resultResourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<Result>(`${this.resultResourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    findBySubmissionId(submissionId: number): Observable<EntityResponseType> {
        return this.http
            .get<Result>(`${this.resultResourceUrl}/submission/${submissionId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    findResultsForParticipation(
        courseId: number,
        exerciseId: number,
        participationId: number,
        req?: any
    ): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get(`${this.courseResourceUrl}/${courseId}/exercises/${exerciseId}/participations/${participationId}/results`, {
                params: options,
                observe: 'response'
            })
            .map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res));
    }

    getResultsForExercise(courseId: number, exerciseId: number, req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Result[]>(`${this.courseResourceUrl}/${courseId}/exercises/${exerciseId}/results`, {
                params: options,
                observe: 'response'
            })
            .map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res));
    }

    getFeedbackDetailsForResult(resultId: number): Observable<HttpResponse<Feedback[]>> {
        return this.http.get<Feedback[]>(`${this.resultResourceUrl}/${resultId}/details`, { observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resultResourceUrl}/${id}`, { observe: 'response' });
    }

    protected convertDateFromClient(result: Result): Result {
        const copy: Result = Object.assign({}, result, {
            completionDate: result.completionDate != null && moment(result.completionDate).isValid() ? result.completionDate.toJSON() : null
        });
        return copy;
    }

    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((result: Result) => {
                result.completionDate = result.completionDate != null ? moment(result.completionDate) : null;
                result.participation = this.convertParticipationDateFromServer(result.participation);
            });
        }
        return res;
    }

    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.completionDate = res.body.completionDate != null ? moment(res.body.completionDate) : null;
            res.body.participation = this.convertParticipationDateFromServer(res.body.participation);
        }
        return res;
    }

    convertParticipationDateFromServer(participation: Participation) {
        if (participation) {
            participation.initializationDate = participation.initializationDate != null ? moment(participation.initializationDate) : null;
            participation.exercise = this.exerciseService.convertExerciseDateFromServer(participation.exercise);
        }
        return participation;
    }
}
