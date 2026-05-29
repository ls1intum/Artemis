import { Component, OnInit, inject, input } from '@angular/core';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExternalSubmissionService } from 'app/exercise/external-submission/external-submission.service';
import { SCORE_PATTERN } from 'app/app.constants';
import { User } from 'app/account/user/user.model';
import { EventManager } from 'app/shared/service/event-manager.service';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { DynamicDialogRef } from 'primeng/dynamicdialog';

@Component({
    selector: 'jhi-external-submission-dialog',
    templateUrl: './external-submission-dialog.component.html',
    imports: [FormsModule, TranslateDirective, NgClass, FaIconComponent, HtmlForMarkdownPipe],
})
export class ExternalSubmissionDialogComponent implements OnInit {
    private externalSubmissionService = inject(ExternalSubmissionService);
    private readonly dialogRef = inject(DynamicDialogRef);
    private eventManager = inject(EventManager);

    readonly SCORE_PATTERN = SCORE_PATTERN;

    readonly exercise = input.required<Exercise>();

    student: User = new User();
    result: Result;
    feedbacks: Feedback[] = [];
    isSaving = false;
    userId: number;
    isAssessor: boolean;
    complaint: Complaint;

    // Icons
    faSave = faSave;
    faBan = faBan;

    /**
     * Initialize Component by calling a helper that generates an initial manual result.
     */
    ngOnInit() {
        this.initializeForResultCreation();
    }

    /**
     * Initialize result with initial manual result.
     */
    initializeForResultCreation() {
        this.result = this.externalSubmissionService.generateInitialManualResult();
    }

    /**
     * Close modal window.
     */
    clear() {
        this.dialogRef.close('cancel');
    }

    /**
     * Add manual feedbacks to the result and create external submission.
     */
    save() {
        this.result.feedbacks = this.feedbacks;
        this.isSaving = true;
        for (let i = 0; i < this.result.feedbacks.length; i++) {
            this.result.feedbacks[i].type = FeedbackType.MANUAL;
        }
        this.subscribeToSaveResponse(this.externalSubmissionService.create(this.exercise(), this.student, this.result));
    }

    /**
     * If http request is successful, pass it to onSaveSuccess, otherwise call onSaveError.
     * @param { Observable<HttpResponse<Result>> } result - Observable of Http request
     */
    private subscribeToSaveResponse(result: Observable<HttpResponse<Result>>) {
        result.subscribe({
            next: (res) => this.onSaveSuccess(res),
            error: () => this.onSaveError(),
        });
    }

    /**
     * Close modal window, indicate saving is done and broadcast that manual result is added.
     * @param { HttpResponse<Result> } result - Result of successful http request
     */
    onSaveSuccess(result: HttpResponse<Result>) {
        this.dialogRef.close(result.body);
        this.isSaving = false;
        this.eventManager.broadcast({ name: 'resultListModification', content: 'Added a manual result' });
    }

    /**
     * Indicate that saving didn't work by setting isSaving to false.
     */
    onSaveError() {
        this.isSaving = false;
    }

    /**
     * Add new feedback to feedbacks
     */
    pushFeedback() {
        this.feedbacks.push(new Feedback());
    }

    /**
     * Remove last added feedback if there is one.
     */
    popFeedback() {
        if (this.feedbacks.length > 0) {
            this.feedbacks.pop();
        }
    }
}
