import { Component, Input, OnInit } from '@angular/core';
import { Result, ResultService } from 'app/entities/result';
import { ComplaintService, ComplaintType } from 'app/entities/complaint';
import { Exercise } from 'app/entities/exercise';
import { StudentParticipation } from 'app/entities/participation';

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
    // indicates if the result is older than one week. if it is, the complain button is disabled
    isTimeOfComplaintValid: boolean;
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
                    this.isTimeOfComplaintValid = this.resultService.isTimeOfComplaintValid(this.result, this.exercise);
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

    toggleComplaintForm() {
        this.showRequestMoreFeedbackForm = false;
        this.showComplaintForm = !this.showComplaintForm;
    }

    toggleRequestMoreFeedbackForm() {
        this.showComplaintForm = false;
        this.showRequestMoreFeedbackForm = !this.showRequestMoreFeedbackForm;
    }
}
