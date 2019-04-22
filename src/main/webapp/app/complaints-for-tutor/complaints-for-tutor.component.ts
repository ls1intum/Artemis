import { Component, Input, OnInit } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Moment } from 'moment';
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
    loading = true;
    complaint: Complaint;
    complaintText = '';
    alreadySubmitted: boolean;
    submittedDate: Moment;
    accepted: boolean;
    complaintResponse: ComplaintResponse = new ComplaintResponse();

    constructor(private complaintService: ComplaintService, private jhiAlertService: JhiAlertService, private complaintResponseService: ComplaintResponseService) {}

    ngOnInit(): void {
        this.complaintService.findByResultId(this.resultId).subscribe(
            res => {
                this.complaint = res.body;
                this.complaintText = this.complaint.complaintText;
                this.accepted = this.complaint.accepted;
                this.alreadySubmitted = true;
                this.loading = false;

                if (this.accepted) {
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

    submitComplaintResponse() {
        if (this.complaintResponse.responseText.length > 0) {
            this.complaintResponse.complaint = this.complaint;
            this.complaintResponseService.create(this.complaintResponse).subscribe(
                response => {
                    this.jhiAlertService.success('arTeMiSApp.textAssessment.complaintResponseCreated');
                    this.accepted = true;
                    this.complaintResponse = response.body;
                },
                (err: HttpErrorResponse) => {
                    this.onError(err.message);
                },
            );
        }
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, null);
    }
}
