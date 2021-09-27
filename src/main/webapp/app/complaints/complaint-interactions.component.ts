import { Component, Input, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import dayjs from 'dayjs';
import { ComplaintType } from 'app/entities/complaint.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { Exam } from 'app/entities/exam.model';
import { AccountService } from 'app/core/auth/account.service';
import { Submission } from 'app/entities/submission.model';

@Component({
    selector: 'jhi-complaint-interactions',
    templateUrl: './complaint-interactions.component.html',
})
export class ComplaintInteractionsComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() participation: StudentParticipation;
    @Input() result: Result;
    @Input() exam: Exam;
    // flag to indicate exam test run. Default set to false.
    @Input() testRun = false;
    isCurrentUserSubmissionAuthor: boolean;

    get isExamMode() {
        return this.exam != undefined;
    }

    submission: Submission;

    showRequestMoreFeedbackForm = false;
    // indicates if there is a complaint for the result of the submission
    hasComplaint = false;
    // indicates if more feedback was requested already
    hasRequestMoreFeedback = false;
    // the number of complaints that the student is still allowed to submit in the course. this is used for disabling the complain button.
    numberOfAllowedComplaints: number;
    showComplaintForm = false;
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
        if (this.isExamMode) {
            if (this.participation?.id && this.exercise && this.participation.results && this.participation.results.length > 0) {
                // Make sure results and participation are connected
                this.result = this.participation.results[0];
                this.result.participation = this.participation;
            }
        } else if (this.course) {
            // for normal exercises we track the number of allowed complaints
            if (this.course.complaintsEnabled) {
                this.complaintService.getNumberOfAllowedComplaintsInCourse(this.course.id!, this.exercise.teamMode).subscribe((allowedComplaints: number) => {
                    this.numberOfAllowedComplaints = allowedComplaints;
                });
            } else {
                this.numberOfAllowedComplaints = 0;
            }
        }
        if (this.participation.submissions && this.participation.submissions.length > 0) {
            this.submission = this.participation.submissions[0];
        }
        if (this.result && this.result.completionDate) {
            this.complaintService.findBySubmissionId(this.submission.id!).subscribe((res) => {
                if (res.body) {
                    if (res.body.complaintType == undefined || res.body.complaintType === ComplaintType.COMPLAINT) {
                        this.hasComplaint = true;
                    } else {
                        this.hasRequestMoreFeedback = true;
                    }
                }
            });
        }
        this.accountService.identity().then((user) => {
            if (this.participation && this.participation.student && user && user.id) {
                this.isCurrentUserSubmissionAuthor = this.participation.student.id === user.id;
            }
        });
    }

    get course(): Course | undefined {
        return this.exercise.course;
    }

    /**
     * We disable the component, if no complaint has been made by the user during the Student Review period, for exam exercises.
     */
    get noValidComplaintWasSubmittedWithinTheStudentReviewPeriod() {
        return !(this.testRun || this.isTimeOfComplaintValid || this.hasComplaint);
    }

    /**
     * This function is used to check whether the student is allowed to submit a complaint or not.
     * For exams, submitting a complaint is allowed within the Student Review Period, see {@link isWithinStudentReviewPeriod}.
     *
     * For normal course exercises, submitting a complaint is allowed within one week after the student received the
     * result. If the result was submitted after the assessment due date or the assessment due date is not set, the completion date of the result is checked. If the result was
     * submitted before the assessment due date, the assessment due date is checked, as the student can only see the result after the assessment due date.
     */
    get isTimeOfComplaintValid(): boolean {
        if (this.isExamMode) {
            if (!!this.result && !!this.result.completionDate) {
                return this.testRun || this.isWithinStudentReviewPeriod();
            }
        } else if (this.result && this.result.completionDate) {
            return this.isComplaintWithinTimeBoundaries();
        }
        return false;
    }

    /**
     * Analogous to isTimeOfComplaintValid but exams cannot have more feedback requests.
     */
    get isTimeOfFeedbackRequestValid(): boolean {
        if (!this.isExamMode && this.result && this.result.completionDate) {
            return this.isMoreFeedbackRequestWithinTimeBoundaries();
        }
        return false;
    }

    /**
     * Checks if a more feedback request can be filed
     */
    isMoreFeedbackRequestWithinTimeBoundaries(): boolean {
        if (this.course?.requestMoreFeedbackEnabled && this.course?.maxRequestMoreFeedbackTimeDays !== undefined) {
            const resultCompletionDate = dayjs(this.result.completionDate!);
            if (!this.exercise.assessmentDueDate || resultCompletionDate.isAfter(this.exercise.assessmentDueDate)) {
                return resultCompletionDate.isAfter(dayjs().subtract(this.course?.maxRequestMoreFeedbackTimeDays, 'day'));
            }
            return dayjs(this.exercise.assessmentDueDate).isAfter(dayjs().subtract(this.course?.maxRequestMoreFeedbackTimeDays, 'day'));
        }
        return false;
    }

    /**
     * Checks if a complaint can be filed
     */
    isComplaintWithinTimeBoundaries(): boolean {
        if (this.course?.complaintsEnabled && this.course?.maxComplaintTimeDays !== undefined) {
            const resultCompletionDate = dayjs(this.result.completionDate!);
            if (!this.exercise.assessmentDueDate || resultCompletionDate.isAfter(this.exercise.assessmentDueDate)) {
                return resultCompletionDate.isAfter(dayjs().subtract(this.course?.maxComplaintTimeDays, 'day'));
            }
            return dayjs(this.exercise.assessmentDueDate).isAfter(dayjs().subtract(this.course?.maxComplaintTimeDays, 'day'));
        }
        return false;
    }

    /**
     * A guard function used to indicate whether complaint submissions are valid.
     * These are only allowed if they are submitted within the student review period.
     */
    private isWithinStudentReviewPeriod(): boolean {
        if (this.testRun) {
            return true;
        } else if (this.exam.examStudentReviewStart && this.exam.examStudentReviewEnd) {
            return this.serverDateService.now().isBetween(this.exam.examStudentReviewStart, this.exam.examStudentReviewEnd);
        }
        return false;
    }

    /**
     * toggles between showing the complaint form
     */
    toggleComplaintForm() {
        this.showRequestMoreFeedbackForm = false;
        this.showComplaintForm = !this.showComplaintForm;
    }

    /**
     * toggles between showing the feedback request form
     */
    toggleRequestMoreFeedbackForm() {
        this.showComplaintForm = false;
        this.showRequestMoreFeedbackForm = !this.showRequestMoreFeedbackForm;
    }

    /**
     * Calculates the maximum number of complaints allowed for the exercise.
     * In case of exams, it returns an arbitrary number > 0, as we do not limit the number of complaints for exams
     */
    calculateMaxComplaints() {
        if (this.course) {
            return this.exercise.teamMode ? this.course.maxTeamComplaints! : this.course.maxComplaints!;
        }
        return 1;
    }
}
