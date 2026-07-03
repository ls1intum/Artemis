import { Component, HostListener, OnDestroy, OnInit, inject, input, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/foundation/service/alert.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { TeamSubmissionSyncComponent } from 'app/exercise/team-submission-sync/team-submission-sync.component';
import { TeamParticipateInfoBoxComponent } from 'app/exercise/team/team-participate/team-participate-info-box.component';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { TextEditorService } from 'app/text/overview/service/text-editor.service';
import dayjs from 'dayjs/esm';
import { Subject, Subscription, merge } from 'rxjs';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { debounceTime, distinctUntilChanged, map, skip } from 'rxjs/operators';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { ComponentCanDeactivate } from 'app/foundation/guard/can-deactivate.model';
import { Feedback, buildFeedbackTextForReview } from 'app/assessment/shared/entities/feedback.model';
import { hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { StringCountService } from 'app/text/overview/service/string-count.service';
import { AccountService } from 'app/core/auth/account.service';
import { getFirstResultWithComplaint, getLatestSubmissionResult, setLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { getUnreferencedFeedback, isAthenaAIResult } from 'app/exercise/result/result.utils';
import { onError } from 'app/foundation/util/global.utils';
import { Course } from 'app/course/shared/entities/course.model';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronDown, faCircleNotch, faEye } from '@fortawesome/free-solid-svg-icons';
import { MAX_SUBMISSION_TEXT_LENGTH } from 'app/foundation/constants/input.constants';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ResizeableContainerComponent } from 'app/shared-ui/resizeable-container/resizeable-container.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { TextResultComponent } from '../text-result/text-result.component';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { UpperCasePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/foundation/pipes/html-for-markdown.pipe';
import { onTextEditorTab } from 'app/foundation/util/text.utils';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-text-editor',
    templateUrl: './text-editor.component.html',
    providers: [ParticipationService],
    styleUrls: ['./text-editor.component.scss'],
    imports: [
        ResizeableContainerComponent,
        TeamParticipateInfoBoxComponent,
        TranslateDirective,
        FormsModule,
        TeamSubmissionSyncComponent,
        TextResultComponent,
        UnifiedFeedbackComponent,
        RatingComponent,
        ComplaintsStudentViewComponent,
        FaIconComponent,
        UpperCasePipe,
        ArtemisTranslatePipe,
        HtmlForMarkdownPipe,
    ],
})
export class TextEditorComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    private route = inject(ActivatedRoute);
    private textSubmissionService = inject(TextSubmissionService);
    private textService = inject(TextEditorService);
    private alertService = inject(AlertService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private stringCountService = inject(StringCountService);
    private accountService = inject(AccountService);
    private translateService = inject(TranslateService);

    readonly MAX_CHARACTER_COUNT = MAX_SUBMISSION_TEXT_LENGTH;
    protected readonly Result = Result;
    protected readonly hasExerciseDueDatePassed = hasExerciseDueDatePassed;
    protected readonly isAthenaAIResult = isAthenaAIResult;
    protected readonly buildFeedbackTextForReview = buildFeedbackTextForReview;

    participationId = input<number>();
    expandProblemStatement = input<boolean>(true);
    inputExercise = input<TextExercise>();
    inputSubmission = input<TextSubmission>();
    inputParticipation = input<StudentParticipation>();
    isExamSummary = input<boolean>(false);

    readonly textExercise = signal<TextExercise>(undefined!);
    readonly participation = signal<StudentParticipation>(undefined!);
    readonly result = signal<Result>(undefined!);
    readonly resultWithComplaint = signal<Result | undefined>(undefined);
    readonly submission = signal<TextSubmission>(undefined!);
    readonly course = signal<Course | undefined>(undefined);
    readonly isSaving = signal(false);
    private textEditorInput = new Subject<string>();
    textEditorInputObservable = this.textEditorInput.asObservable();
    private submissionChange = new Subject<TextSubmission>();
    submissionObservable = this.buildSubmissionObservable();
    // Is submitting always enabled?
    readonly isAllowedToSubmitAfterDueDate = signal<boolean>(false);
    // answer is the text that is stored in the user interface
    readonly answer = signal<string>('');
    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;
    readonly examMode = signal(false);
    readonly isGeneratingFeedback = signal(false);

    // indicates, that it is an exam exercise and the publishResults date is in the past
    isAfterPublishDate: boolean;
    readonly isOwnerOfParticipation = signal<boolean>(false);
    readonly isReadOnlyWithShowResult = signal(false);
    // Icon
    farListAlt = faListAlt;
    faChevronDown = faChevronDown;
    faCircleNotch = faCircleNotch;
    faEye = faEye;

    // used in the html template
    protected readonly onTextEditorTab = onTextEditorTab;

    participationUpdateListener: Subscription;
    readonly sortedHistoryResults = signal<Result[]>([]);
    hasAthenaResultForLatestSubmission = false;
    submissionId: number | undefined;
    resultId: number | undefined;

    ngOnInit() {
        if (this.inputValuesArePresent()) {
            this.setupComponentWithInputValues();
        } else {
            const participationId = this.participationId() !== undefined ? this.participationId() : Number(this.route.snapshot.paramMap.get('participationId'));
            this.submissionId = Number(this.route.snapshot.paramMap.get('submissionId')) || undefined;

            if (Number.isNaN(participationId)) {
                return this.alertService.error('artemisApp.textExercise.error');
            }

            // When participationId is provided as input (e.g. in exam summary), route params won't contain it,
            // so we fetch directly instead of relying on the route.params subscription to trigger the fetch.
            if (this.participationId() !== undefined) {
                this.textService.get(participationId!, this.resultId).subscribe({
                    next: (data: StudentParticipation) => {
                        this.updateParticipation(data, this.submissionId, this.resultId);
                        this.participationWebsocketService.addParticipation(this.participation(), this.textExercise());
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
                this.isReadOnlyWithShowResult.set(!!this.submissionId);
            } else {
                this.route.params?.subscribe((params) => {
                    const newSubmissionId = Number(this.route.snapshot.paramMap.get('submissionId')) || undefined;
                    const newResultId = Number(this.route.snapshot.paramMap.get('resultId')) || undefined;
                    const newParticipationId = Number(params['participationId']);
                    const participationChanged = !Number.isNaN(newParticipationId) && newParticipationId !== this.participation()?.id;
                    const submissionOrResultChanged = newSubmissionId !== this.submissionId || newResultId !== this.resultId;
                    this.submissionId = newSubmissionId;
                    this.resultId = newResultId;
                    this.isReadOnlyWithShowResult.set(!!newSubmissionId);
                    if (participationChanged || submissionOrResultChanged) {
                        const participationIdToFetch = !Number.isNaN(newParticipationId) ? newParticipationId : this.participation()?.id;
                        if (participationIdToFetch === undefined) {
                            return;
                        }
                        this.textService.get(participationIdToFetch, this.resultId).subscribe({
                            next: (data: StudentParticipation) => {
                                this.updateParticipation(data, this.submissionId, this.resultId);
                            },
                            error: (error: HttpErrorResponse) => onError(this.alertService, error),
                        });
                    } else {
                        this.updateParticipation(this.participation(), this.submissionId, this.resultId);
                    }
                });

                this.isReadOnlyWithShowResult.set(!!this.submissionId);
            }
        }
        this.participationUpdateListener?.unsubscribe();
        // Triggers on new result received
        this.participationUpdateListener = this.participationWebsocketService
            .subscribeForParticipationChanges()
            .pipe(skip(1))
            .subscribe((changedParticipation: StudentParticipation) => {
                // subscribeForParticipationChanges() is backed by a single app-wide BehaviorSubject, so every
                // text-editor instance receives every participation change (including the ones emitted by other
                // instances when they call addParticipation()). Without this guard, multiple editors rendered
                // together - e.g. several text exercises in the exam result summary - would all overwrite their
                // own exercise/submission state with whichever participation was added last, making every text
                // summary display the last exercise. Only react to changes for our own participation.
                if (changedParticipation?.id !== this.participation()?.id) {
                    return;
                }
                const results = changedParticipation.submissions?.flatMap((submission) => submission.results ?? []) || [];
                const oldResults = this.participation().submissions?.flatMap((submission) => submission.results ?? []) || [];
                const lastResult = results?.last();
                const isNewAthenaResult =
                    !!results &&
                    ((results?.length || 0) > (oldResults.length || 0) || lastResult?.completionDate === undefined) &&
                    lastResult?.assessmentType === AssessmentType.AUTOMATIC_ATHENA &&
                    lastResult?.successful !== undefined;
                if (isNewAthenaResult) {
                    this.isGeneratingFeedback.set(false);
                    if (lastResult?.successful === false) {
                        this.alertService.error('artemisApp.exercise.athenaFeedbackFailed');
                    } else {
                        this.alertService.success('artemisApp.exercise.athenaFeedbackSuccessful', { title: this.textExercise()?.title ?? '' });
                        this.hasAthenaResultForLatestSubmission = true;
                        if (this.isExamSummary() && this.participation()?.id !== undefined) {
                            this.textService.get(this.participation().id!, lastResult?.id).subscribe({
                                next: (data) => this.updateParticipation(data, this.submissionId, lastResult?.id),
                                error: (error: HttpErrorResponse) => onError(this.alertService, error),
                            });
                            return;
                        }
                    }
                }
                this.updateParticipation(changedParticipation, this.submissionId, this.resultId);
            });
    }

    private inputValuesArePresent(): boolean {
        return !!(this.inputExercise() || this.inputSubmission() || this.inputParticipation());
    }

    /**
     * Uses values directly passed to this component instead of subscribing to a participation to save resources
     *
     * <i>e.g. used within {@link ExamResultSummaryComponent} and the respective {@link ModelingExamSummaryComponent}
     * as directly after the exam no grading is present and only the student solution shall be displayed </i>
     * @private
     */
    private setupComponentWithInputValues() {
        if (this.inputExercise() !== undefined) {
            this.textExercise.set(this.inputExercise()!);
            this.examMode.set(!!this.textExercise().exerciseGroup);
        }
        if (this.inputSubmission() !== undefined) {
            this.submission.set(this.inputSubmission()!);
        }
        if (this.inputParticipation() !== undefined) {
            this.participation.set(this.inputParticipation()!);
        }

        if (this.submission()?.text) {
            this.answer.set(this.submission().text ?? '');
        }

        if (this.participation() && this.textExercise()) {
            this.participationWebsocketService.addParticipation(this.participation(), this.textExercise());
        }
    }

    /**
     * Updates the participation, the submission selected can be chosen through submissionId, default undefined means latest
     * @param participation The participation data
     * @param submissionId The id of the submission of choice. undefined value defaults to the latest submission
     * @param resultId The id of the specific result to display. undefined value defaults to the latest result
     */
    private updateParticipation(participation: StudentParticipation, submissionId: number | undefined = undefined, resultId: number | undefined = undefined) {
        if (participation) {
            this.participation.set(participation);
        } else {
            return;
        }
        this.textExercise.set(this.participation().exercise as TextExercise);
        this.examMode.set(!!this.textExercise().exerciseGroup);
        this.textExercise().studentParticipations = [this.participation()];
        this.checkIfSubmitAlwaysEnabled();
        this.isAfterAssessmentDueDate = !!this.textExercise().course && (!this.textExercise().assessmentDueDate || dayjs().isAfter(this.textExercise().assessmentDueDate));
        this.isAfterPublishDate = !!this.textExercise().exerciseGroup?.exam?.publishResultsDate && dayjs().isAfter(this.textExercise().exerciseGroup!.exam!.publishResultsDate);
        this.course.set(getCourseFromExercise(this.textExercise()));
        this.sortedHistoryResults.set(
            participation.submissions
                ?.flatMap((submission) => {
                    return (
                        submission.results?.map((result) => {
                            result.submission = submission;
                            return result;
                        }) ?? []
                    );
                })
                .sort((a, b) => (a.id ?? 0) - (b.id ?? 0)) || [],
        );

        if (this.participation().submissions?.length) {
            if (submissionId) {
                const foundSubmission = this.participation().submissions!.find((sub) => sub.id === submissionId)!;
                if (foundSubmission) {
                    this.submission.set(foundSubmission);
                } else {
                    this.submission.set(
                        this.participation()
                            .submissions!.sort((a, b) => (a.id ?? 0) - (b.id ?? 0))
                            .last() as TextSubmission,
                    );
                }
            } else {
                this.submission.set(
                    this.participation()
                        .submissions!.sort((a, b) => (a.id ?? 0) - (b.id ?? 0))
                        .last() as TextSubmission,
                );
            }

            setLatestSubmissionResult(this.submission(), getLatestSubmissionResult(this.submission()));

            // If resultId is provided, find the specific result; otherwise use latest
            if (resultId && this.submission()?.results) {
                const specificResult = this.submission().results?.find((result) => result.id === resultId);
                this.result.set(specificResult || this.submission().latestResult!);
            } else if (!this.submission()?.results) {
                this.result.set(this.sortedHistoryResults().last()!);
            } else {
                this.result.set(this.submission().latestResult!);
                this.hasAthenaResultForLatestSubmission = this.submission().latestResult!.assessmentType === AssessmentType.AUTOMATIC_ATHENA;
            }
            if (this.result() && !this.result().submission) {
                this.result().submission = this.submission();
            }

            // if one of the submissions results has a complaint, we get it
            this.resultWithComplaint.set(getFirstResultWithComplaint(this.submission()));

            if (this.submission()?.text) {
                this.answer.set(this.submission().text ?? '');
            } else {
                // handles the case when a submission is empty
                this.answer.set('');
            }
        }
        // check whether the student looks at the result
        this.isOwnerOfParticipation.set(this.accountService.isOwnerOfParticipation(this.participation()));
    }

    ngOnDestroy() {
        // Auto-save unsaved changes when navigating away from the component.
        // This ensures students don't lose their work if they accidentally navigate away
        // without explicitly saving their text submission.
        if (!this.canDeactivate() && this.textExercise().id) {
            let newSubmission = new TextSubmission();
            if (this.submission()) {
                newSubmission = this.submission();
            }
            newSubmission.text = this.answer();
            if (this.submission()?.id) {
                this.textSubmissionService.update(newSubmission, this.textExercise().id!).subscribe((response) => {
                    this.submission.set(response.body!);
                    setLatestSubmissionResult(this.submission(), getLatestSubmissionResult(this.submission()));
                    // Reconnect the submission to its participation so that the submission status
                    // is displayed correctly in the result component after auto-save.
                    if (this.submission().participation) {
                        this.submission().participation!.submissions = [this.submission()];
                        this.participationWebsocketService.addParticipation(this.submission().participation as StudentParticipation, this.textExercise());
                    }
                });
            }
        }

        this.participationUpdateListener?.unsubscribe();
        if (this.participation()) {
            this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(this.participation().id!, this.textExercise());
        }
    }

    private checkIfSubmitAlwaysEnabled() {
        const isInitializationAfterDueDate =
            this.textExercise().dueDate && this.participation().initializationDate && dayjs(this.participation().initializationDate).isAfter(this.textExercise().dueDate);
        this.isAllowedToSubmitAfterDueDate.set(!!isInitializationAfterDueDate && !this.participation().testRun && !dayjs().isAfter(this.participation().individualDueDate));
    }

    get isAutomaticResult(): boolean {
        return this.result()?.assessmentType === AssessmentType.AUTOMATIC_ATHENA;
    }
    /**
     * True, if the due date is after the current date, or there is no due date, or the participation is a practice run
     */
    get isActive(): boolean {
        return (
            !this.examMode() &&
            (!this.result() || this.isAutomaticResult) &&
            (!!this.participation()?.testRun || (this.textExercise() && (!this.textExercise().dueDate || !hasExerciseDueDatePassed(this.textExercise(), this.participation()))))
        );
    }

    /**
     * Check whether or not a result exists and if, returns the unreferenced feedback of it
     */
    get unreferencedFeedback(): Feedback[] | undefined {
        return this.result() ? getUnreferencedFeedback(this.result().feedbacks) : undefined;
    }

    get wordCount(): number {
        return this.stringCountService.countWords(this.answer());
    }

    get characterCount(): number {
        return this.stringCountService.countCharacters(this.answer());
    }

    canDeactivate(): boolean {
        if (!this.submission()) {
            return true;
        }
        return this.submission().text === this.answer();
    }

    /**
     * Displays the alert for confirming refreshing or closing the page if there are unsaved changes
     * NOTE: while the beforeunload event might be deprecated in the future, it is currently the only way to display a confirmation dialog when the user tries to leave the page
     * @param event the beforeunload event
     */
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: BeforeUnloadEvent) {
        if (!this.canDeactivate()) {
            event.preventDefault();
            return this.translateService.instant('pendingChanges');
        }
        return true;
    }

    submit() {
        if (this.isSaving()) {
            return;
        }

        if (!this.submission()) {
            return;
        }

        this.isSaving.set(true);
        this.submission.set(this.submissionForAnswer(this.answer()));
        const submissionToCreateOrUpdate = this.submission();
        // id undefined creates a new submission and setting results to undefined prevents foreign key constraints when deleting results from submission
        if (this.hasAthenaResultForLatestSubmission) {
            submissionToCreateOrUpdate.id = undefined;
            submissionToCreateOrUpdate.results = undefined;
        } else {
            setLatestSubmissionResult(submissionToCreateOrUpdate, getLatestSubmissionResult(this.submission()));
        }

        this.textSubmissionService.update(submissionToCreateOrUpdate, this.textExercise().id!).subscribe({
            next: (response) => {
                this.submission.set(response.body!);
                if (this.participation().team) {
                    // Make sure the team is not lost during update
                    const studentParticipation = this.submission().participation as StudentParticipation;
                    studentParticipation.team = this.participation().team;
                }
                setLatestSubmissionResult(this.submission(), getLatestSubmissionResult(this.submission()));
                this.submissionChange.next(this.submission());
                // reconnect so that the submission status is displayed correctly in the result.component
                this.submission().participation!.submissions = [this.submission()];
                this.participation.set(this.submission().participation as StudentParticipation);
                this.participation().exercise = this.textExercise();
                this.participationWebsocketService.addParticipation(this.participation(), this.textExercise());
                this.textExercise().studentParticipations = [this.participation()];
                this.result.set(getLatestSubmissionResult(this.submission())!);
                this.isSaving.set(false);
                if (!this.isAllowedToSubmitAfterDueDate()) {
                    this.alertService.success('entity.action.submitSuccessfulAlert');
                    this.hasAthenaResultForLatestSubmission = false;
                } else {
                    this.alertService.warning('entity.action.submitDueDateMissedAlert');
                }
            },
            error: (err: HttpErrorResponse) => {
                this.alertService.error(err.error.message);
                this.isSaving.set(false);
            },
        });
    }

    /**
     * Stream of submissions being emitted on:
     * 1. text editor input after a debounce time of 2 seconds
     * 2. manually triggered change on submission (e.g. when submit was clicked)
     */
    private buildSubmissionObservable() {
        const textEditorStream = this.textEditorInput
            .asObservable()
            .pipe(debounceTime(2000), distinctUntilChanged())
            .pipe(map((answer: string) => this.submissionForAnswer(answer)));
        const submissionChangeStream = this.submissionChange.asObservable();
        return merge(textEditorStream, submissionChangeStream);
    }

    private submissionForAnswer(answer: string): TextSubmission {
        return { ...this.submission(), text: answer, language: this.textService.predictLanguage(answer) };
    }

    onReceiveSubmissionFromTeam(submission: TextSubmission) {
        submission.participation!.exercise = this.textExercise();
        submission.participation!.submissions = [submission];
        // Keep the existing team on the participation
        const studentParticipation = submission.participation as StudentParticipation;
        studentParticipation.team = this.participation().team;
        this.updateParticipation(studentParticipation);
    }

    onTextEditorInput(event: Event) {
        this.textEditorInput.next((event.target as HTMLTextAreaElement).value);
    }
}
