import { ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, OnInit, Output, signal } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming/programming-submission.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { GitDiffReportModalComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { Submission } from 'app/entities/submission.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { faCodeCompare } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ExamPageComponent } from 'app/exam/participate/exercises/exam-page.component';
import { Observable, Subject, Subscription, debounceTime, take } from 'rxjs';
import { LineStat } from 'app/exercises/programming/hestia/git-diff-report/git-diff-line-stat.component';

@Component({
    selector: 'jhi-programming-exam-diff',
    templateUrl: './programming-exercise-exam-diff.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: ProgrammingExerciseExamDiffComponent }],
})
export class ProgrammingExerciseExamDiffComponent extends ExamPageComponent implements OnInit, OnDestroy {
    @Input() exercise: ProgrammingExercise;
    @Input() previousSubmission: ProgrammingSubmission | undefined;
    @Input() currentSubmission: ProgrammingSubmission;
    @Input() studentParticipation: ProgrammingExerciseStudentParticipation;
    @Input() submissions: ProgrammingSubmission[];
    @Input() cachedDiffReports: Map<string, ProgrammingExerciseGitDiffReport> = new Map<string, ProgrammingExerciseGitDiffReport>();
    @Output() cachedDiffReportsChange = new EventEmitter<Map<string, ProgrammingExerciseGitDiffReport>>();
    @Input() exerciseIdSubject: Subject<number> = new Subject<number>();
    protected readonly lineStat = signal<LineStat>({
        addedLineCount: 0,
        removedLineCount: 0,
    });
    isLoadingDiffReport: boolean;

    private exerciseIdSubscription: Subscription;

    readonly FeatureToggle = FeatureToggle;
    readonly ButtonSize = ButtonSize;
    readonly faCodeCompare = faCodeCompare;
    readonly IncludedInOverallScore = IncludedInOverallScore;

    constructor(
        protected changeDetectorReference: ChangeDetectorRef,
        private programmingExerciseService: ProgrammingExerciseService,
        private modalService: NgbModal,
    ) {
        super(changeDetectorReference);
    }

    ngOnInit() {
        // we subscribe to the exercise id because this allows us to avoid reloading the diff report every time the user switches between submission timestamps
        this.exerciseIdSubscription = this.exerciseIdSubject.pipe(debounceTime(200)).subscribe(() => {
            // we cannot use a tuple of the ids as key because they are only compared by reference, so we have to use this workaround with a string
            const key = this.calculateMapKey();
            if (this.cachedDiffReports.has(key)) {
                const diffReport = this.cachedDiffReports.get(key)!;
                this.assignPropertiesToReport(diffReport);
            } else {
                this.loadGitDiffReport();
            }
        });
    }

    ngOnDestroy(): void {
        this.exerciseIdSubscription?.unsubscribe();
    }

    loadGitDiffReport() {
        this.isLoadingDiffReport = true;
        let subscription: Observable<ProgrammingExerciseGitDiffReport | undefined>;
        if (this.previousSubmission) {
            subscription = this.programmingExerciseService.getDiffReportForSubmissions(this.exercise.id!, this.previousSubmission.id!, this.currentSubmission.id!);
        } else {
            // if there is no previous submission, we want to see the diff between the current submission and the template
            subscription = this.programmingExerciseService.getDiffReportForSubmissionWithTemplate(this.exercise.id!, this.currentSubmission.id!);
        }
        const key = this.calculateMapKey();
        subscription.pipe(take(1)).subscribe((gitDiffReport: ProgrammingExerciseGitDiffReport | undefined) => {
            if (gitDiffReport) {
                this.assignPropertiesToReport(gitDiffReport);
                this.cachedDiffReports.set(key, gitDiffReport);
                this.cachedDiffReportsChange.emit(this.cachedDiffReports);
            }
            this.isLoadingDiffReport = false;
        });
    }

    private assignPropertiesToReport(gitDiffReport: ProgrammingExerciseGitDiffReport) {
        this.exercise.gitDiffReport = gitDiffReport;
        gitDiffReport.programmingExercise = this.exercise;
        gitDiffReport.participationIdForLeftCommit = this.previousSubmission?.participation?.id;
        gitDiffReport.participationIdForRightCommit = this.currentSubmission.participation?.id;
        gitDiffReport.leftCommitHash = this.previousSubmission?.commitHash;
        gitDiffReport.rightCommitHash = this.currentSubmission.commitHash;
    }

    /**
     * Shows the git-diff in a modal.
     */
    showGitDiff(): void {
        const modalRef = this.modalService.open(GitDiffReportModalComponent, { windowClass: GitDiffReportModalComponent.WINDOW_CLASS });
        const component: GitDiffReportModalComponent = modalRef.componentInstance;
        component.diffForTemplateAndSolution.set(false);
    }

    getSubmission(): Submission | undefined {
        return this.currentSubmission;
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    private calculateMapKey() {
        return JSON.stringify([this.previousSubmission?.id, this.currentSubmission.id!]);
    }
}
