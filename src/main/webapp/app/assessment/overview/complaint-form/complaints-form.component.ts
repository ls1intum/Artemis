import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, output } from '@angular/core';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { onError } from 'app/shared/util/global.utils';
import { ComplaintRequestDTO } from 'app/assessment/shared/entities/complaint-request-dto.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TextareaCounterComponent } from 'app/shared/textarea/textarea-counter.component';

@Component({
    selector: 'jhi-complaint-form',
    templateUrl: './complaints-form.component.html',
    styleUrls: ['../complaints.scss'],
    imports: [TranslateDirective, FormsModule, ArtemisTranslatePipe, TextareaCounterComponent],
})
export class ComplaintsFormComponent implements OnInit {
    private complaintService = inject(ComplaintService);
    private alertService = inject(AlertService);

    readonly exercise = input.required<Exercise>();
    readonly resultId = input.required<number>();
    readonly examId = input<number>();
    readonly complaintType = input.required<ComplaintType>();
    readonly isCurrentUserSubmissionAuthor = input(false);
    readonly onSubmit = output<void>();
    maxComplaintsPerCourse = 1;
    maxComplaintTextLimit: number;
    complaintText?: string;
    course?: Course;

    readonly ComplaintType = ComplaintType;

    ngOnInit(): void {
        this.course = getCourseFromExercise(this.exercise());
        this.maxComplaintTextLimit = this.course?.maxComplaintTextLimit ?? 0;
        const exercise = this.exercise();
        if (exercise.course) {
            // only set the complaint limit for course exercises, there are unlimited complaints for exams
            this.maxComplaintsPerCourse = exercise.teamMode ? exercise.course.maxTeamComplaints! : exercise.course.maxComplaints!;
        } else {
            // Complaints for exams should always allow at least 2000 characters. If the course limit is higher, the custom limit gets used.
            this.maxComplaintTextLimit = Math.max(2000, this.maxComplaintTextLimit);
        }
    }

    /**
     * Creates a new complaint on the provided result with the entered text and notifies the output emitter on success.
     */
    createComplaint(): void {
        const complaintRequest = new ComplaintRequestDTO();
        complaintRequest.resultId = this.resultId();
        complaintRequest.complaintType = this.complaintType();
        complaintRequest.complaintText = this.complaintText;
        complaintRequest.examId = this.examId();

        // TODO: Rethink global client error handling and adapt this line accordingly
        if (complaintRequest.complaintText !== undefined && this.maxComplaintTextLimit < complaintRequest.complaintText!.length) {
            this.alertService.error('artemisApp.complaint.exceededComplaintTextLimit', { maxComplaintTextLimit: this.maxComplaintTextLimit! });
            return;
        }

        this.complaintService.create(complaintRequest).subscribe({
            next: () => {
                this.onSubmit.emit();
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
