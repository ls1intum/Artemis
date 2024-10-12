import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { CachedRepositoryFilesService } from 'app/exercises/programming/manage/services/cached-repository-files.service';
import { firstValueFrom } from 'rxjs';
import { GitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-git-diff-report-modal',
    templateUrl: './git-diff-report-modal.component.html',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [GitDiffReportComponent, TranslateDirective],
})
export class GitDiffReportModalComponent {
    static readonly WINDOW_CLASS = 'diff-view-modal';

    private readonly activeModal = inject(NgbActiveModal);
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private readonly cachedRepositoryFilesService = inject(CachedRepositoryFilesService);

    readonly report = input.required<ProgrammingExerciseGitDiffReport>();
    readonly diffForTemplateAndSolution = input<boolean>(true);
    readonly cachedRepositoryFiles = input<Map<string, Map<string, string>>>(new Map<string, Map<string, string>>());

    readonly errorWhileFetchingRepos = signal<boolean>(false);
    readonly leftCommitFileContentByPath = signal<Map<string, string> | undefined>(undefined);
    readonly rightCommitFileContentByPath = signal<Map<string, string> | undefined>(undefined);

    constructor() {
        effect(
            () => {
                // We call the signal here to ensure the effect always runs when the report changes.
                this.report();
                const diffBetweenTemplateAndSolution = this.diffForTemplateAndSolution();
                untracked(async () => {
                    if (diffBetweenTemplateAndSolution) {
                        await this.loadFilesForTemplateAndSolution();
                    } else {
                        await this.loadRepositoryFilesForParticipationsFromCacheIfAvailable();
                    }
                });
            },
            { allowSignalWrites: true },
        );
    }

    private async loadFilesForTemplateAndSolution(): Promise<void> {
        await this.fetchTemplateRepoFiles();
        await this.fetchSolutionRepoFiles();
    }

    private async fetchSolutionRepoFiles(): Promise<void> {
        try {
            const solutionRepoFiles = await firstValueFrom(this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(this.report().programmingExercise.id!));
            this.rightCommitFileContentByPath.set(solutionRepoFiles);
        } catch (e) {
            this.errorWhileFetchingRepos.set(true);
        }
    }

    private async loadRepositoryFilesForParticipationsFromCacheIfAvailable(): Promise<void> {
        if (this.report().participationIdForLeftCommit) {
            const key = this.report().leftCommitHash!;
            if (this.cachedRepositoryFiles().has(key)) {
                this.leftCommitFileContentByPath.set(this.cachedRepositoryFiles().get(key)!);
                await this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable();
            } else {
                await this.fetchParticipationRepoFilesAtLeftCommit();
            }
        } else {
            await this.loadTemplateRepoFilesFromCacheIfAvailable();
            await this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable();
        }
    }

    private async fetchParticipationRepoFilesAtLeftCommit(): Promise<void> {
        try {
            const filesWithContent =
                (await firstValueFrom(
                    this.programmingExerciseParticipationService.getParticipationRepositoryFilesWithContentAtCommit(
                        this.report().participationIdForLeftCommit!,
                        this.report().leftCommitHash!,
                    ),
                )) ?? new Map<string, string>();
            this.leftCommitFileContentByPath.set(filesWithContent);
            this.cachedRepositoryFiles().set(this.report().leftCommitHash!, filesWithContent);
            this.cachedRepositoryFilesService.emitCachedRepositoryFiles(this.cachedRepositoryFiles());
            await this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable();
        } catch (e) {
            this.errorWhileFetchingRepos.set(true);
        }
    }

    private async loadTemplateRepoFilesFromCacheIfAvailable(): Promise<void> {
        const key = this.calculateTemplateMapKey();
        if (this.cachedRepositoryFiles().has(key)) {
            this.leftCommitFileContentByPath.set(this.cachedRepositoryFiles().get(key)!);
        } else {
            await this.fetchTemplateRepoFiles();
        }
    }

    private async loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable(): Promise<void> {
        const key = this.report().rightCommitHash!;
        if (this.cachedRepositoryFiles().has(key)) {
            this.rightCommitFileContentByPath.set(this.cachedRepositoryFiles().get(key)!);
        } else {
            await this.fetchParticipationRepoFilesAtRightCommit();
        }
    }

    private async fetchTemplateRepoFiles(): Promise<void> {
        const key = this.calculateTemplateMapKey();
        try {
            const response =
                (await firstValueFrom(this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(this.report().programmingExercise.id!))) ??
                new Map<string, string>();
            this.leftCommitFileContentByPath.set(response);
            this.cachedRepositoryFiles().set(key, response);
            this.cachedRepositoryFilesService.emitCachedRepositoryFiles(this.cachedRepositoryFiles());
        } catch (e) {
            this.errorWhileFetchingRepos.set(true);
        }
    }

    private async fetchParticipationRepoFilesAtRightCommit(): Promise<void> {
        try {
            const filesWithContent =
                (await firstValueFrom(
                    this.programmingExerciseParticipationService.getParticipationRepositoryFilesWithContentAtCommit(
                        this.report().participationIdForRightCommit!,
                        this.report().rightCommitHash!,
                    ),
                )) ?? new Map<string, string>();
            this.rightCommitFileContentByPath.set(filesWithContent);
            this.cachedRepositoryFiles().set(this.report().rightCommitHash!, filesWithContent);
            this.cachedRepositoryFilesService.emitCachedRepositoryFiles(this.cachedRepositoryFiles());
        } catch (e) {
            this.errorWhileFetchingRepos.set(true);
        }
    }

    close(): void {
        this.activeModal.dismiss();
    }

    private calculateTemplateMapKey() {
        return this.report().programmingExercise.id! + '-template';
    }
}
