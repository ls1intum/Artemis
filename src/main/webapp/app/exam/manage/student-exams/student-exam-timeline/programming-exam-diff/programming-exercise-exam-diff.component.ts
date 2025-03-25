import { Component, OnDestroy, OnInit, inject, input, model, output, signal } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal.component';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { faCodeCompare } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseGitDiffReport } from 'app/programming/shared/entities/programming-exercise-git-diff-report.model';
import { Observable, Subject, Subscription, debounceTime, take } from 'rxjs';
import { CachedRepositoryFilesService } from 'app/programming/manage/services/cached-repository-files.service';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge.component';
import { CommitsInfoComponent } from 'app/programming/shared/commits-info/commits-info.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Submission } from 'app/entities/submission.model';
import { SubmissionVersion } from 'app/entities/submission-version.model';

@Component({
    selector: 'jhi-programming-exam-diff',
    templateUrl: './programming-exercise-exam-diff.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: ProgrammingExerciseExamDiffComponent }],
    imports: [IncludedInScoreBadgeComponent, CommitsInfoComponent, TranslateDirective, GitDiffLineStatComponent, NgbTooltip, ButtonComponent, ArtemisTranslatePipe],
})
export class ProgrammingExerciseExamDiffComponent extends ExamSubmissionComponent implements OnInit, OnDestroy {
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private modalService = inject(NgbModal);
    private cachedRepositoryFilesService = inject(CachedRepositoryFilesService);

    exercise = model.required<ProgrammingExercise>();
    previousSubmission = model<ProgrammingSubmission>();
    currentSubmission = model<ProgrammingSubmission>();
    studentParticipation = model<ProgrammingExerciseStudentParticipation>();
    submissions = model<ProgrammingSubmission[]>();
    cachedDiffReports = input<Map<string, ProgrammingExerciseGitDiffReport>>(new Map<string, ProgrammingExerciseGitDiffReport>());
    cachedDiffReportsChange = output<Map<string, ProgrammingExerciseGitDiffReport>>();
    exerciseIdSubject = model<Subject<number>>(new Subject<number>());

    isLoadingDiffReport: boolean;
    addedLineCount: number;
    removedLineCount: number;
    cachedRepositoryFiles: Map<string, Map<string, string>> = new Map<string, Map<string, string>>();
    exerciseType = ExerciseType.PROGRAMMING;

    private exerciseIdSubscription: Subscription;

    readonly FeatureToggle = FeatureToggle;
    readonly ButtonSize = ButtonSize;
    readonly faCodeCompare = faCodeCompare;
    readonly IncludedInOverallScore = IncludedInOverallScore;

    ngOnInit() {
        // we subscribe to the exercise id because this allows us to avoid reloading the diff report every time the user switches between submission timestamps
        this.exerciseIdSubscription = this.exerciseIdSubject()
            .pipe(debounceTime(200))
            .subscribe(() => {
                // we cannot use a tuple of the ids as key because they are only compared by reference, so we have to use this workaround with a string
                const key = this.calculateMapKey();
                if (this.cachedDiffReports().has(key)) {
                    const diffReport = this.cachedDiffReports().get(key)!;
                    this.assignPropertiesToReportAndCalculateLineCount(diffReport);
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
        const previousSubmission = this.previousSubmission();
        if (previousSubmission) {
            subscription = this.programmingExerciseService.getDiffReportForSubmissions(this.exercise().id!, previousSubmission.id!, this.currentSubmission()!.id!);
        } else {
            // if there is no previous submission, we want to see the diff between the current submission and the template
            subscription = this.programmingExerciseService.getDiffReportForSubmissionWithTemplate(this.exercise().id!, this.currentSubmission()!.id!);
        }
        const key = this.calculateMapKey();
        subscription.pipe(take(1)).subscribe((gitDiffReport: ProgrammingExerciseGitDiffReport | undefined) => {
            if (gitDiffReport) {
                this.assignPropertiesToReportAndCalculateLineCount(gitDiffReport);
                this.cachedDiffReports().set(key, gitDiffReport);
                this.cachedDiffReportsChange.emit(this.cachedDiffReports());
            }
            this.isLoadingDiffReport = false;
        });
    }

    private assignPropertiesToReportAndCalculateLineCount(gitDiffReport: ProgrammingExerciseGitDiffReport) {
        this.exercise().gitDiffReport = gitDiffReport;
        gitDiffReport.programmingExercise = this.exercise();
        gitDiffReport.participationIdForLeftCommit = this.previousSubmission()?.participation?.id;
        gitDiffReport.participationIdForRightCommit = this.currentSubmission()?.participation?.id;
        gitDiffReport.leftCommitHash = this.previousSubmission()?.commitHash;
        gitDiffReport.rightCommitHash = this.currentSubmission()?.commitHash;
        this.calculateLineCount(gitDiffReport);
    }

    /**
     * Calculates the added and removed line count for the given git-diff report.
     * In case the report doesn't contain any entries, the line count is set to 0.
     * @param gitDiffReport the report containing the git diff
     */
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
        const modalRef = this.modalService.open(GitDiffReportModalComponent, { windowClass: GitDiffReportModalComponent.WINDOW_CLASS });
        modalRef.componentInstance.report = signal(this.exercise().gitDiffReport);
        modalRef.componentInstance.diffForTemplateAndSolution = signal(false);
        modalRef.componentInstance.cachedRepositoryFiles = signal(this.cachedRepositoryFiles);
        this.cachedRepositoryFilesService.getCachedRepositoryFilesObservable().subscribe((cachedRepositoryFiles) => {
            this.cachedRepositoryFiles = cachedRepositoryFiles;
        });
    }

    private calculateMapKey() {
        return JSON.stringify([this.previousSubmission()?.id, this.currentSubmission()?.id]);
    }

    getExercise(): Exercise {
        return this.exercise();
    }

    getExerciseId(): number | undefined {
        return this.exercise().id;
    }

    getSubmission(): Submission | undefined {
        return this.currentSubmission();
    }

    hasUnsavedChanges(): boolean {
        return false;
    }

    setSubmissionVersion(submissionVersion: SubmissionVersion): void {
        this.submissionVersion = submissionVersion;
    }

    updateSubmissionFromView(): void {}

    updateViewFromSubmission(): void {}
}
