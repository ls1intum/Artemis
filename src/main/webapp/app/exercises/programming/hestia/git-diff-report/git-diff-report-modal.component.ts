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

    constructor(
        protected activeModal: NgbActiveModal,
        private programmingExerciseService: ProgrammingExerciseService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
    ) {}

    ngOnInit(): void {
        if (this.diffForTemplateAndSolution) {
            this.loadFilesForTemplateAndSolution();
        } else {
            this.loadRepositoryFilesForParticipations();
        }
    }

    private loadFilesForTemplateAndSolution() {
        this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(this.report.programmingExercise.id!).subscribe({
            next: (response: Map<string, string>) => {
                this.firstCommitFileContentByPath = response;
            },
            error: () => {
                this.errorWhileFetchingRepos = true;
            },
        });
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
        if (this.report.participationIdForFirstCommit) {
            this.programmingExerciseParticipationService
                .getParticipationRepositoryFilesWithContentAtCommit(this.report.participationIdForFirstCommit!, this.report.firstCommitHash)
                .subscribe({
                    next: (filesWithContent: Map<string, string>) => {
                        this.firstCommitFileContentByPath = filesWithContent;
                    },
                    error: () => {
                        this.errorWhileFetchingRepos = true;
                    },
                });
        } else {
            // if there is no first commit, we want to see the diff between the current submission and the template
            this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(this.report.programmingExercise.id!).subscribe({
                next: (response: Map<string, string>) => {
                    this.firstCommitFileContentByPath = response;
                },
                error: () => {
                    this.errorWhileFetchingRepos = true;
                },
            });
        }
        this.programmingExerciseParticipationService
            .getParticipationRepositoryFilesWithContentAtCommit(this.report.participationIdForSecondCommit!, this.report.secondCommitHash)
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
