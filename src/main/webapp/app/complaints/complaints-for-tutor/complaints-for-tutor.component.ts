import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { finalize } from 'rxjs/operators';
import { Exercise } from 'app/entities/exercise.model';
import { ActivatedRoute, Router } from '@angular/router';
import { assessmentNavigateBack } from 'app/exercises/shared/navigate-back.util';
import { Location } from '@angular/common';
import { Submission } from 'app/entities/submission.model';
import { isAllowedToRespondToComplaintAction } from 'app/assessment/assessment.service';

@Component({
    selector: 'jhi-complaints-for-tutor-form',
    templateUrl: './complaints-for-tutor.component.html',
    providers: [],
})
export class ComplaintsForTutorComponent implements OnInit {
    @Input() complaint: Complaint;
    @Input() isTestRun = false;
    @Input() isAssessor = false;
    @Input() zeroIndent = true;
    @Input() exercise: Exercise | undefined;
    @Input() submission: Submission | undefined;
    // Indicates that the assessment should be updated after a complaint. Includes the corresponding complaint
    // that should be sent to the server along with the assessment update.
    @Output() updateAssessmentAfterComplaint = new EventEmitter<ComplaintResponse>();
    complaintText?: string;
    handled: boolean;
    complaintResponse: ComplaintResponse = new ComplaintResponse();
    ComplaintType = ComplaintType;
    isLoading = false;
    showLockDuration = false;
    showRemoveLockButton = false;
    isLockedForLoggedInUser = false;

    constructor(
        private alertService: AlertService,
        private complaintResponseService: ComplaintResponseService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private location: Location,
    ) {}

    ngOnInit(): void {
        if (this.complaint) {
            this.complaintText = this.complaint.complaintText;
            this.handled = this.complaint.accepted !== undefined;

            if (this.handled) {
                this.complaintResponse = this.complaint.complaintResponse!;
                this.showRemoveLockButton = false;
                this.showLockDuration = false;
            } else {
                if (this.isAllowedToRespond) {
                    if (this.complaint.complaintResponse) {
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
        this.isLoading = true;
        this.complaintResponseService
            .createLock(this.complaint.id!)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                (response) => {
                    this.complaintResponse = response.body!;
                    this.complaint = this.complaintResponse.complaint!;
                    this.showRemoveLockButton = true;
                    this.showLockDuration = true;
                    this.alertService.success('artemisApp.locks.acquired');
                },
                (err: HttpErrorResponse) => {
                    this.onError(err);
                },
            );
    }

    private refreshLock() {
        this.complaintResponse = this.complaint.complaintResponse!;
        this.showLockDuration = true;
        // a lock exists we have to check if it affects the currently logged in user
        this.isLockedForLoggedInUser = this.complaintResponseService.isComplaintResponseLockedForLoggedInUser(this.complaintResponse, this.exercise!);
        if (!this.isLockedForLoggedInUser) {
            // update the lock
            this.isLoading = true;
            this.complaintResponseService
                .refreshLock(this.complaint.id!)
                .pipe(
                    finalize(() => {
                        this.isLoading = false;
                    }),
                )
                .subscribe(
                    (response) => {
                        this.complaintResponse = response.body!;
                        this.complaint = this.complaintResponse.complaint!;
                        this.showRemoveLockButton = true;
                        this.alertService.success('artemisApp.locks.acquired');
                    },
                    (err: HttpErrorResponse) => {
                        this.onError(err);
                    },
                );
        } else {
            this.showRemoveLockButton = false;
        }
    }

    navigateBack() {
        assessmentNavigateBack(this.location, this.router, this.exercise, this.submission, this.isTestRun);
    }

    removeLock() {
        this.complaintResponseService.removeLock(this.complaint.id!).subscribe(
            () => {
                this.alertService.success('artemisApp.locks.lockRemoved');
                this.navigateBack();
            },
            (err: HttpErrorResponse) => {
                this.onError(err);
            },
        );
    }

    respondToComplaint(acceptComplaint: boolean): void {
        if (!this.complaintResponse.responseText || this.complaintResponse.responseText.length <= 0) {
            this.alertService.error('artemisApp.complaintResponse.noText');
            return;
        }
        if (!this.isAllowedToRespond) {
            return;
        }

        this.complaintResponse.complaint = this.complaint;
        this.complaintResponse.complaint.complaintResponse = undefined; // breaking circular structure
        this.complaintResponse.complaint!.accepted = acceptComplaint;

        if (acceptComplaint && this.complaint.complaintType === ComplaintType.COMPLAINT) {
            // Tell the parent (assessment) component to update the corresponding result if the complaint was accepted.
            // The complaint is sent along with the assessment update by the parent to avoid additional requests.
            this.updateAssessmentAfterComplaint.emit(this.complaintResponse);
            this.handled = true;
            this.showLockDuration = false;
            this.showRemoveLockButton = false;
        } else {
            // If the complaint was rejected or it was a more feedback request, just the complaint response is updated.
            this.resolveComplaint();
        }
    }

    private resolveComplaint() {
        this.isLoading = true;
        this.complaintResponseService
            .resolveComplaint(this.complaintResponse)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                (response) => {
                    this.handled = true;
                    if (this.complaint.complaintType === ComplaintType.MORE_FEEDBACK) {
                        this.alertService.success('artemisApp.moreFeedbackResponse.created');
                    } else {
                        this.alertService.success('artemisApp.complaintResponse.created');
                    }
                    this.complaintResponse = response.body!;
                    this.complaint = this.complaintResponse.complaint!;
                    this.isLockedForLoggedInUser = false;
                    this.showLockDuration = false;
                    this.showRemoveLockButton = false;
                },
                (err: HttpErrorResponse) => {
                    this.onError(err);
                },
            );
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
     * For team exercises, the team tutor is the assessor and handles both complaints and feedback requests himself
     * For individual exercises, complaints are handled by a secondary reviewer and feedback requests by the assessor himself
     * For exam test runs, the original assessor is allowed to respond to complaints.
     */
    get isAllowedToRespond(): boolean {
        return isAllowedToRespondToComplaintAction(this.exercise?.isAtLeastInstructor ?? false, this.isTestRun, this.isAssessor, this.complaint, this.exercise);
    }
}
