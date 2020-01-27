import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { TextExercise, TextExerciseService } from 'app/entities/text-exercise';
import { Result, ResultService } from 'app/entities/result';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { TextEditorService } from 'app/text-editor/text-editor.service';
import * as moment from 'moment';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { Feedback } from 'app/entities/feedback';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ComponentCanDeactivate } from 'app/shared';
import { Observable } from 'rxjs/Observable';
import { ButtonType } from 'app/shared/components';
import { participationStatus } from 'app/entities/exercise';

@Component({
    templateUrl: './text-editor.component.html',
    providers: [ParticipationService],
})
export class TextEditorComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    readonly ButtonType = ButtonType;
    textExercise: TextExercise;
    participation: StudentParticipation;
    result: Result;
    submission: TextSubmission;
    isSaving: boolean;
    // Is submitting always enabled?
    isAlwaysActive: boolean;
    isAllowedToSubmitAfterDeadline: boolean;
    answer: string;
    // indicates if the assessment due date is in the past. the assessment will not be loaded and displayed to the student if it is not.
    isAfterAssessmentDueDate: boolean;

    constructor(
        private route: ActivatedRoute,
        private textExerciseService: TextExerciseService,
        private participationService: ParticipationService,
        private textSubmissionService: TextSubmissionService,
        private textService: TextEditorService,
        private resultService: ResultService,
        private jhiAlertService: JhiAlertService,
        private artemisMarkdown: ArtemisMarkdown,
        private location: Location,
        private translateService: TranslateService,
        private participationWebsocketService: ParticipationWebsocketService,
    ) {
        this.isSaving = false;
    }

    ngOnInit() {
        const participationId = Number(this.route.snapshot.paramMap.get('participationId'));
        if (Number.isNaN(participationId)) {
            return this.jhiAlertService.error('artemisApp.textExercise.error', null, undefined);
        }

        this.textService.get(participationId).subscribe(
            (data: StudentParticipation) => {
                this.participation = data;
                this.textExercise = this.participation.exercise as TextExercise;
                this.textExercise.studentParticipations = [this.participation];
                this.textExercise.participationStatus = participationStatus(this.textExercise);
                this.checkIfSubmitAlwaysEnabled();
                this.isAfterAssessmentDueDate = !this.textExercise.assessmentDueDate || moment().isAfter(this.textExercise.assessmentDueDate);

                if (data.submissions && data.submissions.length > 0) {
                    this.submission = data.submissions[0] as TextSubmission;
                    if (this.submission && data.results && this.isAfterAssessmentDueDate) {
                        this.result = data.results.find(r => r.submission!.id === this.submission.id)!;
                    }

                    if (this.submission && this.submission.text) {
                        this.answer = this.submission.text;
                    }
                }
            },
            (error: HttpErrorResponse) => this.onError(error),
        );
    }

    ngOnDestroy() {
        if (this.canDeactivate() && this.textExercise.id) {
            let newSubmission = new TextSubmission();
            if (this.submission) {
                newSubmission = this.submission;
            }
            newSubmission.submitted = false;
            newSubmission.text = this.answer;
            if (this.submission.id) {
                this.textSubmissionService.update(newSubmission, this.textExercise.id).subscribe(response => {
                    this.submission = response.body!;
                    // reconnect so that the submission status is displayed correctly in the result.component
                    this.submission.participation.submissions = [this.submission];
                    this.participationWebsocketService.addParticipation(this.submission.participation as StudentParticipation, this.textExercise);
                });
            }
        }
    }

    private checkIfSubmitAlwaysEnabled() {
        const isInitializationAfterDueDate =
            this.textExercise.dueDate && this.participation.initializationDate && moment(this.participation.initializationDate).isAfter(this.textExercise.dueDate);
        const isAlwaysActive = !this.result && (!this.textExercise.dueDate || isInitializationAfterDueDate);

        this.isAllowedToSubmitAfterDeadline = !!isInitializationAfterDueDate;
        this.isAlwaysActive = !!isAlwaysActive;
    }

    /**
     * True, if the deadline is after the current date, or there is no deadline, or the exercise is always active
     */
    get isActive(): boolean {
        const isActive = !this.result && (this.isAlwaysActive || (this.textExercise && this.textExercise.dueDate && moment(this.textExercise.dueDate).isSameOrAfter(moment())));
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
     * Find "General Feedback" item for Result, if it exists.
     * General Feedback is stored in the same Array as  the other Feedback, but does not have a reference.
     * @return General Feedback item, if it exists and if it has a Feedback Text.
     */
    get generalFeedback(): Feedback | null {
        if (this.result && this.result.feedbacks && Array.isArray(this.result.feedbacks)) {
            const feedbackWithoutReference = this.result.feedbacks.find(f => f.reference == null) || null;
            if (feedbackWithoutReference != null && feedbackWithoutReference.detailText != null && feedbackWithoutReference.detailText.length > 0) {
                return feedbackWithoutReference;
            }
        }

        return null;
    }

    // Displays the alert for confirming refreshing or closing the page if there are unsaved changes
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification($event: any) {
        if (this.canDeactivate()) {
            $event.returnValue = this.translateService.instant('pendingChanges');
        }
    }

    canDeactivate(): Observable<boolean> | boolean {
        return this.submission.text !== this.answer;
    }

    submit() {
        if (this.isSaving) {
            return;
        }

        if (!this.submission) {
            return;
        }

        this.isSaving = true;
        this.submission.text = this.answer;
        this.submission.language = this.textService.predictLanguage(this.submission.text);

        this.submission.submitted = true;
        this.textSubmissionService.update(this.submission, this.textExercise.id).subscribe(
            response => {
                this.submission = response.body!;
                // reconnect so that the submission status is displayed correctly in the result.component
                this.submission.participation.submissions = [this.submission];
                this.participationWebsocketService.addParticipation(this.submission.participation as StudentParticipation, this.textExercise);
                this.result = this.submission.result;
                this.isSaving = false;

                if (!this.isAllowedToSubmitAfterDeadline) {
                    this.jhiAlertService.success('entity.action.submitSuccessfulAlert');
                } else {
                    this.jhiAlertService.warning('entity.action.submitDeadlineMissedAlert');
                }
            },
            err => {
                this.jhiAlertService.error('artemisApp.modelingEditor.error');
                this.submission.submitted = false;
                this.isSaving = false;
            },
        );
    }

    onTextEditorTab(editor: HTMLTextAreaElement, event: KeyboardEvent) {
        event.preventDefault();
        const value = editor.value;
        const start = editor.selectionStart;
        const end = editor.selectionEnd;

        editor.value = value.substring(0, start) + '\t' + value.substring(end);
        editor.selectionStart = editor.selectionEnd = start + 1;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message, null, undefined);
    }

    previous() {
        this.location.back();
    }
}
