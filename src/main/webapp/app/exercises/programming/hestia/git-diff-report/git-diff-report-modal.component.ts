import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';

@Component({
    selector: 'jhi-git-diff-report-modal',
    templateUrl: './git-diff-report-modal.component.html',
})
export class GitDiffReportModalComponent implements OnInit {
    @Input() report: ProgrammingExerciseGitDiffReport;
    @Input() diffForTemplateAndSolution = true;

    errorWhileFetchingRepos = false;
    firstCommitFileContentByPath: Map<string, string>;
    secondCommitFileContentByPath: Map<string, string>;

    title: string;

    constructor(
        protected activeModal: NgbActiveModal,
        private programmingExerciseService: ProgrammingExerciseService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
    ) {}

    ngOnInit(): void {
        if (this.diffForTemplateAndSolution) {
            this.loadFilesForTemplateAndSolution();
            this.title = 'artemisApp.programmingExercise.gitDiffReportModal.title';
        } else {
            this.loadRepositoryFilesForParticipations();
            this.title = 'artemisApp.programmingExercise.gitDiffReportModal.titleForSubmissions';
        }
    }

    private loadFilesForTemplateAndSolution() {
        this.fetchTemplateRepoFiles();
        this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(this.report.programmingExercise.id!).subscribe({
            next: (response: Map<string, string>) => {
                this.secondCommitFileContentByPath = response;
            },
            error: () => {
                this.errorWhileFetchingRepos = true;
            },
        });
    }

    private loadRepositoryFilesForParticipations() {
        if (this.report.participationIdForLeftCommit) {
            this.programmingExerciseParticipationService
                .getParticipationRepositoryFilesWithContentAtCommit(this.report.participationIdForLeftCommit!, this.report.leftCommitHash!)
                .subscribe({
                    next: (filesWithContent: Map<string, string>) => {
                        this.firstCommitFileContentByPath = filesWithContent;
                        this.fetchParticipationRepoFilesAtRightCommit();
                    },
                    error: () => {
                        this.errorWhileFetchingRepos = true;
                    },
                });
        } else {
            // if there is no left commit, we want to see the diff between the current submission and the template
            this.fetchTemplateRepoFiles();
            this.fetchParticipationRepoFilesAtRightCommit();
        }
    }

    private fetchTemplateRepoFiles() {
        this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(this.report.programmingExercise.id!).subscribe({
            next: (response: Map<string, string>) => {
                this.firstCommitFileContentByPath = response;
            },
            error: () => {
                this.errorWhileFetchingRepos = true;
            },
        });
    }

    private fetchParticipationRepoFilesAtRightCommit() {
        this.programmingExerciseParticipationService
            .getParticipationRepositoryFilesWithContentAtCommit(this.report.participationIdForRightCommit!, this.report.rightCommitHash!)
            .subscribe({
                next: (filesWithContent: Map<string, string>) => {
                    this.secondCommitFileContentByPath = filesWithContent;
                },
                error: () => {
                    this.errorWhileFetchingRepos = true;
                },
            });
    }

    close(): void {
        this.activeModal.dismiss();
    }
}
