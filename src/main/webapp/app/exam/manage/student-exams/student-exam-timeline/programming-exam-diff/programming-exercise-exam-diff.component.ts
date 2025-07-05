import { Component, OnDestroy, OnInit, computed, inject, input, model, output, signal } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { GitDiffReportModalComponent } from 'app/programming/shared/git-diff-report/git-diff-report-modal/git-diff-report-modal.component';
import { NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { faCodeCompare } from '@fortawesome/free-solid-svg-icons';
import { Observable, Subject, Subscription, debounceTime, forkJoin, of } from 'rxjs';
import { CachedRepositoryFilesService } from 'app/programming/manage/services/cached-repository-files.service';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { CommitsInfoComponent } from 'app/programming/shared/commits-info/commits-info.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';
import { RepositoryDiffInformation, processRepositoryDiff } from 'app/programming/shared/utils/diff.utils';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { AlertService } from 'app/shared/service/alert.service';

@Component({
    selector: 'jhi-programming-exam-diff',
    templateUrl: './programming-exercise-exam-diff.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: ProgrammingExerciseExamDiffComponent }],
    imports: [IncludedInScoreBadgeComponent, CommitsInfoComponent, TranslateDirective, GitDiffLineStatComponent, NgbTooltip, ButtonComponent, ArtemisTranslatePipe],
})
export class ProgrammingExerciseExamDiffComponent extends ExamSubmissionComponent implements OnInit, OnDestroy {
    private alertService = inject(AlertService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private modalService = inject(NgbModal);
    private cachedRepositoryFilesService = inject(CachedRepositoryFilesService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    exercise = model.required<ProgrammingExercise>();
    previousSubmission = model<ProgrammingSubmission>();
    currentSubmission = model<ProgrammingSubmission>();
    studentParticipation = model<ProgrammingExerciseStudentParticipation>();
    submissions = model<ProgrammingSubmission[]>();
    exerciseIdSubject = model<Subject<number>>(new Subject<number>());
    cachedDiffInformation = input<Map<string, RepositoryDiffInformation>>(new Map<string, RepositoryDiffInformation>());
    cachedDiffInformationChange = output<Map<string, RepositoryDiffInformation>>();
    cachedDiffReportsChange = output<Map<string, RepositoryDiffInformation>>();

    isLoadingDiffReport: boolean;
    isLeftTemplate: boolean;
    leftKey: string;
    rightKey: string;
    addedLineCount = computed(() => this.diffInformation()?.totalLineChange.addedLineCount ?? 0);
    removedLineCount = computed(() => this.diffInformation()?.totalLineChange.removedLineCount ?? 0);
    cachedRepositoryFiles: Map<string, Map<string, string>> = new Map<string, Map<string, string>>();
    exerciseType = ExerciseType.PROGRAMMING;
    diffInformation = signal<RepositoryDiffInformation | undefined>(undefined);
    diffReady = signal<boolean>(false);

    private exerciseIdSubscription: Subscription;

    readonly FeatureToggle = FeatureToggle;
    readonly ButtonSize = ButtonSize;
    readonly faCodeCompare = faCodeCompare;
    readonly IncludedInOverallScore = IncludedInOverallScore;

    ngOnInit() {
        // we subscribe to the exercise id because this allows us to avoid reloading the diff report every time the user switches between submission timestamps
        this.cachedRepositoryFilesService.getCachedRepositoryFilesObservable().subscribe((cachedRepositoryFiles) => {
            this.cachedRepositoryFiles = cachedRepositoryFiles;
        });

        this.exerciseIdSubscription = this.exerciseIdSubject()
            .pipe(debounceTime(200))
            .subscribe(() => {
                const key = this.calculateMapKey();
                if (this.cachedDiffInformation().has(key)) {
                    this.diffInformation.set(this.cachedDiffInformation().get(key)!);
                    this.diffReady.set(true);
                } else {
                    this.fetchRepositoriesAndProcessDiff();
                }
            });
    }

    ngOnDestroy(): void {
        this.exerciseIdSubscription?.unsubscribe();
    }

    fetchRepositoriesAndProcessDiff(): void {
        if (this.previousSubmission()) {
            this.isLeftTemplate = false;
            this.leftKey = this.previousSubmission()?.commitHash ?? '';
        } else {
            this.isLeftTemplate = true;
            this.leftKey = this.generateRepositoryKeyForTemplate();
        }
        this.rightKey = this.currentSubmission()?.commitHash ?? '';

        let leftSubscription: Observable<Map<string, string> | undefined>;
        if (this.cachedRepositoryFiles.has(this.leftKey)) {
            leftSubscription = of(this.cachedRepositoryFiles.get(this.leftKey));
        } else {
            leftSubscription = this.fetchRepositoryFilesAtLeftCommit();
        }

        let rightSubscription: Observable<Map<string, string> | undefined>;
        if (this.cachedRepositoryFiles.has(this.rightKey)) {
            rightSubscription = of(this.cachedRepositoryFiles.get(this.rightKey));
        } else {
            rightSubscription = this.fetchRepositoryFilesAtRightCommit();
        }

        forkJoin([leftSubscription, rightSubscription]).subscribe(([left, right]) => {
            if (left && right) {
                this.cachedRepositoryFiles.set(this.leftKey, left);
                this.cachedRepositoryFiles.set(this.rightKey, right);
                this.processRepositoryDiff(left, right);
            } else {
                this.alertService.error('artemisApp.programmingExercise.repositoryFilesError');
            }
        });
    }

    fetchRepositoryFilesAtRightCommit(): Observable<Map<string, string> | undefined> {
        const exerciseId = this.exercise()?.id;
        const participationId = this.currentSubmission()?.participation?.id;
        const commitHash = this.currentSubmission()?.commitHash;

        if (!exerciseId || !participationId || !commitHash) {
            return of(undefined);
        }

        return this.programmingExerciseParticipationService.getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(
            exerciseId,
            participationId,
            commitHash,
            RepositoryType.USER,
        );
    }

    fetchRepositoryFilesAtLeftCommit(): Observable<Map<string, string> | undefined> {
        const exerciseId = this.exercise()?.id;

        if (!exerciseId) {
            return of(undefined);
        }

        if (this.isLeftTemplate) {
            return this.programmingExerciseService.getTemplateRepositoryTestFilesWithContent(exerciseId);
        } else {
            const participationId = this.previousSubmission()?.participation?.id;
            const commitHash = this.previousSubmission()?.commitHash;

            if (!participationId || !commitHash) {
                return of(undefined);
            }

            return this.programmingExerciseParticipationService.getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(
                exerciseId,
                participationId,
                commitHash,
                RepositoryType.USER,
            );
        }
    }

    async processRepositoryDiff(left: Map<string, string>, right: Map<string, string>) {
        // Set ready state to false when starting diff processing
        this.diffReady.set(false);

        this.diffInformation.set(await processRepositoryDiff(left, right));
        this.cachedDiffInformation().set(this.calculateMapKey(), this.diffInformation()!);
        this.cachedDiffInformationChange.emit(this.cachedDiffInformation());
        this.isLoadingDiffReport = false;

        // Set ready state to true when diff processing is complete
        this.diffReady.set(true);
    }

    /**
     * Shows the git-diff in a modal.
     */
    showGitDiff(): void {
        if (!this.cachedDiffInformation().has(this.calculateMapKey())) {
            return;
        }
        const modalRef = this.modalService.open(GitDiffReportModalComponent, { windowClass: GitDiffReportModalComponent.WINDOW_CLASS });
        modalRef.componentInstance.repositoryDiffInformation = signal(this.cachedDiffInformation().get(this.calculateMapKey())!);
        modalRef.componentInstance.diffForTemplateAndSolution = signal(false);
    }

    private calculateMapKey() {
        return JSON.stringify([this.previousSubmission()?.id, this.currentSubmission()?.id]);
    }

    private generateRepositoryKeyForTemplate() {
        return this.exercise().id + '-template';
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
