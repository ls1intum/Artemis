import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, of } from 'rxjs';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';

@Injectable({
    providedIn: 'root',
})
export class RepositoryFilesService {
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);

    private readonly cachedRepositoryFiles = new Map<string, Map<string, string>>();

    public loadFilesForTemplateAndSolution(exerciseId: number): Observable<[Map<string, string>, Map<string, string>]> {
        return forkJoin([this.fetchTemplateRepoFiles(exerciseId), this.fetchSolutionRepoFiles(exerciseId)]);
    }

    public loadRepositoryFilesForParticipations(
        exerciseId: number,
        participationIdForLeftCommit: number,
        leftCommitHash: string,
        participationIdForRightCommit: number,
        rightCommitHash: string,
    ): Observable<[Map<string, string>, Map<string, string>]> {
        if (participationIdForLeftCommit) {
            return forkJoin([
                this.fetchParticipationRepoFiles(participationIdForLeftCommit, leftCommitHash),
                this.fetchParticipationRepoFiles(participationIdForRightCommit, rightCommitHash),
            ]);
        } else {
            return forkJoin([this.fetchTemplateRepoFiles(exerciseId), this.fetchParticipationRepoFiles(participationIdForRightCommit, rightCommitHash)]);
        }
    }

    private fetchTemplateRepoFiles(exerciseId: number): Observable<Map<string, string>> {
        return this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(exerciseId);
    }

    private fetchSolutionRepoFiles(exerciseId: number): Observable<Map<string, string>> {
        return this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(exerciseId);
    }

    private fetchParticipationRepoFiles(participationId: number, commitHash: string): Observable<Map<string, string>> {
        return this.fetchCached(commitHash, () => {
            return this.programmingExerciseParticipationService.getParticipationRepositoryFilesWithContentAtCommit(participationId, commitHash);
        });
    }

    private fetchCached(cacheKey: string, fetchFiles: () => Observable<Map<string, string>>): Observable<Map<string, string>> {
        const cachedFiles = this.cachedRepositoryFiles.get(cacheKey);
        if (cachedFiles) {
            return of(cachedFiles);
        }
        return fetchFiles();
    }
}
