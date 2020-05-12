import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';
import { map } from 'rxjs/operators';

import * as moment from 'moment';
import { createRequestOption } from 'app/shared/util/request-util';
import { Exercise } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { Submission } from 'app/entities/submission.model';
import { Result } from 'app/entities/result.model';

export type EntityResponseType = HttpResponse<StudentParticipation>;
export type EntityArrayResponseType = HttpResponse<StudentParticipation[]>;

@Injectable({ providedIn: 'root' })
export class ParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/participations';

    constructor(private http: HttpClient) {}

    /**
     *  Updates an existing participation.
     * @param participation - The participation to update
     */
    update(participation: StudentParticipation): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(participation);
        return this.http
            .put<StudentParticipation>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Get the participation for the given "participationId"
     * @param participationId - Id of the participation to retrieve
     */
    find(participationId: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentParticipation>(`${this.resourceUrl}/${participationId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Get the participation for the given "participationId" including its latest result.
     * @param participationId - Id of the participation to retrieve
     */
    findWithLatestResult(participationId: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentParticipation>(`${this.resourceUrl}/${participationId}/withLatestResult`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Finds one participation for the currently logged in user for the given exercise in the given course
     * @param exerciseId - The participationId of the exercise for which to retrieve the participation
     */
    findParticipation(exerciseId: number): Observable<EntityResponseType | null> {
        return this.http
            .get<StudentParticipation>(SERVER_API_URL + `api/exercises/${exerciseId}/participation`, { observe: 'response' })
            .pipe(
                map((res: EntityResponseType) => {
                    if (typeof res === 'undefined' || res === null) {
                        return null;
                    }
                    return this.convertDateFromServer(res);
                }),
            );
    }

    /**
     * Get all the participations for an exercise
     * @param exerciseId - The participationId of the exercise
     * @param withLatestResult - Whether the Result for the participations should also be fetched
     */
    findAllParticipationsByExercise(exerciseId: number, withLatestResult = false): Observable<EntityArrayResponseType> {
        const options = createRequestOption({ withLatestResult });
        return this.http
            .get<StudentParticipation[]>(SERVER_API_URL + `api/exercises/${exerciseId}/participations`, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    /**
     * Delete the "participationId" participation. This only works for student participations - other participations should not be deleted here!
     * @param participationId - The participationId of the participation to delete
     * @param req - Request Options
     */
    delete(participationId: number, req?: any): Observable<HttpResponse<any>> {
        const options = createRequestOption(req);
        return this.http.delete<void>(`${this.resourceUrl}/${participationId}`, { params: options, observe: 'response' });
    }

    /**
     * Delete the "participationId" participation of student participations for guided tutorials
     * @param participationId - The participationId of the participation to delete
     * @param req - Request Options
     */
    deleteForGuidedTour(participationId: number, req?: any): Observable<HttpResponse<any>> {
        const options = createRequestOption(req);
        return this.http.delete<void>(`api/guided-tour/participations/${participationId}`, { params: options, observe: 'response' });
    }

    /**
     * Remove the build plan of the ProgrammingExerciseStudentParticipation of the "participationId".
     * This only works for programming exercises.
     *
     * @param participation - The participationId of the ProgrammingExerciseStudentParticipation for which the build plan should be removed
     */
    cleanupBuildPlan(participation: StudentParticipation): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(participation);
        return this.http
            .put<StudentParticipation>(`${this.resourceUrl}/${participation.id}/cleanupBuildPlan`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Get the latest build artifact of a given programming exercise participation.
     * @param participationId - The participationId of the participation
     */
    downloadArtifact(participationId: number) {
        return this.http.get(`${this.resourceUrl}/${participationId}/buildArtifact`, { responseType: 'blob' }).pipe(
            map((artifact) => {
                return artifact;
            }),
        );
    }

    /**
     * Convert the initializationDate from client time to server time
     * @param participation - Participation who's date is converted
     */
    protected convertDateFromClient(participation: StudentParticipation): StudentParticipation {
        const copy: StudentParticipation = Object.assign({}, participation, {
            initializationDate: participation.initializationDate != null && moment(participation.initializationDate).isValid() ? participation.initializationDate.toJSON() : null,
        });
        return copy;
    }

    /**
     * Convert all dates from server Time to client time
     * @param res - Http response that holds the dates
     */
    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.initializationDate = res.body.initializationDate != null ? moment(res.body.initializationDate) : null;
            res.body.results = this.convertResultsDateFromServer(res.body.results);
            res.body.submissions = this.convertSubmissionsDateFromServer(res.body.submissions);
            res.body.exercise = this.convertExerciseDateFromServer(res.body.exercise);
        }
        return res;
    }

    /**
     * Convert all dates from server Time to client time for each Participation in the response
     * @param res - Http response that holds the dates
     */
    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((participation: StudentParticipation) => {
                this.convertParticipationDateFromServer(participation);
            });
        }
        return res;
    }

    /**
     * Convert the release & due date from server to client time
     * @param exercise - Exercise who's dates are converted
     */
    protected convertExerciseDateFromServer(exercise: Exercise) {
        if (exercise !== null) {
            exercise.releaseDate = exercise.releaseDate != null ? moment(exercise.releaseDate) : null;
            exercise.dueDate = exercise.dueDate != null ? moment(exercise.dueDate) : null;
        }
        return exercise;
    }

    /**
     * Convert the dates of a participation from server to client time
     * @param participation - Participation who's dates are converted
     */
    protected convertParticipationDateFromServer(participation: StudentParticipation) {
        participation.initializationDate = participation.initializationDate != null ? moment(participation.initializationDate) : null;
        participation.results = this.convertResultsDateFromServer(participation.results);
        participation.submissions = this.convertSubmissionsDateFromServer(participation.submissions);
        return participation;
    }

    /**
     * Convert the dates of Participations from server to client time
     * @param participations - Array of Participation who's dates are converted
     */
    public convertParticipationsDateFromServer(participations: StudentParticipation[]) {
        const convertedParticipations: StudentParticipation[] = [];
        if (participations != null && participations.length > 0) {
            participations.forEach((participation: StudentParticipation) => {
                convertedParticipations.push(this.convertParticipationDateFromServer(participation));
            });
        }
        return convertedParticipations;
    }

    /**
     * Convert the dates of Results from server to client time
     * @param results - Array of Results who's dates are converted
     */
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

    /**
     * Convert the dates of Submissions from server to client time
     * @param submissions - Array of Submissions who's dates are converted
     */
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

    /**
     * Merge all student participations into one participation
     * @param participations - Array of StudentParticipations that should be merged
     */
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

    /**
     * Merge all student participations in an Programming Exercise into one participation
     * @param participations - Array of ProgrammingExerciseStudentParticipation that should be merged
     */
    private mergeProgrammingParticipations(participations: ProgrammingExerciseStudentParticipation[]): ProgrammingExerciseStudentParticipation {
        const combinedParticipation = new ProgrammingExerciseStudentParticipation();
        if (participations && participations.length > 0) {
            combinedParticipation.repositoryUrl = participations[0].repositoryUrl;
            combinedParticipation.buildPlanId = participations[0].buildPlanId;
            this.mergeResultsAndSubmissions(combinedParticipation, participations);
        }
        return combinedParticipation;
    }

    /**
     * Merge Results with Submissions
     * @param combinedParticipation - StudentParticipation that the other participations are merged into
     * @param participations - Array of StudentParticipations that should be merged
     */
    private mergeResultsAndSubmissions(combinedParticipation: StudentParticipation, participations: StudentParticipation[]) {
        combinedParticipation.id = participations[0].id;
        combinedParticipation.initializationState = participations[0].initializationState;
        combinedParticipation.initializationDate = participations[0].initializationDate;
        combinedParticipation.presentationScore = participations[0].presentationScore;
        combinedParticipation.exercise = participations[0].exercise;

        if (participations[0].student) {
            combinedParticipation.student = participations[0].student;
        }
        if (participations[0].team) {
            combinedParticipation.team = participations[0].team;
        }

        participations.forEach((participation) => {
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
            combinedParticipation.results.forEach((result) => {
                result.participation = combinedParticipation;
            });
        }
        if (combinedParticipation.submissions && combinedParticipation.submissions.length > 0) {
            combinedParticipation.submissions.forEach((submission) => {
                submission.participation = combinedParticipation;
            });
        }
    }
}
