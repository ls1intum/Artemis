import { TumAetUiCheckboxComponent } from '@tumaet/ui-angular/checkbox';
import { TumAetUiInputDirective } from '@tumaet/ui-angular/input';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import type { Result as ResultResponse } from 'app/openapi/model/result';
import { ResultApi } from 'app/openapi/api/result-api';
import { toFeedbackRequest } from 'app/assessment/shared/util/example-submission-request.util';
import { Observable } from 'rxjs';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExternalSubmissionService } from 'app/exercise/external-submission/external-submission.service';
import { SCORE_PATTERN } from 'app/app.constants';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { faBan } from '@fortawesome/free-solid-svg-icons/faBan';
import { faSave } from '@fortawesome/free-solid-svg-icons/faSave';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { TumAetUiDialogRef } from '@tumaet/ui-angular/dialog';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExternalSubmissionStudentSearchComponent } from 'app/exercise/external-submission/external-submission-student-search.component';
import { ExternalSubmissionCandidate } from 'app/exercise/external-submission/external-submission-candidate.model';

@Component({
    selector: 'jhi-external-submission-dialog',
    templateUrl: './external-submission-dialog.component.html',
    styleUrl: './external-submission-dialog.component.css',
    imports: [
        TumAetUiCheckboxComponent,
        TumAetUiInputDirective,
        TumAetUiButtonDirective,
        FormsModule,
        TranslateDirective,
        NgClass,
        FaIconComponent,
        MarkdownDirective,
        ArtemisTranslatePipe,
        ExternalSubmissionStudentSearchComponent,
    ],
})
export class ExternalSubmissionDialogComponent implements OnInit {
    private externalSubmissionService = inject(ExternalSubmissionService);
    private resultApi = inject(ResultApi);
    private readonly dialogRef = inject(TumAetUiDialogRef);
    private eventManager = inject(EventManager);

    readonly SCORE_PATTERN = SCORE_PATTERN;

    readonly exercise = input.required<Exercise>();

    /** The student picked in the search; Save stays disabled until there is one. */
    readonly selectedStudent = signal<ExternalSubmissionCandidate | undefined>(undefined);
    // result is a deep two-way [(ngModel)] target (result.score/successful/rated); use a getter/setter facade over a signal.
    private readonly _result = signal<Result>(undefined!);
    get result(): Result {
        return this._result();
    }
    set result(value: Result) {
        this._result.set(value);
    }
    feedbacks: Feedback[] = [];
    readonly isSaving = signal(false);
    userId?: number;
    isAssessor?: boolean;
    complaint?: Complaint;

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
        this.dialogRef.dismiss('cancel');
    }

    /**
     * Add manual feedbacks to the result and create external submission.
     */
    save() {
        const student = this.selectedStudent();
        if (!student) {
            return;
        }
        this.result.feedbacks = this.feedbacks;
        this.isSaving.set(true);
        for (let i = 0; i < this.result.feedbacks.length; i++) {
            this.result.feedbacks[i].type = FeedbackType.MANUAL;
        }
        this.subscribeToSaveResponse(
            this.resultApi.createResultForExternalSubmission(this.exercise().id!, student.login, {
                score: this.result.score,
                rated: this.result.rated,
                feedbacks: this.result.feedbacks.map(toFeedbackRequest),
            }),
        );
    }

    /**
     * If http request is successful, pass it to onSaveSuccess, otherwise call onSaveError.
     * @param { Observable<ResultResponse> } result - Observable of Http request
     */
    private subscribeToSaveResponse(result: Observable<ResultResponse>) {
        result.subscribe({
            next: (res) => this.onSaveSuccess(res),
            error: () => this.onSaveError(),
        });
    }

    /**
     * Close modal window, indicate saving is done and broadcast that manual result is added.
     * @param { ResultResponse } result - Result of successful http request
     */
    onSaveSuccess(result: ResultResponse) {
        this.dialogRef.close(result);
        this.isSaving.set(false);
        this.eventManager.broadcast({ name: 'resultListModification', content: 'Added a manual result' });
    }

    /**
     * Indicate that saving didn't work by setting isSaving to false.
     */
    onSaveError() {
        this.isSaving.set(false);
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
