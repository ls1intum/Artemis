import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { ComplaintService } from 'app/complaints/complaint.service';
import { Result } from 'app/entities/result.model';
import { HttpErrorResponse } from '@angular/common/http';
import { Moment } from 'moment';
import { ComplaintResponseService } from 'app/complaints/complaint-response.service';
import { filter } from 'rxjs/operators';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { Exercise } from 'app/entities/exercise.model';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-complaint-form',
    templateUrl: './complaints.component.html',
    styleUrls: ['complaints.component.scss'],
    providers: [],
})
export class ComplaintsComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() resultId: number;
    @Input() submissionId: number;
    @Input() examId?: number;
    @Input() allowedComplaints: number; // the number of complaints that a student can still submit in the course
    @Input() maxComplaintsPerCourse: number;
    @Input() complaintType: ComplaintType;
    @Input() isCurrentUserSubmissionAuthor = false;
    @Output() submit: EventEmitter<void> = new EventEmitter();
    complaintText?: string;
    alreadySubmitted = false;
    submittedDate: Moment;
    accepted?: boolean;
    handled: boolean;
    complaintResponse: ComplaintResponse;
    ComplaintType = ComplaintType;
    loaded = true;

    constructor(private complaintService: ComplaintService, private alertService: AlertService, private complaintResponseService: ComplaintResponseService) {}

    ngOnInit(): void {
        this.complaintService
            .findBySubmissionId(this.submissionId)
            .pipe(filter((res) => !!res.body))
            .subscribe(
                (res) => {
                    const complaint = res.body!;
                    this.complaintText = complaint.complaintText;
                    this.alreadySubmitted = true;
                    this.submittedDate = complaint.submittedTime!;
                    this.accepted = complaint.accepted;
                    this.handled = this.accepted !== undefined;

                    if (this.handled) {
                        this.complaintResponse = complaint.complaintResponse!;
                    }
                },
                (error: HttpErrorResponse) => {
                    onError(this.alertService, error);
                },
            );
    }

    createComplaint(): void {
        this.loaded = false;
        const complaint = new Complaint();
        complaint.complaintText = this.complaintText;
        complaint.result = new Result();
        complaint.result.id = this.resultId;
        complaint.complaintType = this.complaintType;

        this.complaintService.create(complaint, this.examId).subscribe(
            (res) => {
                this.submittedDate = res.body!.submittedTime!;
                this.alreadySubmitted = true;
                if (complaint.complaintType === ComplaintType.COMPLAINT && !this.examId) {
                    // we do not track the number of complaints for exams
                    this.allowedComplaints--;
                }
                this.loaded = true;
                this.submit.emit();
            },
            (err: HttpErrorResponse) => {
                this.loaded = true;
                if (err && err.error && err.error.errorKey === 'toomanycomplaints') {
                    this.alertService.error('artemisApp.complaint.tooManyComplaints', { maxComplaintNumber: this.maxComplaintsPerCourse });
                } else {
                    onError(this.alertService, err);
                }
            },
        );
    }
}
