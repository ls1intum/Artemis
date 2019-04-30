import { Component, OnInit, Input } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Moment } from 'moment';
import { ComplaintResponseService } from 'app/entities/complaint-response/complaint-response.service';
import { ComplaintResponse } from 'app/entities/complaint-response';

@Component({
    selector: 'jhi-complaints-for-tutor-form',
    templateUrl: './complaints-for-tutor.component.html',
    providers: [JhiAlertService],
})
export class ComplaintsForTutorComponent implements OnInit {
    @Input() resultId: number;
    loading = true;
    complaintText = '';
    alreadySubmitted: boolean;
    submittedDate: Moment;
    accepted: boolean;
    complaintResponse: ComplaintResponse = new ComplaintResponse();

    constructor(private complaintService: ComplaintService, private jhiAlertService: JhiAlertService, private complaintResponseService: ComplaintResponseService) {}

    ngOnInit(): void {
        this.complaintService.findByResultId(this.resultId).subscribe(
            res => {
                const body = res.body!;
                this.complaintText = body.complaintText;
                this.alreadySubmitted = true;
                this.submittedDate = body.submittedTime;
                this.loading = false;
                this.accepted = body.accepted;

                if (this.accepted) {
                    this.complaintResponseService.findByComplaintId(body.id).subscribe(complaintResponse => (this.complaintResponse = complaintResponse.body!));
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
            this.complaintResponseService.create(this.complaintResponse).subscribe(
                response => {
                    this.jhiAlertService.success('arTeMiSApp.textAssessment.complaintResponseCreated');
                    this.accepted = true;
                    this.complaintResponse = response.body!;
                },
                (err: HttpErrorResponse) => {
                    this.onError(err.message);
                },
            );
        }
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, undefined);
    }
}
