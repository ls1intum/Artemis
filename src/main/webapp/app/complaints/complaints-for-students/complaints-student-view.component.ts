import { Component, Input, OnInit } from '@angular/core';
import { Exercise, getCourseFromExercise } from 'app/entities/exercise.model';
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
import dayjs from 'dayjs/esm';
import { HttpResponse } from '@angular/common/http';
import { faInfoCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-complaint-student-view',
    templateUrl: './complaints-student-view.component.html',
    styleUrls: ['../complaints.scss'],
})
export class ComplaintsStudentViewComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() participation: StudentParticipation;
    @Input() result?: Result;
    @Input() exam: Exam;
    // flag to indicate exam test run. Default set to false.
    @Input() testRun = false;

    submission: Submission;
    complaint: Complaint;
    course?: Course;
    // Indicates what type of complaint is currently created by the student. Undefined if the student didn't click on a button yet.
    formComplaintType?: ComplaintType;
    // The number of complaints that the student is still allowed to submit in the course.
    remainingNumberOfComplaints = 0;
    isCorrectUserToFileAction = false;
    isExamMode: boolean;
    showSection = false;
    timeOfFeedbackRequestValid = false;
    timeOfComplaintValid = false;

    ComplaintType = ComplaintType;

    // Icons
    faInfoCircle = faInfoCircle;

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
        this.course = getCourseFromExercise(this.exercise);
        this.isExamMode = this.exam != undefined;
        if (this.participation && this.result?.completionDate) {
            // Make sure results and participation are connected
            this.result.participation = this.participation;

            if (this.participation.submissions && this.participation.submissions.length > 0) {
                this.submission = this.participation.submissions.sort((a, b) => b.id! - a.id!)[0];
            }
            // for course exercises we track the number of allowed complaints
            if (this.course?.complaintsEnabled) {
                this.complaintService.getNumberOfAllowedComplaintsInCourse(this.course!.id!, this.exercise.teamMode).subscribe((allowedComplaints: number) => {
                    this.remainingNumberOfComplaints = allowedComplaints;
                });
            }
            this.loadPotentialComplaint();
            this.accountService.identity().then((user) => {
                if (user?.id) {
                    if (this.participation?.student) {
                        this.isCorrectUserToFileAction = this.participation.student.id === user.id;
                    } else if (this.participation.team?.students) {
                        this.isCorrectUserToFileAction = !!this.participation.team.students.find((student) => student.id === user.id);
                    }
                }
            });

            this.timeOfFeedbackRequestValid = this.isTimeOfFeedbackRequestValid();
            this.timeOfComplaintValid = this.isTimeOfComplaintValid();
            this.showSection = this.getSectionVisibility();
        }
    }

    /**
     * Sets the complaint if a complaint and a valid result exists
     */
    loadPotentialComplaint(): void {
        this.complaintService
            .findBySubmissionId(this.submission.id!)
            .pipe(filter((res) => !!res.body))
            .subscribe((res: HttpResponse<Complaint>) => {
                this.complaint = res.body!;
            });
    }

    /**
     * Determines whether to show the section
     */
    private getSectionVisibility(): boolean {
        if (this.isExamMode) {
            return this.isWithinExamReviewPeriod();
        } else {
            return !!(this.course?.complaintsEnabled || this.course?.requestMoreFeedbackEnabled);
        }
    }

    /**
     * Checks whether the student is allowed to submit a complaint or not for exam and course exercises.
     */
    private isTimeOfComplaintValid(): boolean {
        if (!this.isExamMode) {
            if (this.course?.maxComplaintTimeDays) {
                const dueDate = ComplaintService.getIndividualComplaintDueDate(this.exercise, this.course.maxComplaintTimeDays, this.result);
                return !!dueDate && dayjs().isBefore(dueDate);
            }
            return false;
        }
        return this.isWithinExamReviewPeriod();
    }

    /**
     * Checks whether the student is allowed to submit a more feedback request. This is only possible for course exercises.
     */
    private isTimeOfFeedbackRequestValid(): boolean {
        if (!this.isExamMode && this.course?.maxRequestMoreFeedbackTimeDays) {
            const dueDate = ComplaintService.getIndividualComplaintDueDate(this.exercise, this.course.maxRequestMoreFeedbackTimeDays, this.result);
            return !!dueDate && dayjs().isBefore(dueDate);
        }
        return false;
    }

    /**
     * A guard function used to indicate whether complaint submissions are valid.
     * These are only allowed if they are submitted within the student review period.
     */
    private isWithinExamReviewPeriod(): boolean {
        if (this.testRun) {
            return true;
        } else if (this.exam.examStudentReviewStart && this.exam.examStudentReviewEnd) {
            return this.serverDateService.now().isBetween(dayjs(this.exam.examStudentReviewStart), dayjs(this.exam.examStudentReviewEnd));
        }
        return false;
    }
}
