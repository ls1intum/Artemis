import { Component, EventEmitter, Input, Output } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { AlertService } from 'app/core/util/alert.service';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';

@Component({
    selector: 'jhi-complaint-response-form',
    templateUrl: './complaints-response-form.component.html',
    styleUrls: ['./complaints-response-form.component.scss'],
})
export class ComplaintsResponseFormComponent {
    @Input() complaint: Complaint;
    @Input() locked: boolean;
    @Output() submit: EventEmitter<ComplaintResponse> = new EventEmitter();
    ComplaintType = ComplaintType;
    complaintResponseText?: string;

    constructor(private complaintResponseService: ComplaintResponseService, private alertService: AlertService) {}

    respondToComplaint(acceptComplaint: boolean): void {
        if (!this.complaintResponseText || this.complaintResponseText.length <= 0) {
            this.alertService.error('artemisApp.complaintResponse.noText');
            return;
        }
        const complaintResponse = this.complaint.complaintResponse;
        // TODO: Resolve commits and more feedback requests separately:
        // mfr don't need an id, a new complaint response can be generated
        complaintResponse!.complaint = this.complaint;
        complaintResponse!.complaint.complaintResponse = undefined; // break circular structure
        complaintResponse!.complaint.accepted = acceptComplaint;

        this.resolveComplaint(complaintResponse!);
        this.submit.emit(complaintResponse);
    }

    private resolveComplaint(complaintResponse: ComplaintResponse): void {
        this.complaintResponseService
            .resolveComplaint(complaintResponse)
            .pipe()
            .subscribe(
                (response) => {
                    if (this.complaint.complaintType === ComplaintType.COMPLAINT) {
                        this.alertService.success('artemisApp.complaintResponse.created');
                    } else {
                        this.alertService.success('artemisApp.moreFeedbackResponse.created');
                    }

                    this.complaint.complaintResponse = response.body!;
                    // this.isLockedForLoggedInUser = false;
                },
                (err: HttpErrorResponse) => {
                    this.onError(err);
                },
            );
    }

    onError(httpErrorResponse: HttpErrorResponse): void {
        this.alertService.error('error.unexpectedError', {
            error: httpErrorResponse.message,
        });
    }
}
