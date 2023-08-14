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
    maxComplaintTextLimit: number;
    complaintText?: string;
    course?: Course;

    readonly ComplaintType = ComplaintType;

    constructor(
        private complaintService: ComplaintService,
        private alertService: AlertService,
    ) {}

    ngOnInit(): void {
        this.course = getCourseFromExercise(this.exercise);
        this.maxComplaintTextLimit = this.course?.maxComplaintTextLimit ?? 0;
        if (this.exercise.course) {
            // only set the complaint limit for course exercises, there are unlimited complaints for exams
            this.maxComplaintsPerCourse = this.exercise.teamMode ? this.exercise.course.maxTeamComplaints! : this.exercise.course.maxComplaints!;
        } else {
            // Complaints for exams should always allow at least 2000 characters. If the course limit is higher, the custom limit gets used.
            this.maxComplaintTextLimit = Math.max(2000, this.maxComplaintTextLimit);
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
        if (complaint.complaintText !== undefined && this.maxComplaintTextLimit < complaint.complaintText!.length) {
            this.alertService.error('artemisApp.complaint.exceededComplaintTextLimit', { maxComplaintTextLimit: this.maxComplaintTextLimit! });
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
