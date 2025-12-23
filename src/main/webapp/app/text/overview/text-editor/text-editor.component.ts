import { Component, HostListener, OnDestroy, OnInit, inject, input } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { HeaderParticipationPageComponent } from 'app/exercise/exercise-headers/participation-page/header-participation-page.component';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { TeamSubmissionSyncComponent } from 'app/exercise/team-submission-sync/team-submission-sync.component';
import { TeamParticipateInfoBoxComponent } from 'app/exercise/team/team-participate/team-participate-info-box.component';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { TextEditorService } from 'app/text/overview/service/text-editor.service';
import dayjs from 'dayjs/esm';
import { Subject, Subscription, merge } from 'rxjs';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { debounceTime, distinctUntilChanged, map, skip } from 'rxjs/operators';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { StringCountService } from 'app/text/overview/service/string-count.service';
import { AccountService } from 'app/core/auth/account.service';
import { getFirstResultWithComplaint, getLatestSubmissionResult, setLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { getUnreferencedFeedback, isAthenaAIResult } from 'app/exercise/result/result.utils';
import { onError } from 'app/shared/util/global.utils';
import { Course } from 'app/core/course/shared/entities/course.model';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronDown, faCircleNotch, faEye, faTimeline } from '@fortawesome/free-solid-svg-icons';
import { MAX_SUBMISSION_TEXT_LENGTH } from 'app/shared/constants/input.constants';
import { ChatServiceMode } from 'app/iris/overview/services/iris-chat.service';
import { IrisCourseSettingsWithRateLimitDTO } from 'app/iris/shared/entities/settings/iris-course-settings.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_IRIS } from 'app/app.constants';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { RequestFeedbackButtonComponent } from 'app/core/course/overview/exercise-details/request-feedback-button/request-feedback-button.component';
import { ResultHistoryComponent } from 'app/exercise/result-history/result-history.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { TextResultComponent } from '../text-result/text-result.component';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisExerciseChatbotButtonComponent } from 'app/iris/overview/exercise-chatbot/exercise-chatbot-button.component';
import { UpperCasePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { onTextEditorTab } from 'app/shared/util/text.utils';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-text-editor',
    templateUrl: './text-editor.component.html',
    providers: [ParticipationService],
    styleUrls: ['./text-editor.component.scss'],
    imports: [
        HeaderParticipationPageComponent,
        ButtonComponent,
        RouterLink,
        RequestFeedbackButtonComponent,
        ResultHistoryComponent,
        ResizeableContainerComponent,
        TeamParticipateInfoBoxComponent,
        TranslateDirective,
        FormsModule,
        TeamSubmissionSyncComponent,
        TextResultComponent,
        AdditionalFeedbackComponent,
        RatingComponent,
        ComplaintsStudentViewComponent,
        FaIconComponent,
        IrisExerciseChatbotButtonComponent,
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
    private profileService = inject(ProfileService);
    private irisSettingsService = inject(IrisSettingsService);
    private translateService = inject(TranslateService);

    readonly ButtonType = ButtonType;
    readonly MAX_CHARACTER_COUNT = MAX_SUBMISSION_TEXT_LENGTH;
    protected readonly Result = Result;
    protected readonly hasExerciseDueDatePassed = hasExerciseDueDatePassed;
    readonly ChatServiceMode = ChatServiceMode;
    protected readonly isAthenaAIResult = isAthenaAIResult;

    participationId = input<number>();
    displayHeader = input<boolean>(true);
    expandProblemStatement = input<boolean>(true);
    inputExercise = input<TextExercise>();
    inputSubmission = input<TextSubmission>();
    inputParticipation = input<StudentParticipation>();
    isExamSummary = input<boolean>(false);

    textExercise: TextExercise;
    participation: StudentParticipation;
    result: Result;
    resultWithComplaint?: Result;
    submission: TextSubmission;
    course?: Course;
    isSaving = false;
    private textEditorInput = new Subject<string>();
    textEditorInputObservable = this.textEditorInput.asObservable();
    private submissionChange = new Subject<TextSubmission>();
    submissionObservable = this.buildSubmissionObservable();
    // Is submitting always enabled?
    isAlwaysActive: boolean;
    isAllowedToSubmitAfterDueDate: boolean;
    // answer is the text that is stored in the user interface
    answer: string;
    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;
    examMode = false;
    isGeneratingFeedback = false;
    irisSettings?: IrisCourseSettingsWithRateLimitDTO;

    // indicates, that it is an exam exercise and the publishResults date is in the past
    isAfterPublishDate: boolean;
    isOwnerOfParticipation: boolean;
    isReadOnlyWithShowResult = false;
    // Icon
    farListAlt = faListAlt;
    faChevronDown = faChevronDown;
    faCircleNotch = faCircleNotch;
    faTimeline = faTimeline;
    faEye = faEye;

    // used in the html template
    protected readonly onTextEditorTab = onTextEditorTab;

    participationUpdateListener: Subscription;
    sortedHistoryResults: Result[];
    hasAthenaResultForLatestSubmission = false;
    showHistory = false;
    submissionId: number | undefined;

    ngOnInit() {
        if (this.inputValuesArePresent()) {
            this.setupComponentWithInputValues();
        } else {
            const participationId = this.participationId() !== undefined ? this.participationId() : Number(this.route.snapshot.paramMap.get('participationId'));
            this.submissionId = Number(this.route.snapshot.paramMap.get('submissionId')) || undefined;

            if (Number.isNaN(participationId)) {
                return this.alertService.error('artemisApp.textExercise.error');
            }

            this.route.params?.subscribe(() => {
                this.submissionId = Number(this.route.snapshot.paramMap.get('submissionId')) || undefined;
                this.updateParticipation(this.participation, this.submissionId);
            });

            this.textService.get(participationId!).subscribe({
                next: (data: StudentParticipation) => this.updateParticipation(data, this.submissionId),
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });

            this.isReadOnlyWithShowResult = !!this.submissionId;
        }
        this.participationUpdateListener?.unsubscribe();
        // Triggers on new result received
        this.participationUpdateListener = this.participationWebsocketService
            .subscribeForParticipationChanges()
            .pipe(skip(1))
            .subscribe((changedParticipation: StudentParticipation) => {
                const results = changedParticipation.submissions?.flatMap((submission) => submission.results ?? []) || [];
                const oldResults = this.participation.submissions?.flatMap((submission) => submission.results ?? []) || [];
                if (
                    results &&
                    ((results?.length || 0) > (oldResults.length || 0) || results?.last()?.completionDate === undefined) &&
                    results?.last()?.assessmentType === AssessmentType.AUTOMATIC_ATHENA &&
                    results.last()?.successful !== undefined
                ) {
                    this.isGeneratingFeedback = false;
                    if (results.last()?.successful === false) {
                        this.alertService.error('artemisApp.exercise.athenaFeedbackFailed');
                    } else {
                        this.alertService.success('artemisApp.exercise.athenaFeedbackSuccessful');
                        this.hasAthenaResultForLatestSubmission = true;
                    }
                }
                this.updateParticipation(this.participation);
                this.loadIrisSettings();
            });
    }

    /**
     * Loads Iris settings for the current exercise if Iris is available and the exercise is not in exam mode.
     *
     * This method retrieves the application profile settings and checks if `PROFILE_IRIS` is active.
     * If active and the exercise is not in exam mode, it fetches the Iris settings for the given exercise ID.
     */
    private loadIrisSettings(): void {
        // only load the settings if Iris is available and this is not an exam exercise
        if (this.profileService.isProfileActive(PROFILE_IRIS) && !this.examMode && this.course?.id) {
            this.irisSettingsService.getCourseSettingsWithRateLimit(this.course.id).subscribe((response) => {
                this.irisSettings = response;
            });
        }
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
            this.textExercise = this.inputExercise()!;
        }
        if (this.inputSubmission() !== undefined) {
            this.submission = this.inputSubmission()!;
        }
        if (this.inputParticipation() !== undefined) {
            this.participation = this.inputParticipation()!;
        }

        if (this.submission?.text) {
            this.answer = this.submission.text;
        }
    }

    /**
     * Updates the participation, the submission selected can be chosen through submissionId, default undefined means latest
     * @param participation The participation data
     * @param submissionId The id of the submission of choice. undefined value defaults to the latest submission
     */
    private updateParticipation(participation: StudentParticipation, submissionId: number | undefined = undefined) {
        if (participation) {
            this.participation = participation;
        } else {
            return;
        }
        this.textExercise = this.participation.exercise as TextExercise;
        this.examMode = !!this.textExercise.exerciseGroup;
        this.textExercise.studentParticipations = [this.participation];
        this.checkIfSubmitAlwaysEnabled();
        this.isAfterAssessmentDueDate = !!this.textExercise.course && (!this.textExercise.assessmentDueDate || dayjs().isAfter(this.textExercise.assessmentDueDate));
        this.isAfterPublishDate = !!this.textExercise.exerciseGroup?.exam?.publishResultsDate && dayjs().isAfter(this.textExercise.exerciseGroup.exam.publishResultsDate);
        this.course = getCourseFromExercise(this.textExercise);
        this.sortedHistoryResults =
            participation.submissions
                ?.flatMap((submission) => {
                    return (
                        submission.results?.map((result) => {
                            result.submission = submission;
                            return result;
                        }) ?? []
                    );
                })
                .sort((a, b) => (a.id ?? 0) - (b.id ?? 0)) || [];

        if (this.participation.submissions?.length) {
            if (submissionId) {
                const foundSubmission = this.participation.submissions.find((sub) => sub.id === submissionId)!;
                if (foundSubmission) {
                    this.submission = foundSubmission;
                } else {
                    this.submission = this.participation.submissions.sort((a, b) => (a.id ?? 0) - (b.id ?? 0)).last() as TextSubmission;
                }
            } else {
                this.submission = this.participation.submissions.sort((a, b) => (a.id ?? 0) - (b.id ?? 0)).last() as TextSubmission;
            }

            setLatestSubmissionResult(this.submission, getLatestSubmissionResult(this.submission));
            if (!this.submission?.results) {
                this.result = this.sortedHistoryResults.last()!;
            } else {
                this.result = this.submission.latestResult!;
                this.hasAthenaResultForLatestSubmission = this.submission.latestResult!.assessmentType === AssessmentType.AUTOMATIC_ATHENA;
            }
            // if one of the submissions results has a complaint, we get it
            this.resultWithComplaint = getFirstResultWithComplaint(this.submission);

            if (this.submission?.text) {
                this.answer = this.submission.text;
            } else {
                // handles the case when a submission is empty
                this.answer = '';
            }
        }
        // check whether the student looks at the result
        this.isOwnerOfParticipation = this.accountService.isOwnerOfParticipation(this.participation);
    }

    ngOnDestroy() {
        if (!this.canDeactivate() && this.textExercise.id) {
            let newSubmission = new TextSubmission();
            if (this.submission) {
                newSubmission = this.submission;
            }
            newSubmission.text = this.answer;
            if (this.submission.id) {
                this.textSubmissionService.update(newSubmission, this.textExercise.id).subscribe((response) => {
                    this.submission = response.body!;
                    setLatestSubmissionResult(this.submission, getLatestSubmissionResult(this.submission));
                    // reconnect so that the submission status is displayed correctly in the result.component
                    this.submission.participation!.submissions = [this.submission];
                    this.participationWebsocketService.addParticipation(this.submission.participation as StudentParticipation, this.textExercise);
                });
            }
        }

        this.participationUpdateListener?.unsubscribe();
        if (this.participation) {
            this.participationWebsocketService.unsubscribeForLatestResultOfParticipation(this.participation.id!, this.textExercise);
        }
    }

    private checkIfSubmitAlwaysEnabled() {
        const isInitializationAfterDueDate =
            this.textExercise.dueDate && this.participation.initializationDate && dayjs(this.participation.initializationDate).isAfter(this.textExercise.dueDate);
        const isAlwaysActive = !this.result && (!this.textExercise.dueDate || isInitializationAfterDueDate);

        this.isAllowedToSubmitAfterDueDate = !!isInitializationAfterDueDate && !dayjs().isAfter(this.participation.individualDueDate);
        this.isAlwaysActive = !!isAlwaysActive;
    }

    get isAutomaticResult(): boolean {
        return this.result?.assessmentType === AssessmentType.AUTOMATIC_ATHENA;
    }
    /**
     * True, if the due date is after the current date, or there is no due date, or the exercise is always active
     */
    get isActive(): boolean {
        const isActive =
            !this.examMode &&
            (!this.result || this.isAutomaticResult) &&
            (this.isAlwaysActive || (this.textExercise && this.textExercise.dueDate && !hasExerciseDueDatePassed(this.textExercise, this.participation)));
        return !!isActive;
    }

    get submitButtonTooltip(): string {
        if (this.isAllowedToSubmitAfterDueDate) {
            return 'entity.action.submitDueDateMissedTooltip';
        }
        if (this.isActive && !this.textExercise.dueDate) {
            return 'entity.action.submitNoDueDateTooltip';
        } else if (this.isActive) {
            return 'entity.action.submitTooltip';
        }

        return 'entity.action.dueDateMissedTooltip';
    }

    /**
     * Check whether or not a result exists and if, returns the unreferenced feedback of it
     */
    get unreferencedFeedback(): Feedback[] | undefined {
        return this.result ? getUnreferencedFeedback(this.result.feedbacks) : undefined;
    }

    get wordCount(): number {
        return this.stringCountService.countWords(this.answer);
    }

    get characterCount(): number {
        return this.stringCountService.countCharacters(this.answer);
    }

    canDeactivate(): boolean {
        if (!this.submission) {
            return true;
        }
        return this.submission.text === this.answer;
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
        if (this.isSaving) {
            return;
        }

        if (!this.submission) {
            return;
        }

        this.isSaving = true;
        this.submission = this.submissionForAnswer(this.answer);
        const submissionToCreateOrUpdate = this.submission;
        // id undefined creates a new submission and setting results to undefined prevents foreign key constraints when deleting results from submission
        if (this.hasAthenaResultForLatestSubmission) {
            submissionToCreateOrUpdate.id = undefined;
            submissionToCreateOrUpdate.results = undefined;
        } else {
            setLatestSubmissionResult(submissionToCreateOrUpdate, getLatestSubmissionResult(this.submission));
        }

        this.textSubmissionService.update(submissionToCreateOrUpdate, this.textExercise.id!).subscribe({
            next: (response) => {
                this.submission = response.body!;
                if (this.participation.team) {
                    // Make sure the team is not lost during update
                    const studentParticipation = this.submission.participation as StudentParticipation;
                    studentParticipation.team = this.participation.team;
                }
                setLatestSubmissionResult(this.submission, getLatestSubmissionResult(this.submission));
                this.submissionChange.next(this.submission);
                // reconnect so that the submission status is displayed correctly in the result.component
                this.submission.participation!.submissions = [this.submission];
                this.participation = this.submission.participation as StudentParticipation;
                this.participation.exercise = this.textExercise;
                this.participationWebsocketService.addParticipation(this.participation, this.textExercise);
                this.textExercise.studentParticipations = [this.participation];
                this.result = getLatestSubmissionResult(this.submission)!;
                this.isSaving = false;
                if (!this.isAllowedToSubmitAfterDueDate) {
                    this.alertService.success('entity.action.submitSuccessfulAlert');
                    this.hasAthenaResultForLatestSubmission = false;
                } else {
                    this.alertService.warning('entity.action.submitDueDateMissedAlert');
                }
            },
            error: (err: HttpErrorResponse) => {
                this.alertService.error(err.error.message);
                this.isSaving = false;
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
        return { ...this.submission, text: answer, language: this.textService.predictLanguage(answer) };
    }

    onReceiveSubmissionFromTeam(submission: TextSubmission) {
        submission.participation!.exercise = this.textExercise;
        submission.participation!.submissions = [submission];
        // Keep the existing team on the participation
        const studentParticipation = submission.participation as StudentParticipation;
        studentParticipation.team = this.participation.team;
        this.updateParticipation(studentParticipation);
    }

    onTextEditorInput(event: Event) {
        this.textEditorInput.next((<HTMLTextAreaElement>event.target).value);
    }
}
