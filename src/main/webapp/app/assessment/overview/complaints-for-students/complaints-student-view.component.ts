import { ChangeDetectorRef, Component, OnInit, Renderer2, inject, input } from '@angular/core';
import { Exercise, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { AccountService } from 'app/core/auth/account.service';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { filter } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { HttpResponse } from '@angular/common/http';
import { faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ComplaintsFormComponent } from 'app/assessment/overview/complaint-form/complaints-form.component';
import { ComplaintRequestComponent } from 'app/assessment/overview/complaint-request/complaint-request.component';
import { ComplaintResponseComponent } from 'app/assessment/manage/complaint-response/complaint-response.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-complaint-student-view',
    templateUrl: './complaints-student-view.component.html',
    styleUrls: ['../complaints.scss'],
    imports: [TranslateDirective, FaIconComponent, ComplaintsFormComponent, ComplaintRequestComponent, ComplaintResponseComponent, ArtemisTranslatePipe],
})
export class ComplaintsStudentViewComponent implements OnInit {
    private cdr = inject(ChangeDetectorRef);
    private complaintService = inject(ComplaintService);
    private serverDateService = inject(ArtemisServerDateService);
    private accountService = inject(AccountService);
    private courseService = inject(CourseManagementService);
    private renderer = inject(Renderer2);

    readonly exercise = input.required<Exercise>();
    readonly participation = input.required<StudentParticipation>();
    readonly result = input<Result>();
    readonly exam = input<Exam>();
    // flag to indicate exam test run. Default set to false.
    readonly testRun = input(false);

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

    ngOnInit(): void {
        this.course = getCourseFromExercise(this.exercise());
        this.isExamMode = this.exam() != undefined;
        const participation = this.participation();
        const result = this.result();
        if (participation && result?.completionDate) {
            if (participation.submissions && participation.submissions.length > 0) {
                this.submission = participation.submissions.sort((a, b) => b.id! - a.id!)[0];
            }
            // for course exercises we track the number of allowed complaints
            if (this.course?.complaintsEnabled) {
                this.courseService.getNumberOfAllowedComplaintsInCourse(this.course!.id!, this.exercise().teamMode).subscribe((allowedComplaints: number) => {
                    this.remainingNumberOfComplaints = allowedComplaints;
                });
            }
            this.loadPotentialComplaint();
            this.accountService.identity().then((user) => {
                if (user?.id) {
                    const participationValue = this.participation();
                    if (participationValue?.student) {
                        this.isCorrectUserToFileAction = participationValue.student.id === user.id;
                    } else if (participationValue.team?.students) {
                        this.isCorrectUserToFileAction = !!participationValue.team.students.find((student) => student.id === user.id);
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
                const dueDate = ComplaintService.getIndividualComplaintDueDate(this.exercise(), this.course.maxComplaintTimeDays, this.result(), this.participation());
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
            const dueDate = ComplaintService.getIndividualComplaintDueDate(this.exercise(), this.course.maxRequestMoreFeedbackTimeDays, this.result(), this.participation());
            return !!dueDate && dayjs().isBefore(dueDate);
        }
        return false;
    }

    /**
     * A guard function used to indicate whether complaint submissions are valid.
     * These are only allowed if they are submitted within the student review period.
     */
    private isWithinExamReviewPeriod(): boolean {
        if (this.testRun()) {
            return true;
        } else if (this.exam()?.examStudentReviewStart && this.exam()?.examStudentReviewEnd) {
            return this.serverDateService.now().isBetween(dayjs(this.exam()?.examStudentReviewStart), dayjs(this.exam()?.examStudentReviewEnd));
        }
        return false;
    }

    /**
     * Function to set complaint type (which opens the complaint form) and scrolls to the complaint form
     */
    openComplaintForm(complainType: ComplaintType): void {
        this.formComplaintType = complainType;
        this.cdr.detectChanges(); // Wait for the view to update
        this.scrollToComplaint();
    }

    /**
     * Function to scroll to the complaint form
     */
    private scrollToComplaint(): void {
        this.renderer.selectRootElement('#complaintScrollpoint', true).scrollIntoView({ behavior: 'smooth', block: 'end' });
    }
}
