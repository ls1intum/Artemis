import { Injectable, inject } from '@angular/core';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
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

    public loadFilesForTemplateAndSolution(report: ProgrammingExerciseGitDiffReport): Observable<[Map<string, string>, Map<string, string>]> {
        return forkJoin([this.fetchTemplateRepoFiles(report), this.fetchSolutionRepoFiles(report)]);
    }

    public loadRepositoryFilesForParticipations(report: ProgrammingExerciseGitDiffReport): Observable<[Map<string, string>, Map<string, string>]> {
        if (report.participationIdForLeftCommit) {
            return forkJoin([this.fetchParticipationRepoFilesAtLeftCommit(report), this.fetchParticipationRepoFilesAtRightCommit(report)]);
        } else {
            return forkJoin([this.fetchTemplateRepoFiles(report), this.fetchParticipationRepoFilesAtRightCommit(report)]);
        }
    }

    private fetchTemplateRepoFiles(report: ProgrammingExerciseGitDiffReport): Observable<Map<string, string>> {
        return this.fetchCached(this.calculateTemplateCacheKey(report), () =>
            this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(report.programmingExercise.id!),
        );
    }

    private fetchSolutionRepoFiles(report: ProgrammingExerciseGitDiffReport): Observable<Map<string, string>> {
        return this.fetchCached(this.calculateSolutionCacheKey(report), () => {
            return this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(report.programmingExercise.id!);
        });
    }

    private fetchParticipationRepoFilesAtLeftCommit(report: ProgrammingExerciseGitDiffReport): Observable<Map<string, string>> {
        return this.fetchCached(report.leftCommitHash!, () => {
            return this.programmingExerciseParticipationService.getParticipationRepositoryFilesWithContentAtCommit(report.participationIdForLeftCommit!, report.leftCommitHash!);
        });
    }

    private fetchParticipationRepoFilesAtRightCommit(report: ProgrammingExerciseGitDiffReport): Observable<Map<string, string>> {
        return this.fetchCached(report.rightCommitHash!, () => {
            return this.programmingExerciseParticipationService.getParticipationRepositoryFilesWithContentAtCommit(report.participationIdForRightCommit!, report.rightCommitHash!);
        });
    }

    private fetchCached(cacheKey: string, fetchFiles: () => Observable<Map<string, string>>): Observable<Map<string, string>> {
        const cachedFiles = this.cachedRepositoryFiles.get(cacheKey);
        if (cachedFiles) {
            return of(cachedFiles);
        }
        return fetchFiles();
    }

    private calculateTemplateCacheKey(report: ProgrammingExerciseGitDiffReport): string {
        return `exercise-${report.programmingExercise.id!}-template`;
    }

    private calculateSolutionCacheKey(report: ProgrammingExerciseGitDiffReport): string {
        return `exercise-${report.programmingExercise.id!}-solution`;
    }
}
