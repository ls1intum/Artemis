import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
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
    report = input.required<ProgrammingExerciseGitDiffReport>();
    diffForTemplateAndSolution = input<boolean>(true);
    cachedRepositoryFiles = input<Map<string, Map<string, string>>>(new Map<string, Map<string, string>>());

    errorWhileFetchingRepos = signal<boolean>(false);
    leftCommitFileContentByPath = signal<Map<string, string> | undefined>(undefined);
    rightCommitFileContentByPath = signal<Map<string, string> | undefined>(undefined);

    /*
    private templateRepoFilesSubscription: Subscription;
    private solutionRepoFilesSubscription: Subscription;
    private participationRepoFilesAtLeftCommitSubscription: Subscription;
    private participationRepoFilesAtRightCommitSubscription: Subscription;
     */

    private readonly activeModal = inject(NgbActiveModal);
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private readonly cachedRepositoryFilesService = inject(CachedRepositoryFilesService);

    constructor() {
        effect(
            async () => {
                if (this.diffForTemplateAndSolution()) {
                    await this.loadFilesForTemplateAndSolution2();
                } else {
                    await this.loadRepositoryFilesForParticipationsFromCacheIfAvailable2();
                }
            },
            { allowSignalWrites: true },
        );
    }

    /* ngOnInit(): void {
        if (this.diffForTemplateAndSolution) {
            this.loadFilesForTemplateAndSolution();
        } else {
            this.loadRepositoryFilesForParticipationsFromCacheIfAvailable();
        }
    }

    ngOnDestroy(): void {
        this.templateRepoFilesSubscription?.unsubscribe();
        this.solutionRepoFilesSubscription?.unsubscribe();
        this.participationRepoFilesAtLeftCommitSubscription?.unsubscribe();
        this.participationRepoFilesAtRightCommitSubscription?.unsubscribe();
    }*/

    private async loadFilesForTemplateAndSolution2(): Promise<void> {
        await this.fetchTemplateRepoFiles2();
        await this.fetchSolutionRepoFiles2();
    }

    /*
    private loadFilesForTemplateAndSolution() {
        this.fetchTemplateRepoFiles();
        this.fetchSolutionRepoFiles();
    }*/

    private async fetchSolutionRepoFiles2(): Promise<void> {
        try {
            const solutionRepoFiles = await firstValueFrom(this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(this.report().programmingExercise.id!));
            this.rightCommitFileContentByPath.set(solutionRepoFiles);
        } catch (e) {
            this.errorWhileFetchingRepos.set(true);
        }
    }

    /*private fetchSolutionRepoFiles() {
        this.solutionRepoFilesSubscription = this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(this.report.programmingExercise.id!).subscribe({
            next: (response: Map<string, string>) => {
                this.rightCommitFileContentByPath = response;
            },
            error: () => {
                this.errorWhileFetchingRepos = true;
            },
        });
    }*/

    private async loadRepositoryFilesForParticipationsFromCacheIfAvailable2(): Promise<void> {
        if (this.report().participationIdForLeftCommit) {
            const key = this.report().leftCommitHash!;
            if (this.cachedRepositoryFiles().has(key)) {
                this.leftCommitFileContentByPath.set(this.cachedRepositoryFiles().get(key)!);
                await this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable2();
            } else {
                await this.fetchParticipationRepoFilesAtLeftCommit2();
            }
        } else {
            await this.loadTemplateRepoFilesFromCacheIfAvailable2();
            await this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable2();
        }
    }

    /*private loadRepositoryFilesForParticipationsFromCacheIfAvailable() {
        if (this.report.participationIdForLeftCommit) {
            const key = this.report.leftCommitHash!;
            if (this.cachedRepositoryFiles.has(key)) {
                this.leftCommitFileContentByPath = this.cachedRepositoryFiles.get(key)!;
                this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable();
            } else {
                this.fetchParticipationRepoFilesAtLeftCommit();
            }
        } else {
            // if there is no left commit, we want to see the diff between the current submission and the template
            this.loadTemplateRepoFilesFromCacheIfAvailable();
            this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable();
        }
    }*/

    private async fetchParticipationRepoFilesAtLeftCommit2(): Promise<void> {
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
            await this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable2();
        } catch (e) {
            this.errorWhileFetchingRepos.set(true);
        }
    }

    /*private fetchParticipationRepoFilesAtLeftCommit() {
        this.participationRepoFilesAtLeftCommitSubscription = this.programmingExerciseParticipationService
            .getParticipationRepositoryFilesWithContentAtCommit(this.report.participationIdForLeftCommit!, this.report.leftCommitHash!)
            .subscribe({
                next: (filesWithContent: Map<string, string>) => {
                    this.leftCommitFileContentByPath = filesWithContent;
                    this.cachedRepositoryFiles.set(this.report.leftCommitHash!, filesWithContent);
                    this.cachedRepositoryFilesService.emitCachedRepositoryFiles(this.cachedRepositoryFiles);
                    this.loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable();
                },
                error: () => {
                    this.errorWhileFetchingRepos = true;
                },
            });
    }*/

    private async loadTemplateRepoFilesFromCacheIfAvailable2(): Promise<void> {
        const key = this.calculateTemplateMapKey();
        if (this.cachedRepositoryFiles().has(key)) {
            this.leftCommitFileContentByPath.set(this.cachedRepositoryFiles().get(key)!);
        } else {
            await this.fetchTemplateRepoFiles2();
        }
    }

    /*private loadTemplateRepoFilesFromCacheIfAvailable() {
        const key = this.calculateTemplateMapKey();
        if (this.cachedRepositoryFiles.has(key)) {
            this.leftCommitFileContentByPath = this.cachedRepositoryFiles.get(key)!;
        } else {
            this.fetchTemplateRepoFiles();
        }
    }*/

    private async loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable2(): Promise<void> {
        const key = this.report().rightCommitHash!;
        if (this.cachedRepositoryFiles().has(key)) {
            this.rightCommitFileContentByPath.set(this.cachedRepositoryFiles().get(key)!);
        } else {
            await this.fetchParticipationRepoFilesAtRightCommit2();
        }
    }

    /*private loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable() {
        const key = this.report.rightCommitHash!;
        if (this.cachedRepositoryFiles.has(key)) {
            this.rightCommitFileContentByPath = this.cachedRepositoryFiles.get(key)!;
        } else {
            this.fetchParticipationRepoFilesAtRightCommit();
        }
    }*/

    private async fetchTemplateRepoFiles2(): Promise<void> {
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

    /*private fetchTemplateRepoFiles() {
        const key = this.calculateTemplateMapKey();
        this.templateRepoFilesSubscription = this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(this.report.programmingExercise.id!).subscribe({
            next: (response: Map<string, string>) => {
                this.leftCommitFileContentByPath = response;
                this.cachedRepositoryFiles.set(key, response);
                this.cachedRepositoryFilesService.emitCachedRepositoryFiles(this.cachedRepositoryFiles);
            },
            error: () => {
                this.errorWhileFetchingRepos = true;
            },
        });
    }*/

    private async fetchParticipationRepoFilesAtRightCommit2(): Promise<void> {
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

    /*private fetchParticipationRepoFilesAtRightCommit() {
        this.participationRepoFilesAtRightCommitSubscription = this.programmingExerciseParticipationService
            .getParticipationRepositoryFilesWithContentAtCommit(this.report.participationIdForRightCommit!, this.report.rightCommitHash!)
            .subscribe({
                next: (filesWithContent: Map<string, string>) => {
                    this.rightCommitFileContentByPath = filesWithContent;
                    this.cachedRepositoryFiles.set(this.report.rightCommitHash!, filesWithContent);
                    this.cachedRepositoryFilesService.emitCachedRepositoryFiles(this.cachedRepositoryFiles);
                },
                error: () => {
                    this.errorWhileFetchingRepos = true;
                },
            });
    }*/

    close(): void {
        this.activeModal.dismiss();
    }

    private calculateTemplateMapKey() {
        return this.report().programmingExercise.id! + '-template';
    }
}
