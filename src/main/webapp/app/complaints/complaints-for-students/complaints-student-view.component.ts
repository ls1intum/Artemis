import { Component, Input, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { Exam } from 'app/entities/exam.model';
import { AccountService } from 'app/core/auth/account.service';
import { Submission } from 'app/entities/submission.model';
import { filter } from 'rxjs/operators';
import dayjs from 'dayjs';

@Component({
    selector: 'jhi-complaint-student-view',
    templateUrl: './complaints-student-view.component.html',
})
export class ComplaintsStudentViewComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() participation: StudentParticipation;
    @Input() result: Result;
    @Input() exam: Exam;
    // flag to indicate exam test run. Default set to false.
    @Input() testRun = false;

    submission: Submission;
    complaint: Complaint;
    course?: Course;
    // Indicates what type of complaint is currently created by the student. Undefined if the student didn't click on a button yet.
    formComplaintType?: ComplaintType;
    // the number of complaints that the student is still allowed to submit in the course. this is used for disabling the complain button.
    numberOfAllowedComplaints = 0;
    isCurrentUserSubmissionAuthor: boolean;
    isExamMode: boolean;
    showComplaintsSection: boolean;
    timeOfFeedbackRequestValid: boolean;
    timeOfComplaintValid: boolean;

    ComplaintType = ComplaintType;

    constructor(
        private complaintService: ComplaintService,
        private activatedRoute: ActivatedRoute,
        private serverDateService: ArtemisServerDateService,
        private accountService: AccountService,
    ) {}

    /**
     * Loads the number of allowed complaints and feedback requests
     */
    ngOnInit(): void {
        this.course = this.exercise.course;
        this.isExamMode = this.exam != undefined;
        if (this.participation?.id && this.participation.results && this.participation.results.length > 0) {
            // Make sure results and participation are connected
            this.result = this.participation.results[0];
            this.result.participation = this.participation;
        }
        if (this.participation.submissions && this.participation.submissions.length > 0) {
            this.submission = this.participation.submissions[0];
        }
        // for normal exercises we track the number of allowed complaints
        if (this.course?.complaintsEnabled) {
            this.complaintService.getNumberOfAllowedComplaintsInCourse(this.course!.id!, this.exercise.teamMode).subscribe((allowedComplaints: number) => {
                this.numberOfAllowedComplaints = allowedComplaints;
            });
        }
        this.loadPotentialComplaint();
        this.accountService.identity().then((user) => {
            if (this.participation && this.participation.student && user?.id) {
                this.isCurrentUserSubmissionAuthor = this.participation.student.id === user.id;
            }
        });

        this.timeOfFeedbackRequestValid = this.isTimeOfFeedbackRequestValid();
        this.timeOfComplaintValid = this.isTimeOfComplaintValid();
        this.showComplaintsSection = this.isShowComplaintsSection();
    }

    /**
     * Sets the complaint if a complaint and a valid result exists
     */
    loadPotentialComplaint(): void {
        if (this.result && this.result.completionDate) {
            this.complaintService
                .findBySubmissionId(this.submission.id!)
                .pipe(filter((res) => !!res.body))
                .subscribe((res) => {
                    this.complaint = res.body!;
                });
        }
    }

    /**
     * This function is used to check whether the student is allowed to submit a complaint or not.
     * For exams, submitting a complaint is allowed within the Student Review Period, see {@link isWithinExamReviewPeriod}.
     *
     * For normal course exercises, submitting a complaint is allowed within one week after the student received the
     * result. If the result was submitted after the assessment due date or the assessment due date is not set, the completion date of the result is checked. If the result was
     * submitted before the assessment due date, the assessment due date is checked, as the student can only see the result after the assessment due date.
     */
    private isTimeOfComplaintValid(): boolean {
        if (this.result?.completionDate) {
            if (this.isExamMode) {
                return this.isWithinExamReviewPeriod();
            }
            return this.isCompletionDateWithinCourseReviewPeriod(this.result.completionDate);
        }
        return false;
    }

    /**
     * Analogous to isTimeOfComplaintValid but exams cannot have more feedback requests.
     */
    private isTimeOfFeedbackRequestValid(): boolean {
        if (!this.isExamMode && this.result?.completionDate) {
            return this.isCompletionDateWithinCourseReviewPeriod(this.result.completionDate);
        }
        return false;
    }

    /**
     * Determines whether or not to show the complaint section
     */
    private isShowComplaintsSection(): boolean {
        if (this.isExamMode) {
            return this.isTimeOfComplaintValid() || !!this.complaint;
        } else {
            return !!(this.course?.complaintsEnabled || this.course?.requestMoreFeedbackEnabled);
        }
    }

    /**
     * Checks if a complaint (either actual complaint or more feedback request) can be filed
     */
    private isCompletionDateWithinCourseReviewPeriod(completionDate: dayjs.Dayjs): boolean {
        if (!this.course?.maxRequestMoreFeedbackTimeDays) {
            return false;
        }
        if (!this.exercise.assessmentDueDate || completionDate.isAfter(this.exercise.assessmentDueDate)) {
            return completionDate.isAfter(dayjs().subtract(this.course.maxRequestMoreFeedbackTimeDays, 'day'));
        }
        return this.exercise.assessmentDueDate.isAfter(dayjs().subtract(this.course.maxRequestMoreFeedbackTimeDays, 'day'));
    }

    /**
     * A guard function used to indicate whether complaint submissions are valid.
     * These are only allowed if they are submitted within the student review period.
     */
    private isWithinExamReviewPeriod(): boolean {
        if (this.testRun) {
            return true;
        } else if (this.exam.examStudentReviewStart && this.exam.examStudentReviewEnd) {
            return this.serverDateService.now().isBetween(this.exam.examStudentReviewStart, this.exam.examStudentReviewEnd);
        }
        return false;
    }
}
