import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import * as moment from 'moment';
import { createRequestOption } from 'app/shared';
import { Result } from 'app/entities/result';
import { Submission } from 'app/entities/submission';
import { Exercise } from 'app/entities/exercise';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';

export type EntityResponseType = HttpResponse<StudentParticipation>;
export type EntityArrayResponseType = HttpResponse<StudentParticipation[]>;

@Injectable({ providedIn: 'root' })
export class ParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/participations';

    constructor(private http: HttpClient) {}

    create(participation: StudentParticipation): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(participation);
        return this.http
            .post<StudentParticipation>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    update(participation: StudentParticipation): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(participation);
        return this.http
            .put<StudentParticipation>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    find(participationId: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentParticipation>(`${this.resourceUrl}/${participationId}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    findWithLatestResult(participationId: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentParticipation>(`${this.resourceUrl}/${participationId}/withLatestResult`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    /*
     * Finds one participation for the currently logged in user for the given exercise in the given course
     */
    findParticipation(exerciseId: number): Observable<EntityResponseType | null> {
        return this.http
            .get<StudentParticipation>(SERVER_API_URL + `api/exercises/${exerciseId}/participation`, { observe: 'response' })
            .map((res: EntityResponseType) => {
                if (typeof res === 'undefined' || res === null) {
                    return null;
                }
                return this.convertDateFromServer(res);
            });
    }

    findAllParticipationsByExercise(exerciseId: number, withLatestResult = false): Observable<EntityArrayResponseType> {
        const options = createRequestOption({ withLatestResult });
        return this.http
            .get<StudentParticipation[]>(SERVER_API_URL + `api/exercise/${exerciseId}/participations`, {
                params: options,
                observe: 'response',
            })
            .map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res));
    }

    delete(participationId: number, req?: any): Observable<HttpResponse<any>> {
        const options = createRequestOption(req);
        return this.http.delete<void>(`${this.resourceUrl}/${participationId}`, { params: options, observe: 'response' });
    }

    cleanupBuildPlan(participation: StudentParticipation): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(participation);
        return this.http
            .put<StudentParticipation>(`${this.resourceUrl}/${participation.id}/cleanupBuildPlan`, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertDateFromServer(res));
    }

    downloadArtifact(participationId: number) {
        return this.http.get(`${this.resourceUrl}/${participationId}/buildArtifact`, { responseType: 'blob' }).map(artifact => {
            return artifact;
        });
    }

    protected convertDateFromClient(participation: StudentParticipation): StudentParticipation {
        const copy: StudentParticipation = Object.assign({}, participation, {
            initializationDate: participation.initializationDate != null && moment(participation.initializationDate).isValid() ? participation.initializationDate.toJSON() : null,
        });
        return copy;
    }

    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.initializationDate = res.body.initializationDate != null ? moment(res.body.initializationDate) : null;
            res.body.results = this.convertResultsDateFromServer(res.body.results);
            res.body.submissions = this.convertSubmissionsDateFromServer(res.body.submissions);
            res.body.exercise = this.convertExerciseDateFromServer(res.body.exercise);
        }
        return res;
    }

    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((participation: StudentParticipation) => {
                this.convertParticipationDateFromServer(participation);
            });
        }
        return res;
    }

    protected convertExerciseDateFromServer(exercise: Exercise) {
        if (exercise !== null) {
            exercise.releaseDate = exercise.releaseDate != null ? moment(exercise.releaseDate) : null;
            exercise.dueDate = exercise.dueDate != null ? moment(exercise.dueDate) : null;
        }
        return exercise;
    }

    protected convertParticipationDateFromServer(participation: StudentParticipation) {
        participation.initializationDate = participation.initializationDate != null ? moment(participation.initializationDate) : null;
        participation.results = this.convertResultsDateFromServer(participation.results);
        participation.submissions = this.convertSubmissionsDateFromServer(participation.submissions);
        return participation;
    }

    public convertParticipationsDateFromServer(participations: StudentParticipation[]) {
        const convertedParticipations: StudentParticipation[] = [];
        if (participations != null && participations.length > 0) {
            participations.forEach((participation: StudentParticipation) => {
                convertedParticipations.push(this.convertParticipationDateFromServer(participation));
            });
        }
        return convertedParticipations;
    }

    protected convertResultsDateFromServer(results: Result[]) {
        const convertedResults: Result[] = [];
        if (results != null && results.length > 0) {
            results.forEach((result: Result) => {
                result.completionDate = result.completionDate != null ? moment(result.completionDate) : null;
                convertedResults.push(result);
            });
        }
        return convertedResults;
    }

    protected convertSubmissionsDateFromServer(submissions: Submission[]) {
        const convertedSubmissions: Submission[] = [];
        if (submissions != null && submissions.length > 0) {
            submissions.forEach((submission: Submission) => {
                if (submission !== null) {
                    submission.submissionDate = submission.submissionDate != null ? moment(submission.submissionDate) : null;
                    convertedSubmissions.push(submission);
                }
            });
        }
        return convertedSubmissions;
    }

    public mergeStudentParticipations(participations: StudentParticipation[]): StudentParticipation | null {
        if (participations && participations.length > 0) {
            if (participations[0].type === ParticipationType.STUDENT) {
                const combinedParticipation = new StudentParticipation();
                this.mergeResultsAndSubmissions(combinedParticipation, participations);
                return combinedParticipation;
            } else if (participations[0].type === ParticipationType.PROGRAMMING) {
                return this.mergeProgrammingParticipations(participations as ProgrammingExerciseStudentParticipation[]);
            }
        }
        return null;
    }

    private mergeProgrammingParticipations(participations: ProgrammingExerciseStudentParticipation[]): ProgrammingExerciseStudentParticipation {
        const combinedParticipation = new ProgrammingExerciseStudentParticipation();
        if (participations && participations.length > 0) {
            combinedParticipation.repositoryUrl = participations[0].repositoryUrl;
            combinedParticipation.buildPlanId = participations[0].buildPlanId;
            this.mergeResultsAndSubmissions(combinedParticipation, participations);
        }
        return combinedParticipation;
    }

    private mergeResultsAndSubmissions(combinedParticipation: StudentParticipation, participations: StudentParticipation[]) {
        combinedParticipation.id = participations[0].id;
        combinedParticipation.initializationState = participations[0].initializationState;
        combinedParticipation.initializationDate = participations[0].initializationDate;
        combinedParticipation.presentationScore = participations[0].presentationScore;
        combinedParticipation.student = participations[0].student;
        combinedParticipation.exercise = participations[0].exercise;

        participations.forEach(participation => {
            if (participation.results) {
                combinedParticipation.results = combinedParticipation.results ? combinedParticipation.results.concat(participation.results) : participation.results;
            }
            if (participation.submissions) {
                combinedParticipation.submissions = combinedParticipation.submissions
                    ? combinedParticipation.submissions.concat(participation.submissions)
                    : participation.submissions;
            }
        });

        // make sure that results and submissions are connected with the participation because some components need this
        if (combinedParticipation.results && combinedParticipation.results.length > 0) {
            combinedParticipation.results.forEach(result => {
                result.participation = combinedParticipation;
            });
        }
        if (combinedParticipation.submissions && combinedParticipation.submissions.length > 0) {
            combinedParticipation.submissions.forEach(submission => {
                submission.participation = combinedParticipation;
            });
        }
    }
}
