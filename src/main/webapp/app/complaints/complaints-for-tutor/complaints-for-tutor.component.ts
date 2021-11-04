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
    @Input() isAtLeastInstructor: boolean;
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
            console.log(this.complaint);
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

    public updateComplaint(complaintResponse: ComplaintResponse) {
        this.complaint.complaintResponse = complaintResponse;
        this.complaint.accepted = complaintResponse.complaint?.accepted;
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
        if (this.isAtLeastInstructor) {
            return true;
        }
        if (this.complaint!.team) {
            return this.isAssessor;
        } else {
            if (this.isTestRun) {
                return this.isAssessor;
            }
            if (this.complaint.result && this.complaint.result.assessor == undefined) {
                return true;
            }
            return this.complaint!.complaintType === ComplaintType.COMPLAINT ? !this.isAssessor : this.isAssessor;
        }
    }
}
