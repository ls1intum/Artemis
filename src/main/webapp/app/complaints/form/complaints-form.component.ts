import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ComplaintService } from 'app/complaints/complaint.service';
import { Result } from 'app/entities/result.model';
import { HttpErrorResponse } from '@angular/common/http';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { Exercise } from 'app/entities/exercise.model';
import { onError } from 'app/shared/util/global.utils';
import { AlertService } from 'app/core/util/alert.service';
import { faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-complaint-form',
    templateUrl: './complaints-form.component.html',
    styleUrls: ['../complaints.scss'],
})
export class ComplaintsFormComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() resultId: number;
    @Input() examId?: number;
    @Input() remainingNumberOfComplaints: number;
    @Input() complaintType: ComplaintType;
    @Input() isCurrentUserSubmissionAuthor = false;
    @Output() submit: EventEmitter<void> = new EventEmitter();
    maxComplaintsPerCourse = 1;
    complaintText?: string;
    ComplaintType = ComplaintType;
    course?: Course;

    // Icons
    faInfoCircle = faInfoCircle;

    constructor(private complaintService: ComplaintService, private alertService: AlertService) {}

    ngOnInit(): void {
        this.course = getCourseFromExercise(this.exercise);
        if (this.exercise.course) {
            this.maxComplaintsPerCourse = this.exercise.teamMode ? this.exercise.course.maxTeamComplaints! : this.exercise.course.maxComplaints!;
        }
    }

    /**
     * Creates a new complaint on the provided result with the entered text and notifies the output emitter on success.
     */
    createComplaint(): void {
        const complaint = new Complaint();
        complaint.complaintText = this.complaintText;
        complaint.result = new Result();
        complaint.result.id = this.resultId;
        complaint.complaintType = this.complaintType;

        this.complaintService.create(complaint, this.examId).subscribe({
            next: () => {
                this.submit.emit();
            },
            error: (err: HttpErrorResponse) => {
                if (err?.error?.errorKey === 'tooManyComplaints') {
                    this.alertService.error('artemisApp.complaint.tooManyComplaints', { maxComplaintNumber: this.maxComplaintsPerCourse });
                } else if (err?.error?.errorKey === 'exceededComplaintTextLimit') {
                    this.alertService.error('artemisApp.complaint.exceededComplaintTextLimit', { maxComplaintTextLimit: this.course?.maxComplaintTextLimit });
                } else {
                    onError(this.alertService, err);
                }
            },
        });
    }

    /**
     * Calculates and returns the length of the entered text.
     */
    complaintTextLength(): number {
        const textArea: HTMLTextAreaElement = document.querySelector('#complainTextArea') as HTMLTextAreaElement;
        return textArea.value.length;
    }
}
