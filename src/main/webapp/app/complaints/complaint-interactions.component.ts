import { Component, Input, OnInit } from '@angular/core';
import { Result, ResultService } from 'app/entities/result';
import { ComplaintService, ComplaintType } from 'app/entities/complaint';
import { Exercise } from 'app/entities/exercise';
import { StudentParticipation } from 'app/entities/participation';
import * as moment from 'moment';
import { MAX_COMPLAINT_TIME_WEEKS } from 'app/complaints/complaint.constants';

@Component({
    selector: 'jhi-complaint-interactions',
    templateUrl: './complaint-interactions.component.html',
})
export class ComplaintInteractionsComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() participation: StudentParticipation;
    @Input() result: Result;

    showRequestMoreFeedbackForm = false;
    // indicates if there is a complaint for the result of the submission
    hasComplaint = false;
    // indicates if more feedback was requested already
    hasRequestMoreFeedback = false;
    // the number of complaints that the student is still allowed to submit in the course. this is used for disabling the complain button.
    numberOfAllowedComplaints: number;
    showComplaintForm = false;
    ComplaintType = ComplaintType;

    constructor(private complaintService: ComplaintService, private resultService: ResultService) {}

    ngOnInit(): void {
        if (this.exercise.course) {
            this.complaintService.getNumberOfAllowedComplaintsInCourse(this.exercise.course.id).subscribe((allowedComplaints: number) => {
                this.numberOfAllowedComplaints = allowedComplaints;
            });

            if (this.participation.submissions && this.participation.submissions.length > 0) {
                if (this.result && this.result.completionDate) {
                    this.complaintService.findByResultId(this.result.id).subscribe(res => {
                        if (res.body) {
                            if (res.body.complaintType == null || res.body.complaintType === ComplaintType.COMPLAINT) {
                                this.hasComplaint = true;
                            } else {
                                this.hasRequestMoreFeedback = true;
                            }
                        }
                    });
                }
            }
        }
    }

    /**
     * This function is used to check whether the student is allowed to submit a complaint or not. Submitting a complaint is allowed within one week after the student received the
     * result. If the result was submitted after the assessment due date or the assessment due date is not set, the completion date of the result is checked. If the result was
     * submitted before the assessment due date, the assessment due date is checked, as the student can only see the result after the assessment due date.
     */
    get isTimeOfComplaintValid(): boolean {
        if (this.result && this.result.completionDate) {
            const resultCompletionDate = moment(this.result.completionDate!);
            if (!this.exercise.assessmentDueDate || resultCompletionDate.isAfter(this.exercise.assessmentDueDate)) {
                return resultCompletionDate.isAfter(moment().subtract(MAX_COMPLAINT_TIME_WEEKS, 'week'));
            }
            return moment(this.exercise.assessmentDueDate).isAfter(moment().subtract(MAX_COMPLAINT_TIME_WEEKS, 'week'));
        } else {
            return false;
        }
    }

    toggleComplaintForm() {
        this.showRequestMoreFeedbackForm = false;
        this.showComplaintForm = !this.showComplaintForm;
    }

    toggleRequestMoreFeedbackForm() {
        this.showComplaintForm = false;
        this.showRequestMoreFeedbackForm = !this.showRequestMoreFeedbackForm;
    }
}
