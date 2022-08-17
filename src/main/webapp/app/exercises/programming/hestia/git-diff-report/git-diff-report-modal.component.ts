import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';

@Component({
    selector: 'jhi-git-diff-report-modal',
    templateUrl: './git-diff-report-modal.component.html',
})
export class GitDiffReportModalComponent implements OnInit {
    @Input()
    report: ProgrammingExerciseGitDiffReport;

    errorWhileFetchingRepos = false;
    templateFileContentByPath: Map<string, string>;
    solutionFileContentByPath: Map<string, string>;

    constructor(protected activeModal: NgbActiveModal, private programmingExerciseService: ProgrammingExerciseService) {}

    ngOnInit(): void {
        this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(this.report.programmingExercise.id!).subscribe({
            next: (response: Map<string, string>) => {
                this.templateFileContentByPath = response;
            },
            error: () => {
                this.errorWhileFetchingRepos = true;
            },
        });
        this.programmingExerciseService.getSolutionRepositoryTestFilesWithContent(this.report.programmingExercise.id!).subscribe({
            next: (response: Map<string, string>) => {
                this.solutionFileContentByPath = response;
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
