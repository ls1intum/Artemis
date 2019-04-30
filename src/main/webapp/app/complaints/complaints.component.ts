import { Component, OnInit, Input } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { Complaint } from 'app/entities/complaint';
import { Result } from 'app/entities/result';
import { HttpErrorResponse } from '@angular/common/http';
import { Moment } from 'moment';
import { ComplaintResponseService } from 'app/entities/complaint-response/complaint-response.service';
import { ComplaintResponse } from 'app/entities/complaint-response';

@Component({
    selector: 'jhi-complaint-form',
    templateUrl: './complaints.component.html',
    providers: [JhiAlertService],
})
export class ComplaintsComponent implements OnInit {
    @Input() resultId: number;
    complaintText = '';
    alreadySubmitted: boolean;
    submittedDate: Moment;
    accepted: boolean;
    handled: boolean;
    complaintResponse: ComplaintResponse;

    readonly maxComplaintNumberPerStudent = 3;

    constructor(private complaintService: ComplaintService, private jhiAlertService: JhiAlertService, private complaintResponseService: ComplaintResponseService) {}

    ngOnInit(): void {
        this.complaintService.findByResultId(this.resultId).subscribe(
            res => {
                this.complaintText = res.body.complaintText;
                this.alreadySubmitted = true;
                this.submittedDate = res.body.submittedTime;
                this.accepted = res.body.accepted;
                this.handled = this.accepted !== undefined;

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

    createComplaint(): void {
        const complaint = new Complaint();
        complaint.complaintText = this.complaintText;
        complaint.result = new Result();
        complaint.result.id = this.resultId;

        this.complaintService.create(complaint).subscribe(
            res => {
                this.jhiAlertService.success('arTeMiSApp.complaint.created');
                this.submittedDate = res.body.submittedTime;
                this.alreadySubmitted = true;
            },
            (err: HttpErrorResponse) => {
                if (err && err.error && err.error.errorKey === 'toomanycomplaints') {
                    this.jhiAlertService.error(`Complaint limit reached. You cannot have more than ${this.maxComplaintNumberPerStudent} at the same time.`);
                } else {
                    this.onError(err.message);
                }
            },
        );
    }

    requestMoreFeedback(): void {
        // TODO: implement
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error(error, null, null);
    }
}
