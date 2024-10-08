import { ChangeDetectionStrategy, Component, EventEmitter, OnInit, effect, inject, input, signal, untracked } from '@angular/core';
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
export class GitDiffReportModalComponent implements OnInit {
    private readonly activeModal = inject(NgbActiveModal);
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private readonly cachedRepositoryFilesService = inject(CachedRepositoryFilesService);

    readonly report = input.required<ProgrammingExerciseGitDiffReport>();
    readonly diffForTemplateAndSolution = input<boolean>(true);
    readonly cachedRepositoryFiles = input<Map<string, Map<string, string>>>(new Map<string, Map<string, string>>());
    readonly onFilesLoaded = new EventEmitter<void>();

    readonly errorWhileFetchingRepos = signal<boolean>(false);
    readonly leftCommitFileContentByPath = signal<Map<string, string> | undefined>(undefined);
    readonly rightCommitFileContentByPath = signal<Map<string, string> | undefined>(undefined);

    constructor() {
        effect(
            async () => {
                if (this.diffForTemplateAndSolution()) {
                    await this.loadFilesForTemplateAndSolution();
                } else {
                    await this.loadRepositoryFilesForParticipationsFromCacheIfAvailable();
                }
                this.onFilesLoaded.emit();
            },
            { allowSignalWrites: true },
        );
    }

    async ngOnInit() {
        this.onFilesLoaded.emit();
    }

    private async loadFilesForTemplateAndSolution() {
        await Promise.all([this.fetchSolutionRepoFiles(), this.fetchTemplateRepoFiles()]);
    }

    private async foo() {
        await Promise.all([this.fetchParticipationRepoFilesAtLeftCommit(), this.fetchParticipationRepoFilesAtRightCommit()]);
    }

    private async fetchTemplateRepoFiles(): Promise<void> {
        try {
            const key = this.calculateTemplateMapKey();
            const fileMap =
                (await firstValueFrom(this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(this.report().programmingExercise.id!))) ??
                new Map<string, string>();
            this.leftCommitFileContentByPath.set(fileMap);
            untracked(this.cachedRepositoryFiles).set(key, fileMap);
            this.cachedRepositoryFilesService.emitCachedRepositoryFiles(this.cachedRepositoryFiles());
        } catch (e) {
            this.errorWhileFetchingRepos.set(true);
        }
    }

    private async fetchSolutionRepoFiles(): Promise<void> {
        try {
            const fileMap =
                (await firstValueFrom(this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(this.report().programmingExercise.id!))) ??
                new Map<string, string>();
            this.rightCommitFileContentByPath.set(fileMap);
        } catch (e) {
            this.errorWhileFetchingRepos.set(true);
        }
    }

    private async loadRepositoryFilesForParticipationsFromCacheIfAvailable(): Promise<void> {
        if (this.report().participationIdForLeftCommit) {
            const key = this.report().leftCommitHash!;
            if (untracked(this.cachedRepositoryFiles).has(key)) {
                this.leftCommitFileContentByPath.set(untracked(this.cachedRepositoryFiles).get(key)!);
                await this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable();
            } else {
                await Promise.all([this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable(), this.fetchParticipationRepoFilesAtLeftCommit()]);
            }
        } else {
            // if there is no left commit, we want to see the diff between the current submission and the template
            await Promise.all([this.loadTemplateRepoFilesFromCacheIfAvailable(), await this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable()]);
        }
    }

    private async fetchParticipationRepoFilesAtLeftCommit(): Promise<void> {
        try {
            await Promise.all([
                async () => {
                    const fileMap =
                        (await firstValueFrom(
                            this.programmingExerciseParticipationService.getParticipationRepositoryFilesWithContentAtCommit(
                                this.report().participationIdForLeftCommit!,
                                this.report().leftCommitHash!,
                            ),
                        )) ?? new Map<string, string>();
                    this.leftCommitFileContentByPath.set(fileMap);
                    untracked(this.cachedRepositoryFiles).set(this.report().leftCommitHash!, fileMap);
                    this.cachedRepositoryFilesService.emitCachedRepositoryFiles(this.cachedRepositoryFiles());
                },
                this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable(),
            ]);
        } catch (e) {
            this.errorWhileFetchingRepos.set(true);
        }
    }

    private async loadTemplateRepoFilesFromCacheIfAvailable() {
        const key = this.calculateTemplateMapKey();
        if (untracked(this.cachedRepositoryFiles).has(key)) {
            this.leftCommitFileContentByPath.set(untracked(this.cachedRepositoryFiles).get(key)!);
        } else {
            await this.fetchTemplateRepoFiles();
        }
    }

    private async loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable(): Promise<void> {
        const key = this.report().rightCommitHash!;
        if (untracked(this.cachedRepositoryFiles).has(key)) {
            this.rightCommitFileContentByPath.set(untracked(this.cachedRepositoryFiles).get(key)!);
        } else {
            await this.fetchParticipationRepoFilesAtRightCommit();
        }
    }

    private async fetchParticipationRepoFilesAtRightCommit(): Promise<void> {
        try {
            const fileMap =
                (await firstValueFrom(
                    this.programmingExerciseParticipationService.getParticipationRepositoryFilesWithContentAtCommit(
                        this.report().participationIdForRightCommit!,
                        this.report().rightCommitHash!,
                    ),
                )) ?? new Map<string, string>();
            this.rightCommitFileContentByPath.set(fileMap);
            untracked(this.cachedRepositoryFiles).set(this.report().rightCommitHash!, fileMap);
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
