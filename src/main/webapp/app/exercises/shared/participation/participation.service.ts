import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { createRequestOption } from 'app/shared/util/request.util';
import { Exercise } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';

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

    constructor(private http: HttpClient, private submissionService: SubmissionService, private accountService: AccountService) {}

    update(exercise: Exercise, participation: StudentParticipation): Observable<EntityResponseType> {
        const copy = this.convertParticipationForServer(participation, exercise);
        return this.http
            .put<StudentParticipation>(SERVER_API_URL + `api/exercises/${exercise.id}/participations`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processParticipationEntityResponseType(res)));
    }

    updateIndividualDueDates(exercise: Exercise, participations: StudentParticipation[]): Observable<EntityArrayResponseType> {
        const copies = participations.map((participation) => this.convertParticipationForServer(participation, exercise));
        return this.http
            .put<StudentParticipation[]>(SERVER_API_URL + `api/exercises/${exercise.id}/participations/update-individual-due-date`, copies, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.processParticipationEntityArrayResponseType(res)));
    }

    private convertParticipationForServer(participation: StudentParticipation, exercise: Exercise): StudentParticipation {
        // make sure participation and exercise are connected, because this is expected by the server
        participation.exercise = ExerciseService.convertExerciseFromClient(exercise);
        return this.convertParticipationDatesFromClient(participation);
    }

    find(participationId: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentParticipation>(`${this.resourceUrl}/${participationId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processParticipationEntityResponseType(res)));
    }

    findWithLatestResult(participationId: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentParticipation>(`${this.resourceUrl}/${participationId}/withLatestResult`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processParticipationEntityResponseType(res)));
    }

    /*
     * Finds one participation for the currently logged-in user for the given exercise in the given course
     */
    findParticipationForCurrentUser(exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentParticipation>(SERVER_API_URL + `api/exercises/${exerciseId}/participation`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processParticipationEntityResponseType(res)));
    }

    findAllParticipationsByExercise(exerciseId: number, withLatestResult = false): Observable<EntityArrayResponseType> {
        const options = createRequestOption({ withLatestResult });
        return this.http
            .get<StudentParticipation[]>(SERVER_API_URL + `api/exercises/${exerciseId}/participations`, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: EntityArrayResponseType) => this.processParticipationEntityArrayResponseType(res)));
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
        const copy = this.convertParticipationDatesFromClient(participation);
        return this.http
            .put<StudentParticipation>(`${this.resourceUrl}/${participation.id}/cleanupBuildPlan`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertParticipationResponseDatesFromServer(res)));
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

    protected convertParticipationDatesFromClient(participation: StudentParticipation): StudentParticipation {
        // return a copy of the object
        return Object.assign({}, participation, {
            initializationDate: convertDateFromClient(participation.initializationDate),
            individualDueDate: convertDateFromClient(participation.individualDueDate),
        });
    }

    protected convertParticipationResponseDatesFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            ParticipationService.convertParticipationDatesFromServer(res.body);
            res.body.results = this.submissionService.convertResultArrayDatesFromServer(res.body.results);
            res.body.submissions = this.submissionService.convertSubmissionArrayDatesFromServer(res.body.submissions);
            res.body.exercise = ExerciseService.convertExerciseDatesFromServer(res.body.exercise);
        }
        return res;
    }

    protected convertParticipationResponseArrayDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((participation: StudentParticipation) => {
                ParticipationService.convertParticipationDatesFromServer(participation);
            });
        }
        return res;
    }

    /**
     * Converts the dates that are part of the participation into a usable format.
     *
     * Does not convert dates in dependant attributes (e.g. results, submissions)!
     * @param participation for which the dates should be converted into the dayjs format.
     */
    public static convertParticipationDatesFromServer(participation?: Participation) {
        if (participation) {
            participation.initializationDate = convertDateFromServer(participation.initializationDate);
            participation.individualDueDate = convertDateFromServer(participation.individualDueDate);
        }
        return participation;
    }

    public static convertParticipationArrayDatesFromServer(participations?: StudentParticipation[]) {
        const convertedParticipations: StudentParticipation[] = [];
        if (participations?.length) {
            participations.forEach((participation: StudentParticipation) => {
                convertedParticipations.push(ParticipationService.convertParticipationDatesFromServer(participation)!);
            });
        }
        return convertedParticipations;
    }

    public mergeStudentParticipations(participations: StudentParticipation[]): StudentParticipation[] {
        const mergedParticipations: StudentParticipation[] = [];

        if (participations?.length) {
            const nonTestRunParticipations = participations.filter((participation: StudentParticipation) => participation.testRun === false);
            const testRunParticipations = participations.filter((participation: StudentParticipation) => participation.testRun === true);

            if (participations[0].type === ParticipationType.STUDENT) {
                if (nonTestRunParticipations.length) {
                    const combinedParticipation = new StudentParticipation();
                    this.mergeResultsAndSubmissions(combinedParticipation, nonTestRunParticipations);
                    mergedParticipations.push(combinedParticipation);
                }
                if (nonTestRunParticipations.length) {
                    const combinedParticipationTestRun = new StudentParticipation();
                    this.mergeResultsAndSubmissions(combinedParticipationTestRun, testRunParticipations);
                    mergedParticipations.push(combinedParticipationTestRun);
                }
            } else if (participations[0].type === ParticipationType.PROGRAMMING) {
                if (nonTestRunParticipations.length) {
                    const combinedParticipation = this.mergeProgrammingParticipations(
                        participations.filter((participation: StudentParticipation) => participation.testRun === false) as ProgrammingExerciseStudentParticipation[],
                    );
                    mergedParticipations.push(combinedParticipation);
                }
                if (nonTestRunParticipations.length) {
                    const combinedParticipationTestRun = this.mergeProgrammingParticipations(
                        participations.filter((participation: StudentParticipation) => participation.testRun === true) as ProgrammingExerciseStudentParticipation[],
                    );
                    mergedParticipations.push(combinedParticipationTestRun);
                }
            }
        }
        return mergedParticipations;
    }

    private mergeProgrammingParticipations(participations: ProgrammingExerciseStudentParticipation[]): ProgrammingExerciseStudentParticipation {
        const combinedParticipation = new ProgrammingExerciseStudentParticipation();
        if (participations?.length) {
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
        combinedParticipation.individualDueDate = participations[0].individualDueDate;
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
        if (combinedParticipation.results?.length) {
            combinedParticipation.results.forEach((result) => {
                result.participation = combinedParticipation;
            });
        }
        if (combinedParticipation.submissions?.length) {
            combinedParticipation.submissions.forEach((submission) => {
                submission.participation = combinedParticipation;
            });
        }
    }

    public getSpecificStudentParticipation(studentParticipations: StudentParticipation[], testRun: boolean | undefined): StudentParticipation | undefined {
        return studentParticipations.filter((participation) => participation.testRun === testRun).first();
    }

    /**
     * This method bundles recurring conversion steps for Participation EntityArrayResponses.
     * @param participationRes
     * @private
     */
    private processParticipationEntityArrayResponseType(participationRes: EntityArrayResponseType): EntityArrayResponseType {
        this.convertParticipationResponseArrayDatesFromServer(participationRes);
        this.setAccessRightsParticipationEntityArrayResponseType(participationRes);
        return participationRes;
    }

    /**
     * This method bundles recurring conversion steps for Participation EntityResponses.
     * @param participationRes
     * @private
     */
    private processParticipationEntityResponseType(participationRes: EntityResponseType): EntityResponseType {
        this.convertParticipationResponseDatesFromServer(participationRes);
        this.setAccessRightsParticipationEntityResponseType(participationRes);
        return participationRes;
    }

    private setAccessRightsParticipationEntityResponseType(res: EntityResponseType): EntityResponseType {
        if (res.body?.exercise) {
            this.accountService.setAccessRightsForExercise(res.body.exercise);
        }
        return res;
    }

    private setAccessRightsParticipationEntityArrayResponseType(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((participation) => {
                if (participation.exercise) {
                    this.accountService.setAccessRightsForExercise(participation.exercise);
                }
            });
        }
        return res;
    }
}
