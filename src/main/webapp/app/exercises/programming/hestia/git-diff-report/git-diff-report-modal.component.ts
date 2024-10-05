import { Component, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { CachedRepositoryFilesService } from 'app/exercises/programming/manage/services/cached-repository-files.service';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-git-diff-report-modal',
    templateUrl: './git-diff-report-modal.component.html',
})
export class GitDiffReportModalComponent implements OnInit, OnDestroy {
    protected activeModal = inject(NgbActiveModal);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private cachedRepositoryFilesService = inject(CachedRepositoryFilesService);

    @Input() report: ProgrammingExerciseGitDiffReport;
    @Input() diffForTemplateAndSolution = true;
    @Input() cachedRepositoryFiles: Map<string, Map<string, string>> = new Map<string, Map<string, string>>();

    errorWhileFetchingRepos = false;
    leftCommitFileContentByPath: Map<string, string>;
    rightCommitFileContentByPath: Map<string, string>;

    private templateRepoFilesSubscription: Subscription;
    private solutionRepoFilesSubscription: Subscription;
    private participationRepoFilesAtLeftCommitSubscription: Subscription;
    private participationRepoFilesAtRightCommitSubscription: Subscription;

    ngOnInit(): void {
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
    }

    private loadFilesForTemplateAndSolution() {
        this.fetchTemplateRepoFiles();
        this.fetchSolutionRepoFiles();
    }

    private fetchSolutionRepoFiles() {
        this.solutionRepoFilesSubscription = this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(this.report.programmingExercise.id!).subscribe({
            next: (response: Map<string, string>) => {
                this.rightCommitFileContentByPath = response;
            },
            error: () => {
                this.errorWhileFetchingRepos = true;
            },
        });
    }

    private loadRepositoryFilesForParticipationsFromCacheIfAvailable() {
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
    }

    private fetchParticipationRepoFilesAtLeftCommit() {
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
    }

    private loadTemplateRepoFilesFromCacheIfAvailable() {
        const key = this.calculateTemplateMapKey();
        if (this.cachedRepositoryFiles.has(key)) {
            this.leftCommitFileContentByPath = this.cachedRepositoryFiles.get(key)!;
        } else {
            this.fetchTemplateRepoFiles();
        }
    }

    private loadParticipationRepoFilesAtRightCommitFromCacheIfAvailable() {
        const key = this.report.rightCommitHash!;
        if (this.cachedRepositoryFiles.has(key)) {
            this.rightCommitFileContentByPath = this.cachedRepositoryFiles.get(key)!;
        } else {
            this.fetchParticipationRepoFilesAtRightCommit();
        }
    }

    private fetchTemplateRepoFiles() {
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
    }

    private fetchParticipationRepoFilesAtRightCommit() {
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
    }

    close(): void {
        this.activeModal.dismiss();
    }

    private calculateTemplateMapKey() {
        return this.report.programmingExercise.id! + '-template';
    }
}
