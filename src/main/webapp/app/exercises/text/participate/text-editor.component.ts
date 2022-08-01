import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import dayjs from 'dayjs/esm';
import { merge, Subject } from 'rxjs';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { Feedback } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { hasExerciseDueDatePassed, participationStatus } from 'app/exercises/shared/exercise/exercise.utils';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ButtonType } from 'app/shared/components/button.component';
import { Result } from 'app/entities/result.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';
import { AccountService } from 'app/core/auth/account.service';
import { getFirstResultWithComplaint, getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';
import { getUnreferencedFeedback } from 'app/exercises/shared/result/result.utils';
import { onError } from 'app/shared/util/global.utils';
import { Course } from 'app/entities/course.model';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { MAX_SUBMISSION_TEXT_LENGTH } from 'app/shared/constants/input.constants';

@Component({
    templateUrl: './text-editor.component.html',
    providers: [ParticipationService],
    styleUrls: ['./text-editor.component.scss'],
})
export class TextEditorComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    readonly ButtonType = ButtonType;
    readonly maxCharacterCount = MAX_SUBMISSION_TEXT_LENGTH;
    textExercise: TextExercise;
    participation: StudentParticipation;
    result: Result;
    resultWithComplaint?: Result;
    submission: TextSubmission;
    course?: Course;
    isSaving: boolean;
    private textEditorInput = new Subject<string>();
    textEditorInputObservable = this.textEditorInput.asObservable();
    private submissionChange = new Subject<TextSubmission>();
    submissionObservable = this.buildSubmissionObservable();
    // Is submitting always enabled?
    isAlwaysActive: boolean;
    isAllowedToSubmitAfterDeadline: boolean;
    // answer is the text that is stored in the user interface
    answer: string;
    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;
    examMode = false;

    // indicates, that it is an exam exercise and the publishResults date is in the past
    isAfterPublishDate: boolean;
    isOwnerOfParticipation: boolean;

    // Icon
    farListAlt = faListAlt;

    constructor(
        private route: ActivatedRoute,
        private textExerciseService: TextExerciseService,
        private participationService: ParticipationService,
        private textSubmissionService: TextSubmissionService,
        private textService: TextEditorService,
        private resultService: ResultService,
        private alertService: AlertService,
        private artemisMarkdown: ArtemisMarkdownService,
        private location: Location,
        private translateService: TranslateService,
        private participationWebsocketService: ParticipationWebsocketService,
        private stringCountService: StringCountService,
        private accountService: AccountService,
    ) {
        this.isSaving = false;
    }

    ngOnInit() {
        const participationId = Number(this.route.snapshot.paramMap.get('participationId'));
        if (Number.isNaN(participationId)) {
            return this.alertService.error('artemisApp.textExercise.error');
        }

        this.textService.get(participationId).subscribe({
            next: (data: StudentParticipation) => this.updateParticipation(data),
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    private updateParticipation(participation: StudentParticipation) {
        this.participation = participation;
        this.textExercise = this.participation.exercise as TextExercise;
        this.examMode = !!this.textExercise.exerciseGroup;
        this.textExercise.studentParticipations = [this.participation];
        this.textExercise.participationStatus = participationStatus(this.textExercise);
        this.checkIfSubmitAlwaysEnabled();
        this.isAfterAssessmentDueDate = !!this.textExercise.course && (!this.textExercise.assessmentDueDate || dayjs().isAfter(this.textExercise.assessmentDueDate));
        this.isAfterPublishDate =
            !!this.textExercise.exerciseGroup &&
            !!this.textExercise.exerciseGroup.exam &&
            !!this.textExercise.exerciseGroup.exam.publishResultsDate &&
            dayjs().isAfter(this.textExercise.exerciseGroup.exam.publishResultsDate);
        this.course = getCourseFromExercise(this.textExercise);

        if (participation.submissions && participation.submissions.length > 0) {
            this.submission = participation.submissions[0] as TextSubmission;
            setLatestSubmissionResult(this.submission, getLatestSubmissionResult(this.submission));
            if (this.submission && this.submission.results && participation.results && (this.isAfterAssessmentDueDate || this.isAfterPublishDate)) {
                this.result = this.submission.latestResult!;
                this.result.participation = participation;
            }
            // if one of the submissions results has a complaint, we get it
            this.resultWithComplaint = getFirstResultWithComplaint(this.submission);

            if (this.submission && this.submission.text) {
                this.answer = this.submission.text;
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
    }

    private checkIfSubmitAlwaysEnabled() {
        const isInitializationAfterDueDate =
            this.textExercise.dueDate && this.participation.initializationDate && dayjs(this.participation.initializationDate).isAfter(this.textExercise.dueDate);
        const isAlwaysActive = !this.result && (!this.textExercise.dueDate || isInitializationAfterDueDate);

        this.isAllowedToSubmitAfterDeadline = !!isInitializationAfterDueDate;
        this.isAlwaysActive = !!isAlwaysActive;
    }

    /**
     * True, if the deadline is after the current date, or there is no deadline, or the exercise is always active
     */
    get isActive(): boolean {
        const isActive =
            !this.examMode &&
            !this.result &&
            (this.isAlwaysActive || (this.textExercise && this.textExercise.dueDate && !hasExerciseDueDatePassed(this.textExercise, this.participation)));
        return !!isActive;
    }

    get submitButtonTooltip(): string {
        if (this.isAllowedToSubmitAfterDeadline) {
            return 'entity.action.submitDeadlineMissedTooltip';
        }
        if (this.isActive && !this.textExercise.dueDate) {
            return 'entity.action.submitNoDeadlineTooltip';
        } else if (this.isActive) {
            return 'entity.action.submitTooltip';
        }

        return 'entity.action.deadlineMissedTooltip';
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

    // Displays the alert for confirming refreshing or closing the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: any) {
        if (!this.canDeactivate()) {
            event.returnValue = this.translateService.instant('pendingChanges');
        }
    }

    canDeactivate(): boolean {
        if (!this.submission) {
            return true;
        }
        return this.submission.text === this.answer;
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
        setLatestSubmissionResult(this.submission, getLatestSubmissionResult(this.submission));

        this.textSubmissionService.update(this.submission, this.textExercise.id!).subscribe({
            next: (response) => {
                this.submission = response.body!;
                setLatestSubmissionResult(this.submission, getLatestSubmissionResult(this.submission));
                this.submissionChange.next(this.submission);
                // reconnect so that the submission status is displayed correctly in the result.component
                this.submission.participation!.submissions = [this.submission];
                this.participation = this.submission.participation as StudentParticipation;
                this.participation.exercise = this.textExercise;
                this.participationWebsocketService.addParticipation(this.participation, this.textExercise);
                this.textExercise.studentParticipations = [this.participation];
                this.textExercise.participationStatus = participationStatus(this.textExercise);
                this.result = getLatestSubmissionResult(this.submission)!;
                if (this.result) {
                    this.result.participation = this.submission.participation;
                }
                this.isSaving = false;

                if (!this.isAllowedToSubmitAfterDeadline) {
                    this.alertService.success('entity.action.submitSuccessfulAlert');
                } else {
                    this.alertService.warning('entity.action.submitDeadlineMissedAlert');
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
        this.updateParticipation(submission.participation as StudentParticipation);
    }

    onTextEditorTab(editor: HTMLTextAreaElement, event: Event) {
        event.preventDefault();
        const value = editor.value;
        const start = editor.selectionStart;
        const end = editor.selectionEnd;

        editor.value = value.substring(0, start) + '\t' + value.substring(end);
        editor.selectionStart = editor.selectionEnd = start + 1;
    }

    onTextEditorInput(event: Event) {
        this.textEditorInput.next((<HTMLTextAreaElement>event.target).value);
    }
}
