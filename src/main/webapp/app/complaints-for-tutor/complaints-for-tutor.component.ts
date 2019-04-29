import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { HttpErrorResponse } from '@angular/common/http';
import { ComplaintResponseService } from 'app/entities/complaint-response/complaint-response.service';
import { ComplaintResponse } from 'app/entities/complaint-response';
import { Complaint } from 'app/entities/complaint';

@Component({
    selector: 'jhi-complaints-for-tutor-form',
    templateUrl: './complaints-for-tutor.component.html',
    providers: [JhiAlertService],
})
export class ComplaintsForTutorComponent implements OnInit {
    @Input() resultId: number;
    // Indicates that the assessment should be updated after a complaint. Includes the corresponding complaint
    // that should be sent to the server along with the assessment update.
    @Output() updateAssessmentAfterComplaint = new EventEmitter<ComplaintResponse>();
    loading = true;
    complaint: Complaint;
    complaintText = '';
    alreadySubmitted: boolean;
    handled: boolean;
    complaintResponse: ComplaintResponse = new ComplaintResponse();

    constructor(private complaintService: ComplaintService, private jhiAlertService: JhiAlertService, private complaintResponseService: ComplaintResponseService) {}

    ngOnInit(): void {
        this.complaintService.findByResultId(this.resultId).subscribe(
            res => {
                this.complaint = res.body;
                this.complaintText = this.complaint.complaintText;
                this.handled = this.complaint.accepted !== undefined;
                this.alreadySubmitted = true;
                this.loading = false;

                if (this.handled) {
                    this.complaintResponseService.findByComplaintId(res.body.id).subscribe(complaintResponse => (this.complaintResponse = complaintResponse.body));
                }
            },
            (err: HttpErrorResponse) => {
                // We can ignore 404, it simply means that there isn't a complain (yet!) associate with this result
                if (err.status !== 404) {
                    this.onError(err.message);
                }
            },
        );
    }

    respondToComplaint(acceptComplaint: boolean): void {
        if (this.complaintResponse.responseText.length > 0) {
            this.handled = true;
            this.complaint.accepted = acceptComplaint;
            this.complaintResponse.complaint = this.complaint;
            if (acceptComplaint) {
                // Tell the parent (assessment) component to update the corresponding result if the complaint was accepted.
                // The complaint is sent along with the assessment update by the parent to avoid additional requests.
                this.updateAssessmentAfterComplaint.emit(this.complaintResponse);
            } else {
                // If the complaint was rejected, just the complaint response is created.
                this.complaintResponseService.create(this.complaintResponse).subscribe(
                    response => {
                        this.jhiAlertService.success('arTeMiSApp.textAssessment.complaintResponseCreated');
                        this.complaintResponse = response.body;
                    },
                    (err: HttpErrorResponse) => {
                        this.onError(err.message);
                    },
                );
            }
        }
    }

    private onError(error: string): void {
        console.error(error);
        this.jhiAlertService.error(error, null, null);
    }
}
