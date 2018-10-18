import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import * as moment from 'moment';

import { Participation } from './participation.model';
import { createRequestOption } from '../../shared';
import { Result } from '../../entities/result/result.model';
import { Submission } from '../../entities/submission/submission.model';
import { Exercise } from '../../entities/exercise/exercise.model';

export type EntityResponseType = HttpResponse<Participation>;
export type EntityArrayResponseType = HttpResponse<Participation[]>;

@Injectable()
export class ParticipationService {
    private resourceUrl = SERVER_API_URL + 'api/participations';

    constructor(private http: HttpClient) {}

    create(participation: Participation): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(participation);
        return this.http
            .post<Participation>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    update(participation: Participation): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(participation);
        return this.http
            .put<Participation>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<Participation>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    findWithLatestResult(id: number): Observable<EntityResponseType> {
        return this.http
            .get<Participation>(`${this.resourceUrl}/${id}/withLatestResult`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    /*
     * Finds one participation for the currently logged in user for the given exercise in the given course
     */
    findParticipation(courseId: number, exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<Participation>(SERVER_API_URL + `api/courses/${courseId}/exercises/${exerciseId}/participation`, { observe: 'response' })
            .map((res: EntityResponseType) => {
                if (typeof res === 'undefined' || res === null) {
                    return null;
                }
                return this.convertDateFromServer(res);
            });
    }

    findAllParticipationsByExercise(exerciseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Participation[]>(SERVER_API_URL + `api/exercise/${exerciseId}/participations`, { observe: 'response' })
            .map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res));
    }

    delete(id: number, req?: any): Observable<HttpResponse<any>> {
        const options = createRequestOption(req);
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { params: options, observe: 'response' });
    }

    repositoryWebUrl(participationId: number) {
        return this.http.get(`${this.resourceUrl}/${participationId}/repositoryWebUrl`, { responseType: 'text' }).map(repositoryWebUrl => {
            return { url: repositoryWebUrl };
        });
    }

    buildPlanWebUrl(participationId: number) {
        return this.http.get(`${this.resourceUrl}/${participationId}/buildPlanWebUrl`, { responseType: 'text' }).map(buildPlanWebUrl => {
            return { url: buildPlanWebUrl };
        });
    }

    downloadArtifact(id: number) {
        return this.http.get(`${this.resourceUrl}/${id}/buildArtifact`, { responseType: 'blob' }).map(artifact => {
            return artifact;
        });
    }

    convertDateFromClient(participation: Participation): Participation {
        const copy: Participation = Object.assign({}, participation, {
            initializationDate:
                participation.initializationDate != null && moment(participation.initializationDate).isValid()
                    ? participation.initializationDate.toJSON()
                    : null
        });
        return copy;
    }

    convertDateFromServer(res: EntityResponseType): EntityResponseType {
        res.body.initializationDate = res.body.initializationDate != null ? moment(res.body.initializationDate) : null;
        res.body.results = this.convertResultsDateFromServer(res.body.results);
        res.body.submissions = this.convertSubmissionsDateFromServer(res.body.submissions);
        res.body.exercise = this.convertExerciseDateFromServer(res.body.exercise);
        return res;
    }

    convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        res.body.forEach((participation: Participation) => {
            this.convertParticipationDateFromServer(participation);
        });
        return res;
    }

    convertExerciseDateFromServer(exercise: Exercise) {
        if (exercise !== null) {
            exercise.releaseDate = exercise.releaseDate != null ? moment(exercise.releaseDate) : null;
            exercise.dueDate = exercise.dueDate != null ? moment(exercise.dueDate) : null;
        }
        return exercise;
    }

    convertParticipationDateFromServer(participation: Participation) {
        participation.initializationDate = participation.initializationDate != null ? moment(participation.initializationDate) : null;
        participation.results = this.convertResultsDateFromServer(participation.results);
        participation.submissions = this.convertSubmissionsDateFromServer(participation.submissions);
        return participation;
    }

    convertParticipationsDateFromServer(participations: Participation[]) {
        const convertedParticipations: Participation[] = [];
        if (participations != null && participations.length > 0) {
            participations.forEach((participation: Participation) => {
                convertedParticipations.push(this.convertParticipationDateFromServer(participation));
            });
        }
        return convertedParticipations;
    }

    convertResultsDateFromServer(results: Result[]) {
        const convertedResults: Result[] = [];
        if (results != null && results.length > 0) {
            results.forEach((result: Result) => {
                result.completionDate = result.completionDate != null ? moment(result.completionDate) : null;
                convertedResults.push(result);
            });
        }
        return convertedResults;
    }

    convertSubmissionsDateFromServer(submissions: Submission[]) {
        const convertedSubmissions: Submission[] = [];
        if (submissions != null && submissions.length > 0) {
            submissions.forEach((submission: Submission) => {
                submission.submissionDate = submission.submissionDate != null ? moment(submission.submissionDate) : null;
                convertedSubmissions.push(submission);
            });
        }
        return convertedSubmissions;
    }
}
