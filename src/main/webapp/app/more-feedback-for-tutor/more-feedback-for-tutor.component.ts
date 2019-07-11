import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ComplaintResponseService } from 'app/entities/complaint-response/complaint-response.service';
import { ComplaintResponse } from 'app/entities/complaint-response';
import { Complaint, ComplaintType } from 'app/entities/complaint';

@Component({
    selector: 'jhi-more-feedback-for-tutor-form',
    templateUrl: './more-feedback-for-tutor.component.html',
    providers: [JhiAlertService],
})
export class MoreFeedbackForTutorComponent implements OnInit {
    @Input() complaint: Complaint;
    @Input() isAllowedToRespond: boolean; // indicates if the tutor is allowed to respond
    complaintText = '';
    handled: boolean;
    complaintResponse: ComplaintResponse = new ComplaintResponse();

    constructor(private complaintService: ComplaintService, private jhiAlertService: JhiAlertService, private complaintResponseService: ComplaintResponseService) {}

    ngOnInit(): void {
        this.complaintText = this.complaint.complaintText;
        this.handled = this.complaint.accepted !== undefined;
        if (this.handled) {
            this.complaintResponseService.findByComplaintId(this.complaint.id).subscribe(complaintResponse => (this.complaintResponse = complaintResponse.body!));
        }
    }

    respondToComplaint(): void {
        if (this.complaintResponse.responseText.length <= 0 || !this.isAllowedToRespond) {
            return;
        }
        this.complaint.accepted = true;
        this.complaintResponse.complaint = this.complaint;
        this.complaintResponseService.create(this.complaintResponse).subscribe(
            response => {
                this.handled = true;
                this.jhiAlertService.success('artemisApp.moreFeedback.response.created');
                this.complaintResponse = response.body!;
            },
            (err: HttpErrorResponse) => {
                this.onError(err.message);
            },
        );
    }

    private onError(error: string): void {
        console.error(error);
        this.jhiAlertService.error(error, null, undefined);
    }
}
