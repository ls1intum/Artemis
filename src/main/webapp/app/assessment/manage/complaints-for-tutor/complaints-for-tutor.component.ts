import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiInputDirective } from '@tumaet/ui-angular/input';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular/message';
import { TumAetUiProgressSpinnerComponent } from '@tumaet/ui-angular/progress-spinner';
import { Component, OnInit, inject, input, model, output, signal } from '@angular/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ComplaintResponseService } from 'app/assessment/manage/services/complaint-response.service';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { finalize, map } from 'rxjs/operators';
import { Exercise, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Router } from '@angular/router';
import { assessmentNavigateBack } from 'app/foundation/util/navigate-back.util';
import { Location } from '@angular/common';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { isAllowedToRespondToComplaintAction } from 'app/assessment/manage/services/assessment.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ComplaintResponseApi } from 'app/openapi/api/complaint-response-api';
import type { ComplaintResponseUpdate } from 'app/openapi/model/complaint-response-update';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TextareaCounterComponent } from 'app/shared-ui/textarea/textarea-counter.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

export type AssessmentAfterComplaint = { complaintResponse: ComplaintResponse; onSuccess: () => void; onError: () => void };

@Component({
    selector: 'jhi-complaints-for-tutor-form',
    templateUrl: './complaints-for-tutor.component.html',
    imports: [
        TranslateDirective,
        FormsModule,
        ArtemisTranslatePipe,
        TextareaCounterComponent,
        ArtemisDatePipe,
        TumAetUiButtonDirective,
        TumAetUiMessageComponent,
        TumAetUiInputDirective,
        TumAetUiProgressSpinnerComponent,
    ],
})
export class ComplaintsForTutorComponent implements OnInit {
    private alertService = inject(AlertService);
    private complaintResponseService = inject(ComplaintResponseService);
    private complaintResponseApi = inject(ComplaintResponseApi);
    private router = inject(Router);
    private location = inject(Location);

    readonly complaint = model.required<Complaint>();
    readonly isTestRun = input(false);
    readonly isAssessor = input(false);
    readonly zeroIndent = input(true);
    readonly exercise = input<Exercise>();
    readonly submission = input<Submission>();
    // Indicates that the assessment should be updated after a complaint. Includes the corresponding complaint
    // that should be sent to the server along with the assessment update.
    readonly updateAssessmentAfterComplaint = output<AssessmentAfterComplaint>();
    complaintText?: string;
    readonly handled = signal(false);
    // Deep [(ngModel)]="complaintResponse.responseText" two-way target → getter/setter facade over a signal so template reads stay reactive.
    private readonly _complaintResponse = signal<ComplaintResponse>(new ComplaintResponse());
    get complaintResponse(): ComplaintResponse {
        return this._complaintResponse();
    }
    set complaintResponse(value: ComplaintResponse) {
        this._complaintResponse.set(value);
    }
    complaintResponseUpdate!: ComplaintResponseUpdate; // instantiated before use in each lock-refresh / resolve flow (ngOnInit and respondToComplaint)
    ComplaintType = ComplaintType;
    readonly isLoading = signal(false);
    readonly showLockDuration = signal(false);
    readonly lockedByCurrentUser = signal(false);
    readonly isLockedForLoggedInUser = signal(false);
    course?: Course;
    readonly maxComplaintResponseTextLimit = signal<number>(undefined!);

    ngOnInit(): void {
        this.course = getCourseFromExercise(this.exercise());

        this.maxComplaintResponseTextLimit.set(this.course?.maxComplaintResponseTextLimit ?? 0);
        if (this.exercise()?.exerciseGroup) {
            // Exams should always allow at least 2000 characters
            this.maxComplaintResponseTextLimit.set(Math.max(2000, this.maxComplaintResponseTextLimit()));
        }

        if (this.complaint()) {
            this.complaintText = this.complaint().complaintText;
            this.handled.set(this.complaint().accepted !== undefined);
            if (this.handled()) {
                // Keep the already-initialized (non-undefined) complaint response if the complaint carries none.
                // respondToComplaint() detaches complaint().complaintResponse to break the circular structure, so
                // a late ngOnInit (zoneless change-detection ordering) must not overwrite the signal with undefined.
                if (this.complaint().complaintResponse) {
                    this.complaintResponse = this.complaint().complaintResponse!;
                }
                this.lockedByCurrentUser.set(false);
                this.showLockDuration.set(false);
            } else {
                if (this.isAllowedToRespond) {
                    if (this.complaint().complaintResponse) {
                        this.complaintResponseUpdate = { action: 'REFRESH_LOCK' };
                        this.refreshLock();
                    } else {
                        this.createLock();
                    }
                } else {
                    this.alertService.error('artemisApp.locks.notAllowedToRespond');
                }
            }
        }
    }

    private createLock() {
        this.isLoading.set(true);
        this.complaintResponseApi
            .lockComplaint(this.complaint().id!)
            .pipe(
                map((dto) => this.complaintResponseService.convertComplaintResponseFromServer(dto)),
                finalize(() => {
                    this.isLoading.set(false);
                }),
            )
            .subscribe({
                next: (response) => {
                    this.complaintResponse = response;
                    this.complaintResponse.complaint = this.complaint();
                    this.complaintResponse.complaint.complaintResponse = this.complaintResponse;
                    this.complaint.set(this.complaintResponse.complaint);
                    this.lockedByCurrentUser.set(true);
                    this.showLockDuration.set(true);
                    this.alertService.success('artemisApp.locks.acquired');
                },
                error: (err: HttpErrorResponse) => {
                    this.onError(err);
                },
            });
    }

    private refreshLock() {
        this.complaintResponse = this.complaint().complaintResponse!;
        this.showLockDuration.set(true);
        // if a lock exists, we have to check if it affects the currently logged-in user
        this.isLockedForLoggedInUser.set(this.complaintResponseService.isComplaintResponseLockedForLoggedInUser(this.complaintResponse, this.exercise()!));
        if (!this.isLockedForLoggedInUser()) {
            // update the lock
            this.isLoading.set(true);
            this.complaintResponseApi
                .refreshLockOrResolveComplaint(this.complaint().id!, this.complaintResponseUpdate)
                .pipe(
                    map((dto) => this.complaintResponseService.convertComplaintResponseFromServer(dto)),
                    finalize(() => {
                        this.isLoading.set(false);
                    }),
                )
                .subscribe({
                    next: (response) => {
                        this.complaintResponse = response;
                        this.complaintResponse.complaint = this.complaint();
                        this.complaintResponse.complaint.complaintResponse = this.complaintResponse;
                        this.complaint.set(this.complaintResponse.complaint);
                        this.lockedByCurrentUser.set(true);
                        this.alertService.success('artemisApp.locks.acquired');
                    },
                    error: (err: HttpErrorResponse) => {
                        this.onError(err);
                    },
                });
        } else {
            this.lockedByCurrentUser.set(false);
        }
    }

    navigateBack() {
        assessmentNavigateBack(this.location, this.router, this.exercise(), this.isTestRun());
    }

    removeLock() {
        this.complaintResponseApi.removeLockFromComplaint(this.complaint().id!).subscribe({
            next: () => {
                this.alertService.success('artemisApp.locks.lockRemoved');
                this.navigateBack();
            },
            error: (err: HttpErrorResponse) => {
                this.onError(err);
            },
        });
    }

    respondToComplaint(acceptComplaint: boolean): void {
        if (!this.complaintResponse.responseText || this.complaintResponse.responseText.length <= 0) {
            this.alertService.error('artemisApp.complaintResponse.noText');
            return;
        }
        if (this.complaintResponse.responseText.length > this.maxComplaintResponseTextLimit()) {
            this.alertService.error('artemisApp.complaint.exceededComplaintResponseTextLimit', {
                maxComplaintRespondTextLimit: this.maxComplaintResponseTextLimit(),
            });
            return;
        }
        if (!this.isAllowedToRespond) {
            return;
        }

        this.complaintResponse.complaint = this.complaint();
        this.complaintResponse.complaint.complaintResponse = undefined; // breaking circular structure
        this.complaintResponse.complaint.accepted = acceptComplaint;

        if (acceptComplaint && this.complaint().complaintType === ComplaintType.COMPLAINT) {
            // Tell the parent (assessment) component to update the corresponding result if the complaint was accepted.
            // The complaint is sent along with the assessment update by the parent to avoid additional requests.
            this.isLoading.set(true);
            this.updateAssessmentAfterComplaint.emit({
                complaintResponse: this.complaintResponse,
                onSuccess: () => {
                    this.isLoading.set(false);
                    this.handled.set(true);
                    this.showLockDuration.set(false);
                    this.lockedByCurrentUser.set(false);
                },
                onError: () => {
                    this.isLoading.set(false);
                },
            });
        } else {
            // If the complaint was rejected, or it was a more feedback request, just the complaint response is updated.
            this.complaintResponseUpdate = {
                action: 'RESOLVE_COMPLAINT',
                responseText: this.complaintResponse.responseText,
                complaintIsAccepted: this.complaintResponse.complaint.accepted,
            };
            this.resolveComplaint();
        }
    }

    private resolveComplaint() {
        this.isLoading.set(true);
        this.complaintResponseApi
            .refreshLockOrResolveComplaint(this.complaintResponse.complaint!.id!, this.complaintResponseUpdate)
            .pipe(
                map((dto) => this.complaintResponseService.convertComplaintResponseFromServer(dto)),
                finalize(() => {
                    this.isLoading.set(false);
                }),
            )
            .subscribe({
                next: (response) => {
                    this.handled.set(true);
                    if (this.complaint().complaintType === ComplaintType.MORE_FEEDBACK) {
                        this.alertService.success('artemisApp.moreFeedbackResponse.created');
                    } else {
                        this.alertService.success('artemisApp.complaintResponse.created');
                    }
                    this.complaintResponse = response;
                    this.complaintResponse.complaint = this.complaint();
                    this.complaintResponse.complaint.complaintResponse = this.complaintResponse;
                    this.complaintResponse.complaint.accepted = this.complaintResponseUpdate.complaintIsAccepted;
                    this.complaint.set(this.complaintResponse.complaint);
                    this.isLockedForLoggedInUser.set(false);
                    this.showLockDuration.set(false);
                    this.lockedByCurrentUser.set(false);
                },
                error: (err: HttpErrorResponse) => {
                    this.onError(err);
                },
            });
    }

    onError(httpErrorResponse: HttpErrorResponse) {
        const error = httpErrorResponse.error;
        if (error && error.errorKey && error.errorKey === 'complaintLock') {
            this.alertService.error(error.message, error.params);
        } else {
            this.alertService.error('error.unexpectedError', {
                error: httpErrorResponse.message,
            });
        }
    }

    /**
     * For team exercises, the team tutor is the assessor and handles both complaints and feedback requests themselves
     * For individual exercises, complaints are handled by a secondary reviewer and feedback requests by the assessor themselves
     * For exam test runs, the original assessor is allowed to respond to complaints.
     */
    get isAllowedToRespond(): boolean {
        return isAllowedToRespondToComplaintAction(this.isTestRun(), this.isAssessor(), this.complaint(), this.exercise());
    }

    /**
     * Calculates and returns the length of the entered text.
     */
    complaintResponseTextLength(): number {
        const textArea: HTMLTextAreaElement = document.querySelector('#responseTextArea') as HTMLTextAreaElement;
        return textArea.value.length;
    }
}
