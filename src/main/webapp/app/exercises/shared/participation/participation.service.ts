import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import dayjs from 'dayjs';
import { createRequestOption } from 'app/shared/util/request.util';
import { Exercise } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { addUserIndependentRepositoryUrl } from 'app/overview/participation.utils';

export type EntityResponseType = HttpResponse<StudentParticipation>;
export type EntityArrayResponseType = HttpResponse<StudentParticipation[]>;
export type EntityBlobResponseType = HttpResponse<Blob>;
export type BuildArtifact = {
    fileName: string;
    fileContent: Blob;
};

@Injectable({ providedIn: 'root' })
export class ParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/participations';

    constructor(private http: HttpClient, private submissionService: SubmissionService) {}

    update(exerciseId: number, participation: StudentParticipation): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(participation);
        return this.http
            .put<StudentParticipation>(SERVER_API_URL + `api/exercises/${exerciseId}/participations`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)))
            .pipe(map((res: EntityResponseType) => this.adjustRepositoryUrlFromServer(res)));
    }

    find(participationId: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentParticipation>(`${this.resourceUrl}/${participationId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)))
            .pipe(map((res: EntityResponseType) => this.adjustRepositoryUrlFromServer(res)));
    }

    findWithLatestResult(participationId: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentParticipation>(`${this.resourceUrl}/${participationId}/withLatestResult`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)))
            .pipe(map((res: EntityResponseType) => this.adjustRepositoryUrlFromServer(res)));
    }

    /*
     * Finds one participation for the currently logged in user for the given exercise in the given course
     */
    findParticipation(exerciseId: number): Observable<EntityResponseType | null> {
        return this.http
            .get<StudentParticipation>(SERVER_API_URL + `api/exercises/${exerciseId}/participation`, { observe: 'response' })
            .pipe(
                map((res: EntityResponseType) => {
                    if (res == undefined) {
                        return null;
                    }
                    return this.convertDateFromServer(res);
                }),
            )
            .pipe(map((res: EntityResponseType) => this.adjustRepositoryUrlFromServer(res)));
    }

    findAllParticipationsByExercise(exerciseId: number, withLatestResult = false): Observable<EntityArrayResponseType> {
        const options = createRequestOption({ withLatestResult });
        return this.http
            .get<StudentParticipation[]>(SERVER_API_URL + `api/exercises/${exerciseId}/participations`, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)))
            .pipe(map((res: EntityArrayResponseType) => this.adjustRepositoryUrlArrayFromServer(res)));
    }

    delete(participationId: number, req?: any): Observable<HttpResponse<any>> {
        const options = createRequestOption(req);
        return this.http.delete<void>(`${this.resourceUrl}/${participationId}`, { params: options, observe: 'response' });
    }

    deleteForGuidedTour(participationId: number, req?: any): Observable<HttpResponse<any>> {
        const options = createRequestOption(req);
        return this.http.delete<void>(`api/guided-tour/participations/${participationId}`, { params: options, observe: 'response' });
    }

    cleanupBuildPlan(participation: StudentParticipation): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(participation);
        return this.http
            .put<StudentParticipation>(`${this.resourceUrl}/${participation.id}/cleanupBuildPlan`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    downloadArtifact(participationId: number): Observable<BuildArtifact> {
        return this.http.get(`${this.resourceUrl}/${participationId}/buildArtifact`, { observe: 'response', responseType: 'blob' }).pipe(
            map((res: EntityBlobResponseType) => {
                const fileNameCandidate = (res.headers.get('content-disposition') || '').split('filename=')[1];
                const fileName = fileNameCandidate ? fileNameCandidate.replace(/"/g, '') : 'artifact';
                return { fileName, fileContent: res.body } as BuildArtifact;
            }),
        );
    }

    protected convertDateFromClient(participation: StudentParticipation): StudentParticipation {
        // return a copy of the object
        return Object.assign({}, participation, {
            initializationDate: participation.initializationDate && dayjs(participation.initializationDate).isValid() ? participation.initializationDate.toJSON() : undefined,
        });
    }

    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.initializationDate = res.body.initializationDate ? dayjs(res.body.initializationDate) : undefined;
            res.body.results = this.submissionService.convertResultsDateFromServer(res.body.results);
            res.body.submissions = this.submissionService.convertSubmissionsDateFromServer(res.body.submissions);
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

    protected convertExerciseDateFromServer(exercise?: Exercise) {
        if (exercise != undefined) {
            exercise.releaseDate = exercise.releaseDate ? dayjs(exercise.releaseDate) : undefined;
            exercise.dueDate = exercise.dueDate ? dayjs(exercise.dueDate) : undefined;
        }
        return exercise;
    }

    protected convertParticipationDateFromServer(participation?: StudentParticipation) {
        if (participation != undefined) {
            participation.initializationDate = participation.initializationDate ? dayjs(participation.initializationDate) : undefined;
            participation.results = this.submissionService.convertResultsDateFromServer(participation.results);
            participation.submissions = this.submissionService.convertSubmissionsDateFromServer(participation.submissions);
        }
        return participation;
    }

    public convertParticipationsDateFromServer(participations?: StudentParticipation[]) {
        const convertedParticipations: StudentParticipation[] = [];
        if (participations != undefined && participations.length > 0) {
            participations.forEach((participation: StudentParticipation) => {
                convertedParticipations.push(this.convertParticipationDateFromServer(participation)!);
            });
        }
        return convertedParticipations;
    }

    protected adjustRepositoryUrlFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body?.type === ParticipationType.PROGRAMMING) {
            addUserIndependentRepositoryUrl(res.body);
        }
        return res;
    }

    protected adjustRepositoryUrlArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((participation: StudentParticipation) => {
                if (participation.type === ParticipationType.PROGRAMMING) {
                    addUserIndependentRepositoryUrl(participation);
                }
            });
        }
        return res;
    }

    public mergeStudentParticipations(participations: StudentParticipation[]): StudentParticipation | undefined {
        if (participations && participations.length > 0) {
            if (participations[0].type === ParticipationType.STUDENT) {
                const combinedParticipation = new StudentParticipation();
                this.mergeResultsAndSubmissions(combinedParticipation, participations);
                return combinedParticipation;
            } else if (participations[0].type === ParticipationType.PROGRAMMING) {
                return this.mergeProgrammingParticipations(participations as ProgrammingExerciseStudentParticipation[]);
            }
        }
        return undefined;
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
        combinedParticipation.exercise = participations[0].exercise;
        combinedParticipation.type = participations[0].type;
        combinedParticipation.testRun = participations[0].testRun;

        if (participations[0].student) {
            combinedParticipation.student = participations[0].student;
        }
        if (participations[0].team) {
            combinedParticipation.team = participations[0].team;
        }
        if (participations[0].participantIdentifier) {
            combinedParticipation.participantIdentifier = participations[0].participantIdentifier;
        }
        if (participations[0].participantName) {
            combinedParticipation.participantName = participations[0].participantName;
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
