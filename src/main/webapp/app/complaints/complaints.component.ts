import { Component, OnInit, Input } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { Complaint } from 'app/entities/complaint';
import { Result } from 'app/entities/result';
import { HttpErrorResponse } from '@angular/common/http';
import { Moment } from 'moment';

@Component({
    selector: 'jhi-complaint-form',
    templateUrl: './complaints.component.html',
    providers: [JhiAlertService]
})
export class ComplaintsComponent implements OnInit {
    @Input() resultId: number;
    complaintText = '';
    alreadySubmitted: boolean;
    submittedDate: Moment;

    constructor(
        private complaintService: ComplaintService,
        private jhiAlertService: JhiAlertService
    ) { }

    ngOnInit(): void {
        this.complaintService.findByResultId(this.resultId).subscribe(
            res => {
                this.complaintText = res.body.complaintText;
                this.alreadySubmitted = true;
                this.submittedDate = res.body.submittedTime;
            },
            (err: HttpErrorResponse) => {
                // We can ignore 404, it simply means that there isn't a complain (yet!) associate with this result
                if (err.status !== 404) {
                    this.onError(err.message);
                }
            }
        );
    }

    createComplaint(): void {
        const complaint = new Complaint();
        complaint.complaintText = this.complaintText;
        complaint.result = new Result();
        complaint.result.id = this.resultId;

        this.complaintService.create(complaint).subscribe(
            res => {
                this.jhiAlertService.success('arTeMiSApp.complaint.submitted');
                this.submittedDate = res.body.submittedTime;
                this.alreadySubmitted = true;
            },
            (err: HttpErrorResponse) => {
                this.onError(err.message);
            }
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
