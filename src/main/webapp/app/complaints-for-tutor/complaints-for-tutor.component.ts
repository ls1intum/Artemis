import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ComplaintResponseService } from 'app/entities/complaint-response/complaint-response.service';
import { ComplaintResponse } from 'app/entities/complaint-response';
import { Complaint, ComplaintType } from 'app/entities/complaint';

@Component({
    selector: 'jhi-complaints-for-tutor-form',
    templateUrl: './complaints-for-tutor.component.html',
    providers: [JhiAlertService],
})
export class ComplaintsForTutorComponent implements OnInit {
    @Input() complaint: Complaint;
    @Input() isAllowedToRespond: boolean; // indicates if the tutor is allowed to respond (i.e. that he is not the assessor)
    // Indicates that the assessment should be updated after a complaint. Includes the corresponding complaint
    // that should be sent to the server along with the assessment update.
    @Output() updateAssessmentAfterComplaint = new EventEmitter<ComplaintResponse>();
    complaintText = '';
    handled: boolean;
    complaintResponse: ComplaintResponse = new ComplaintResponse();
    ComplaintType = ComplaintType;

    constructor(private complaintService: ComplaintService, private jhiAlertService: JhiAlertService, private complaintResponseService: ComplaintResponseService) {}

    ngOnInit(): void {
        this.complaintText = this.complaint.complaintText;
        this.handled = this.complaint.accepted !== undefined;
        if (this.handled) {
            this.complaintResponseService.findByComplaintId(this.complaint.id).subscribe(complaintResponse => {
                if (complaintResponse.body) {
                    this.complaintResponse = complaintResponse.body;
                }
            });
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
        this.complaint.accepted = acceptComplaint;
        this.complaintResponse.complaint = this.complaint;
        if (acceptComplaint && this.complaint.complaintType === ComplaintType.COMPLAINT) {
            // Tell the parent (assessment) component to update the corresponding result if the complaint was accepted.
            // The complaint is sent along with the assessment update by the parent to avoid additional requests.
            this.updateAssessmentAfterComplaint.emit(this.complaintResponse);
            this.handled = true;
        } else {
            // If the complaint was rejected or it was a more feedback request, just the complaint response is created.
            this.complaintResponseService.create(this.complaintResponse).subscribe(
                response => {
                    this.handled = true;
                    this.complaint.complaintType === ComplaintType.MORE_FEEDBACK
                        ? this.jhiAlertService.success('artemisApp.moreFeedbackResponse.created')
                        : this.jhiAlertService.success('artemisApp.complaintResponse.created');
                    this.complaintResponse = response.body!;
                },
                (err: HttpErrorResponse) => {
                    this.onError(err.message);
                },
            );
        }
    }

    private onError(error: string): void {
        console.error(error);
        this.jhiAlertService.error(error, null, undefined);
    }
}
