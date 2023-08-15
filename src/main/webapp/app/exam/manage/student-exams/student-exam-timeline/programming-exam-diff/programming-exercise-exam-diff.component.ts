import { ChangeDetectorRef, Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { faEye } from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { Submission } from 'app/entities/submission.model';
import { SubmissionVersion } from 'app/entities/submission-version.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';

@Component({
    selector: 'jhi-programming-exam-diff',
    templateUrl: './programming-exercise-exam-diff.component.html',
    styleUrls: ['./programming-exercise-exam-diff.component.scss'],
    providers: [{ provide: ExamSubmissionComponent, useExisting: ProgrammingExerciseExamDiffComponent }],
})
export class ProgrammingExerciseExamDiffComponent extends ExamSubmissionComponent {
    @Input() exercise: ProgrammingExercise;
    @Input() previousSubmission: ProgrammingSubmission | undefined;
    @Input() currentSubmission: ProgrammingSubmission;
    @Input() studentParticipation: ProgrammingExerciseStudentParticipation;
    @Input() commits: CommitInfo[];

    isLoadingDiffReport: boolean;
    addedLineCount: number;
    removedLineCount: number;
    readonly FeatureToggle = FeatureToggle;
    readonly ButtonSize = ButtonSize;
    readonly faEye = faEye;

    constructor(
        protected changeDetectorReference: ChangeDetectorRef,
        private programmingExerciseService: ProgrammingExerciseService,
        private modalService: NgbModal,
    ) {
        super(changeDetectorReference);
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    // TODO fix this
    loadGitDiffReport(): void {
        // this.isLoadingDiffReport = true;
        // let subscription;
        // if (!this.currentSubmission) {
        //     return;
        // }
        // if (this.previousSubmission) {
        //     subscription = this.programmingExerciseService.getDiffReportForSubmissions(this.exercise.id!, this.previousSubmission, this.currentSubmission)
        // } else {
        //     subscription = this.programmingExerciseService.getDiffReportForSubmissionWithTemplate(this.exercise.id!, this.currentSubmission)
        // }
        // subscription.subscribe((gitDiffReport: ProgrammingExerciseGitDiffReport | undefined) => {
        //     if (gitDiffReport) {
        //         console.log(gitDiffReport);
        //         this.exercise.gitDiffReport = gitDiffReport;
        //         gitDiffReport.programmingExercise = this.exercise;
        //         this.addedLineCount = gitDiffReport.entries
        //             .map((entry) => entry.lineCount)
        //             .filter((lineCount) => lineCount)
        //             .map((lineCount) => lineCount!)
        //             .reduce((lineCount1, lineCount2) => lineCount1 + lineCount2, 0);
        //         this.removedLineCount = gitDiffReport.entries
        //             .map((entry) => entry.previousLineCount)
        //             .filter((lineCount) => lineCount)
        //             .map((lineCount) => lineCount!)
        //             .reduce((lineCount1, lineCount2) => lineCount1 + lineCount2, 0);
        //         this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(this.exercise.id!).subscribe({
        //             next: (response: Map<string, string>) => {
        //                 this.templateFileContentByPath = response;
        //                 this.isLoading = this.solutionFileContentByPath === undefined;
        //             },
        //         });
        //         this.exerciseService.getSolutionRepositoryTestFilesWithContent(this.exercise.id!).subscribe({
        //             next: (response: Map<string, string>) => {
        //                 this.solutionFileContentByPath = response;
        //                 this.isLoading = this.templateFileContentByPath === undefined;
        //             },
        //         });
        //     },
        //     error: (error) => {
        //         this.isLoading = false;
        //         this.alertService.error(error.message);
        //     }
        //
        //     this.isLoadingDiffReport = false;
        // });
    }

    /**
     * Shows the git-diff in a modal.
     */
    showGitDiff(): void {
        const modalRef = this.modalService.open(GitDiffReportModalComponent, { size: 'xl' });
        modalRef.componentInstance.report = this.exercise.gitDiffReport;
    }

    getSubmission(): Submission | undefined {
        return undefined;
    }

    hasUnsavedChanges(): boolean {
        return false;
    }

    setSubmissionVersion(submissionVersion: SubmissionVersion): void {
        this.submissionVersion = submissionVersion;
    }

    updateViewFromSubmission(): void {}

    updateSubmissionFromView(): void {}
}
