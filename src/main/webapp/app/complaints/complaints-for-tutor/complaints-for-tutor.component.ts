import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintService } from 'app/complaints/complaint.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { finalize } from 'rxjs/operators';
import { Exercise } from 'app/entities/exercise.model';
import { AccountService } from 'app/core/auth/account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { assessmentNavigateBack } from 'app/exercises/shared/navigate-back.util';
import { Location } from '@angular/common';
import { Submission } from 'app/entities/submission.model';

@Component({
    selector: 'jhi-complaints-for-tutor-form',
    templateUrl: './complaints-for-tutor.component.html',
    providers: [],
})
export class ComplaintsForTutorComponent implements OnInit {
    @Input() complaint: Complaint;
    @Input() isAllowedToRespond: boolean;
    @Input() isTestRun = true;
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
    isLockedForLoggedInUser = false;
    showRemoveLockButton = false;

    constructor(
        private complaintService: ComplaintService,
        private jhiAlertService: JhiAlertService,
        private complaintResponseService: ComplaintResponseService,
        private accountService: AccountService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
        private location: Location,
    ) {}

    ngOnInit(): void {
        if (this.complaint) {
            // a complaint is handled if it is either accepted or denied and a complaint response exists
            this.handled = this.complaint.accepted !== undefined && this.complaint.complaintResponse !== undefined;

            this.complaintText = this.complaint.complaintText;
            if (this.handled) {
                // handled complaint --> just display response
                this.complaintResponse = this.complaint.complaintResponse!;
                this.decideToShowDeleteLockButtonOrNot();
            } else {
                if (this.complaint.complaintResponse) {
                    // unhandled complaint where a complaint response exists --> update lock if allowed
                    this.complaintResponse = this.complaint.complaintResponse;
                    this.isLockedForLoggedInUser = this.complaintResponseService.isComplaintResponseLockedForLoggedInUser(this.complaintResponse, this.exercise!);
                    if (!this.isLockedForLoggedInUser) {
                        // update the lock
                        this.isLoading = true;
                        this.complaintResponseService
                            .updateLock(this.complaint.id!)
                            .pipe(
                                finalize(() => {
                                    this.isLoading = false;
                                }),
                            )
                            .subscribe(
                                (response) => {
                                    this.complaintResponse = response.body!;
                                    this.complaint = this.complaintResponse.complaint!;
                                    this.decideToShowDeleteLockButtonOrNot();
                                },
                                (err: HttpErrorResponse) => {
                                    this.onError(err);
                                },
                            );
                    }
                } else {
                    // unhandled complaint where no complaint response exists --> create a new initial complaint response
                    this.isLoading = true;
                    this.complaintResponseService
                        .createInitialResponse(this.complaint.id!)
                        .pipe(
                            finalize(() => {
                                this.isLoading = false;
                            }),
                        )
                        .subscribe(
                            (response) => {
                                this.complaintResponse = response.body!;
                                this.complaint = this.complaintResponse.complaint!;
                                this.decideToShowDeleteLockButtonOrNot();
                            },
                            (err: HttpErrorResponse) => {
                                this.onError(err);
                            },
                        );
                }
            }
        }
    }

    navigateBack() {
        assessmentNavigateBack(this.location, this.router, this.exercise, this.submission, this.isTestRun);
    }

    removeLock() {
        this.complaintResponseService.removeLock(this.complaint.id!).subscribe(
            () => {
                this.navigateBack();
            },
            (err: HttpErrorResponse) => {
                this.onError(err);
            },
        );
    }

    decideToShowDeleteLockButtonOrNot() {
        if (!this.handled && this.complaintResponse?.isCurrentlyLocked && this.complaintResponse?.reviewer?.login === this.accountService.userIdentity?.login) {
            this.showRemoveLockButton = true;
        } else {
            this.showRemoveLockButton = false;
        }
    }

    respondToComplaint(acceptComplaint: boolean): void {
        if (!this.complaintResponse.responseText || this.complaintResponse.responseText.length <= 0) {
            this.jhiAlertService.error('artemisApp.complaintResponse.noText');
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
        } else {
            // If the complaint was rejected or it was a more feedback request, just the complaint response is updated.
            this.isLoading = true;
            this.complaintResponseService
                .update(this.complaintResponse)
                .pipe(
                    finalize(() => {
                        this.isLoading = false;
                    }),
                )
                .subscribe(
                    (response) => {
                        this.handled = true;
                        // eslint-disable-next-line chai-friendly/no-unused-expressions
                        this.complaint.complaintType === ComplaintType.MORE_FEEDBACK
                            ? this.jhiAlertService.success('artemisApp.moreFeedbackResponse.created')
                            : this.jhiAlertService.success('artemisApp.complaintResponse.created');
                        this.complaintResponse = response.body!;
                        this.complaint = this.complaintResponse.complaint!;
                    },
                    (err: HttpErrorResponse) => {
                        this.onError(err);
                    },
                );
        }
    }

    onError(httpErrorResponse: HttpErrorResponse) {
        const error = httpErrorResponse.error;
        if (error && error.errorKey && error.errorKey === 'complaintLock') {
            this.jhiAlertService.error(error.message, error.params);
        } else {
            this.jhiAlertService.error('error.unexpectedError', {
                error: httpErrorResponse.message,
            });
        }
    }
}
