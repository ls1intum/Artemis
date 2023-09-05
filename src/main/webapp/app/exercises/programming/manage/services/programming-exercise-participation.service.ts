import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { AccountService } from 'app/core/auth/account.service';
import { Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Result } from 'app/entities/result.model';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';
import { createRequestOption } from 'app/shared/util/request.util';
import { Observable, map, tap } from 'rxjs';
import { CommitInfo } from 'app/entities/programming-submission.model';

export interface IProgrammingExerciseParticipationService {
    getLatestResultWithFeedback: (participationId: number, withSubmission: boolean) => Observable<Result | undefined>;
    getStudentParticipationWithLatestResult: (participationId: number) => Observable<ProgrammingExerciseStudentParticipation>;
    checkIfParticipationHasResult: (participationId: number) => Observable<boolean>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseParticipationService implements IProgrammingExerciseParticipationService {
    public resourceUrl = 'api/programming-exercise-participations/';

    constructor(
        private http: HttpClient,
        private entityTitleService: EntityTitleService,
        private accountService: AccountService,
    ) {}

    getLatestResultWithFeedback(participationId: number, withSubmission = false): Observable<Result | undefined> {
        const options = createRequestOption({ withSubmission });
        return this.http.get<Result | undefined>(this.resourceUrl + participationId + '/latest-result-with-feedbacks', { params: options }).pipe(
            tap((res) => {
                if (res?.participation?.exercise) {
                    this.sendTitlesToEntityTitleService(res?.participation);
                    this.accountService.setAccessRightsForExerciseAndReferencedCourse(res.participation.exercise);
                }
            }),
        );
    }

    getStudentParticipationWithLatestResult(participationId: number): Observable<ProgrammingExerciseStudentParticipation> {
        return this.http.get<ProgrammingExerciseStudentParticipation>(this.resourceUrl + participationId + '/student-participation-with-latest-result-and-feedbacks').pipe(
            tap((res) => {
                if (res.exercise) {
                    this.sendTitlesToEntityTitleService(res);
                    this.accountService.setAccessRightsForExerciseAndReferencedCourse(res.exercise);
                }
            }),
        );
    }

    checkIfParticipationHasResult(participationId: number): Observable<boolean> {
        return this.http.get<boolean>(this.resourceUrl + participationId + '/has-result');
    }

    resetRepository(participationId: number, gradedParticipationId?: number) {
        let params = new HttpParams();
        if (gradedParticipationId) {
            params = params.set('gradedParticipationId', gradedParticipationId.toString());
        }
        return this.http.put<void>(`${this.resourceUrl}${participationId}/reset-repository`, null, { observe: 'response', params });
    }

    sendTitlesToEntityTitleService(participation: Participation | undefined) {
        if (participation?.exercise) {
            const exercise = participation.exercise;
            this.entityTitleService.setTitle(EntityType.EXERCISE, [exercise.id], exercise.title);

            if (exercise.course) {
                const course = exercise.course;
                this.entityTitleService.setTitle(EntityType.COURSE, [course.id], course.title);
            }
        }
    }

    /**
     * Get the repository files with content for a given participation id at a specific commit hash.
     * @param participationId of the participation to get the files for
     * @param commitId of the commit to get the files for
     */
    getParticipationRepositoryFilesWithContentAtCommit(participationId: number, commitId: string): Observable<Map<string, string> | undefined> {
        return this.http.get(`${this.resourceUrl}${participationId}/files-content/${commitId}`).pipe(
            map((res: HttpResponse<any>) => {
                // this mapping is required because otherwise the HttpResponse object would be parsed
                // to an arbitrary object (and not a map)
                return res && new Map(Object.entries(res));
            }),
        );
    }

    /**
     * Get the repository files with content for a given participation id at a specific commit hash.
     * @param participationId of the participation to get the commit infos for
     */
    retrieveCommitsInfoForParticipation(participationId: number): Observable<CommitInfo[]> {
        return this.http.get<CommitInfo[]>(`${this.resourceUrl}${participationId}/commits-info`);
    }
}
