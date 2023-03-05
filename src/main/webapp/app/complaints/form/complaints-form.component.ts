import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ComplaintService } from 'app/complaints/complaint.service';
import { AlertService } from 'app/core/util/alert.service';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { Course } from 'app/entities/course.model';
import { Exercise, getCourseFromExercise } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { onError } from 'app/shared/util/global.utils';

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
    // eslint-disable-next-line @angular-eslint/no-output-native
    @Output() submit: EventEmitter<void> = new EventEmitter();
    maxComplaintsPerCourse = 1;
    complaintText?: string;
    ComplaintType = ComplaintType;
    course?: Course;

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

        // TODO: Rethink global client error handling and adapt this line accordingly
        if (complaint.complaintText !== undefined && this.course!.maxComplaintTextLimit! < complaint.complaintText!.length) {
            this.alertService.error('artemisApp.complaint.exceededComplaintTextLimit', { maxComplaintTextLimit: this.course!.maxComplaintTextLimit! });
            return;
        }

        this.complaintService.create(complaint, this.examId).subscribe({
            next: () => {
                this.submit.emit();
            },
            error: (err: HttpErrorResponse) => {
                if (err?.error?.errorKey === 'tooManyComplaints') {
                    this.alertService.error('artemisApp.complaint.tooManyComplaints', { maxComplaintNumber: this.maxComplaintsPerCourse });
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
