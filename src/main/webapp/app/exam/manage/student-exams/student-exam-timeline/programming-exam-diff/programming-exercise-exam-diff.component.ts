import { ChangeDetectorRef, Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { Submission } from 'app/entities/submission.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { faEye } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ExamPageComponent } from 'app/exam/participate/exercises/exam-page.component';

@Component({
    selector: 'jhi-programming-exam-diff',
    templateUrl: './programming-exercise-exam-diff.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: ProgrammingExerciseExamDiffComponent }],
})
export class ProgrammingExerciseExamDiffComponent extends ExamPageComponent {
    @Input() exercise: ProgrammingExercise;
    @Input() previousSubmission: ProgrammingSubmission | undefined;
    @Input() currentSubmission: ProgrammingSubmission;
    @Input() studentParticipation: ProgrammingExerciseStudentParticipation;
    @Input() submissions: ProgrammingSubmission[];

    isLoadingDiffReport: boolean;
    addedLineCount: number;
    removedLineCount: number;
    readonly FeatureToggle = FeatureToggle;
    readonly ButtonSize = ButtonSize;
    readonly faEye = faEye;
    readonly IncludedInOverallScore = IncludedInOverallScore;

    constructor(
        protected changeDetectorReference: ChangeDetectorRef,
        private programmingExerciseService: ProgrammingExerciseService,
        private modalService: NgbModal,
    ) {
        super(changeDetectorReference);
    }

    loadGitDiffReport(): void {
        this.isLoadingDiffReport = true;
        let subscription;
        if (!this.currentSubmission) {
            return;
        }
        if (this.previousSubmission) {
            subscription = this.programmingExerciseService.getDiffReportForSubmissions(this.exercise.id!, this.previousSubmission.id!, this.currentSubmission.id!);
        } else {
            // if there is no previous submission, we want to see the diff between the current submission and the template
            subscription = this.programmingExerciseService.getDiffReportForSubmissionWithTemplate(this.exercise.id!, this.currentSubmission.id!);
        }
        subscription.subscribe((gitDiffReport: ProgrammingExerciseGitDiffReport | undefined) => {
            if (gitDiffReport) {
                this.exercise.gitDiffReport = gitDiffReport;
                gitDiffReport.programmingExercise = this.exercise;
                gitDiffReport.participationIdForLeftCommit = this.previousSubmission?.participation?.id;
                gitDiffReport.participationIdForRightCommit = this.currentSubmission.participation?.id;
                gitDiffReport.leftCommitHash = this.previousSubmission?.commitHash;
                gitDiffReport.rightCommitHash = this.currentSubmission.commitHash;
                this.calculateLineCount(gitDiffReport);
            }
            this.isLoadingDiffReport = false;
        });
    }

    private calculateLineCount(gitDiffReport: ProgrammingExerciseGitDiffReport) {
        this.addedLineCount =
            gitDiffReport?.entries
                ?.map((entry) => entry.lineCount)
                .filter((lineCount) => lineCount)
                .map((lineCount) => lineCount!)
                .reduce((lineCount1, lineCount2) => lineCount1 + lineCount2, 0) ?? 0;
        this.removedLineCount =
            gitDiffReport?.entries
                ?.map((entry) => entry.previousLineCount)
                .filter((lineCount) => lineCount)
                .map((lineCount) => lineCount!)
                .reduce((lineCount1, lineCount2) => lineCount1 + lineCount2, 0) ?? 0;
    }

    /**
     * Shows the git-diff in a modal.
     */
    showGitDiff(): void {
        const modalRef = this.modalService.open(GitDiffReportModalComponent, { size: 'xl' });
        modalRef.componentInstance.report = this.exercise.gitDiffReport;
        modalRef.componentInstance.diffForTemplateAndSolution = false;
    }

    getSubmission(): Submission | undefined {
        return this.currentSubmission;
    }
    getExercise(): Exercise {
        return this.exercise;
    }
}
