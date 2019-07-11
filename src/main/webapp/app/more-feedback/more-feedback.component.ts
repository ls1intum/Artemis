import { Component, OnInit, Input, Output, EventEmitter } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { Complaint, ComplaintType } from 'app/entities/complaint';
import { Result } from 'app/entities/result';
import { HttpErrorResponse } from '@angular/common/http';
import { Moment } from 'moment';
import { ComplaintResponseService } from 'app/entities/complaint-response/complaint-response.service';
import { ComplaintResponse } from 'app/entities/complaint-response';

@Component({
    selector: 'jhi-more-feedback-form',
    templateUrl: './more-feedback.component.html',
    providers: [JhiAlertService],
})
export class MoreFeedbackComponent implements OnInit {
    @Input() resultId: number;
    @Output() submit: EventEmitter<any> = new EventEmitter();
    complaintText = '';
    alreadySubmitted: boolean;
    submittedDate: Moment;
    accepted: boolean;
    handled: boolean;
    complaintResponse: ComplaintResponse;
    loaded = false;

    constructor(private complaintService: ComplaintService, private jhiAlertService: JhiAlertService, private complaintResponseService: ComplaintResponseService) {}

    ngOnInit(): void {
        this.complaintService.findByResultId(this.resultId).subscribe(
            res => {
                this.loaded = true;
                if (!res.body) {
                    return;
                }
                this.complaintText = res.body.complaintText;
                this.alreadySubmitted = true;
                this.submittedDate = res.body.submittedTime!;
                this.accepted = res.body.accepted;
                this.handled = this.accepted !== undefined;

                if (this.handled) {
                    this.complaintResponseService.findByComplaintId(res.body.id).subscribe(complaintResponse => (this.complaintResponse = complaintResponse.body!));
                }
            },
            (err: HttpErrorResponse) => {
                this.onError(err.message);
            },
        );
    }

    requestMoreFeedback(): void {
        this.loaded = false;
        const complaint = new Complaint();
        complaint.complaintText = this.complaintText;
        complaint.result = new Result();
        complaint.result.id = this.resultId;
        complaint.complaintType = ComplaintType.MORE_FEEDBACK;

        this.complaintService.create(complaint).subscribe(
            res => {
                this.loaded = true;
                this.submittedDate = res.body!.submittedTime!;
                this.alreadySubmitted = true;
                this.submit.emit();
            },
            (err: HttpErrorResponse) => {
                this.onError(err.message);
            },
        );
    }

    private onError(error: string) {
        console.error(error);
        this.jhiAlertService.error('error.http.400', null, undefined);
    }
}
