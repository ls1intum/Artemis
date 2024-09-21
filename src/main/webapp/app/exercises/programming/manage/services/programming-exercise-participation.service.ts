import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { CommitInfo } from 'app/entities/programming/programming-submission.model';
import { Result } from 'app/entities/result.model';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';
import { createRequestOption } from 'app/shared/util/request.util';
import { Observable, map, tap } from 'rxjs';
import { VcsAccessLogDTO } from 'app/entities/vcs-access-log-entry.model';

export interface IProgrammingExerciseParticipationService {
    getLatestResultWithFeedback: (participationId: number, withSubmission: boolean) => Observable<Result | undefined>;
    getStudentParticipationWithLatestResult: (participationId: number) => Observable<ProgrammingExerciseStudentParticipation>;
    checkIfParticipationHasResult: (participationId: number) => Observable<boolean>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseParticipationService implements IProgrammingExerciseParticipationService {
    public resourceUrlParticipations = 'api/programming-exercise-participations/';
    public resourceUrl = 'api/programming-exercise/';

    constructor(
        private http: HttpClient,
        private entityTitleService: EntityTitleService,
        private accountService: AccountService,
    ) {}

    getLatestResultWithFeedback(participationId: number, withSubmission = false): Observable<Result | undefined> {
        const options = createRequestOption({ withSubmission });
        return this.http.get<Result | undefined>(this.resourceUrlParticipations + participationId + '/latest-result-with-feedbacks', { params: options }).pipe(
            tap((res) => {
                if (res?.participation?.exercise) {
                    this.sendTitlesToEntityTitleService(res?.participation);
                    this.accountService.setAccessRightsForExerciseAndReferencedCourse(res.participation.exercise);
                }
            }),
        );
    }

    getStudentParticipationWithLatestResult(participationId: number): Observable<ProgrammingExerciseStudentParticipation> {
        return this.http
            .get<ProgrammingExerciseStudentParticipation>(this.resourceUrlParticipations + participationId + '/student-participation-with-latest-result-and-feedbacks')
            .pipe(
                tap((res) => {
                    if (res.exercise) {
                        this.sendTitlesToEntityTitleService(res);
                        this.accountService.setAccessRightsForExerciseAndReferencedCourse(res.exercise);
                    }
                }),
            );
    }

    /**
     * Get the student participation with all results and feedbacks for the given participation id.
     * @param participationId of the participation to get the student participation for
     */
    getStudentParticipationWithAllResults(participationId: number): Observable<ProgrammingExerciseStudentParticipation> {
        return this.http.get<ProgrammingExerciseStudentParticipation>(this.resourceUrlParticipations + participationId + '/student-participation-with-all-results').pipe(
            tap((res) => {
                if (res.exercise) {
                    this.sendTitlesToEntityTitleService(res);
                    this.accountService.setAccessRightsForExerciseAndReferencedCourse(res.exercise);
                }
            }),
        );
    }

    checkIfParticipationHasResult(participationId: number): Observable<boolean> {
        return this.http.get<boolean>(this.resourceUrlParticipations + participationId + '/has-result');
    }

    resetRepository(participationId: number, gradedParticipationId?: number) {
        let params = new HttpParams();
        if (gradedParticipationId) {
            params = params.set('gradedParticipationId', gradedParticipationId.toString());
        }
        return this.http.put<void>(`${this.resourceUrlParticipations}${participationId}/reset-repository`, null, { observe: 'response', params });
    }

    sendTitlesToEntityTitleService(participation: Participation | undefined) {
        if (participation?.exercise) {
            const exercise = participation.exercise;
            this.entityTitleService.setExerciseTitle(exercise);

            if (exercise.course) {
                const course = exercise.course;
                this.entityTitleService.setTitle(EntityType.COURSE, [course.id], course.title);
            }
        }
    }

    /**
     * Get the repository files with content for a given participation id at a specific commit hash.
     * The current user needs to be at least an instructor in the course of the participation.
     * @param participationId of the participation to get the files for
     * @param commitId of the commit to get the files for
     */
    getParticipationRepositoryFilesWithContentAtCommit(participationId: number, commitId: string): Observable<Map<string, string> | undefined> {
        return this.http.get(`${this.resourceUrlParticipations}${participationId}/files-content/${commitId}`).pipe(
            map((res: HttpResponse<any>) => {
                // this mapping is required because otherwise the HttpResponse object would be parsed
                // to an arbitrary object (and not a map)
                return res && new Map(Object.entries(res));
            }),
        );
    }

    /**
     * Get the repository files with content for a given participation id at a specific commit hash. This is used for the commit details view.
     * The current user needs to be at least a teaching assistant in the course of the participation.
     * If the user is not a teaching assistant, the user needs to be in the team or the owner of the participation.
     * @param exerciseId of the exercise to get the files for
     * @param participationId of the participation to get the files for
     * @param commitId of the commit to get the files for
     * @param repositoryType of the participation to get the files for (optional)
     */
    getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(
        exerciseId: number,
        participationId: number | undefined,
        commitId: string,
        repositoryType?: string,
    ): Observable<Map<string, string> | undefined> {
        const params: { [key: string]: number | string } = {};
        if (repositoryType) {
            params['repositoryType'] = repositoryType;
        }
        if (participationId) {
            params['participationId'] = participationId;
        }
        return this.http.get(`${this.resourceUrl}${exerciseId}/files-content-commit-details/${commitId}`, { params: params }).pipe(
            map((res: HttpResponse<any>) => {
                // this mapping is required because otherwise the HttpResponse object would be parsed
                // to an arbitrary object (and not a map)
                return res && new Map(Object.entries(res));
            }),
        );
    }
    /**
     * Get the repository files with content for a given participation id at a specific commit hash.
     * The current user needs to be at least a instructor in the course of the participation.
     * @param participationId of the participation to get the commit infos for
     */
    retrieveCommitsInfoForParticipation(participationId: number): Observable<CommitInfo[]> {
        return this.http.get<CommitInfo[]>(`${this.resourceUrlParticipations}${participationId}/commits-info`);
    }

    /**
     * Get the vcs access log for a given participation id.
     * The current user needs to be at least an instructor in the course of the participation.
     * @param participationId of the participation to get the vcs Access log
     */
    getVcsAccessLogForParticipation(participationId: number): Observable<VcsAccessLogDTO[] | undefined> {
        return this.http
            .get<VcsAccessLogDTO[]>(`${this.resourceUrlParticipations}${participationId}/vcs-access-log`, { observe: 'response' })
            .pipe(map((res: HttpResponse<VcsAccessLogDTO[]>) => res.body ?? undefined));
    }

    /**
     * Get the vcs access log for a given exercise id and the repository type.
     * The current user needs to be at least a instructor in the course of the participation.
     * @param exerciseId      of the exercise to get the vcs Access log
     * @param repositoryType  of the repository of the exercise, to get the vcs Access log
     */
    getVcsAccessLogForRepository(exerciseId: number, repositoryType: string): Observable<VcsAccessLogDTO[] | undefined> {
        const params: { [key: string]: number | string } = {};
        if (repositoryType) {
            params['repositoryType'] = repositoryType;
        }
        return this.http
            .get<VcsAccessLogDTO[]>(`${this.resourceUrl}${exerciseId}/vcs-access-log/${repositoryType}`, { observe: 'response' })
            .pipe(map((res: HttpResponse<VcsAccessLogDTO[]>) => res.body ?? undefined));
    }

    /**
     * Get the repository files with content for a given participation id at a specific commit hash.
     * The current user needs to be at least a student in the course of the participation.
     * @param participationId of the participation to get the commit infos for
     */
    retrieveCommitHistoryForParticipation(participationId: number): Observable<CommitInfo[]> {
        return this.http.get<CommitInfo[]>(`${this.resourceUrlParticipations}${participationId}/commit-history`);
    }

    retrieveCommitHistoryForTemplateSolutionOrTests(exerciseId: number, repositoryType: string): Observable<CommitInfo[]> {
        return this.http.get<CommitInfo[]>(`${this.resourceUrl}${exerciseId}/commit-history/${repositoryType}`);
    }

    retrieveCommitHistoryForAuxiliaryRepository(exerciseId: number, repositoryType: string, auxiliaryRepositoryId: number): Observable<CommitInfo[]> {
        const params: { [key: string]: number | string } = {};
        params['repositoryId'] = auxiliaryRepositoryId;
        return this.http.get<CommitInfo[]>(`${this.resourceUrl}${exerciseId}/commit-history/${repositoryType}`, { params: params });
    }
}
