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
    @Input() allowedComplaints: number; // the number of complaints that a student can still submit in the course
    complaintText = '';
    alreadySubmitted: boolean;
    submittedDate: Moment;
    accepted: boolean;
    handled: boolean;
    complaintResponse: ComplaintResponse;

    readonly maxComplaintNumberPerStudent = 3; // please note that this number has to be the same as in Constant.java on the server

    constructor(private complaintService: ComplaintService, private jhiAlertService: JhiAlertService, private complaintResponseService: ComplaintResponseService) {}

    ngOnInit(): void {
        this.complaintService.findByResultId(this.resultId).subscribe(
            res => {
                if (!res.body) {
                    return;
                }
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
                this.onError(err.message);
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
                this.submittedDate = res.body.submittedTime;
                this.alreadySubmitted = true;
                this.allowedComplaints--;
            },
            (err: HttpErrorResponse) => {
                if (err && err.error && err.error.errorKey === 'toomanycomplaints') {
                    this.jhiAlertService.error('arTeMiSApp.complaint.tooManyComplaints', { maxComplaintNumber: this.maxComplaintNumberPerStudent });
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
        this.jhiAlertService.error('error.http.400', null, null);
    }
}
