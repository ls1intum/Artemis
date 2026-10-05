import { Component, Injector, OnInit, Renderer2, afterNextRender, inject, input, signal } from '@angular/core';
import { Exercise, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { StudentParticipation, isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { AccountService } from 'app/core/auth/account.service';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { filter } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { HttpResponse } from '@angular/common/http';
import { faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ComplaintsFormComponent } from 'app/assessment/overview/complaint-form/complaints-form.component';
import { ComplaintRequestComponent } from 'app/assessment/overview/complaint-request/complaint-request.component';
import { ComplaintResponseComponent } from 'app/assessment/manage/complaint-response/complaint-response.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ComplaintDTO } from 'app/assessment/shared/entities/complaint-dto.model';

@Component({
    selector: 'jhi-complaint-student-view',
    templateUrl: './complaints-student-view.component.html',
    styleUrls: ['../complaints.scss'],
    imports: [TranslateDirective, FaIconComponent, ComplaintsFormComponent, ComplaintRequestComponent, ComplaintResponseComponent, ArtemisTranslatePipe],
})
export class ComplaintsStudentViewComponent implements OnInit {
    private injector = inject(Injector);
    private complaintService = inject(ComplaintService);
    private serverDateService = inject(ArtemisServerDateService);
    private accountService = inject(AccountService);
    private courseService = inject(CourseManagementService);
    private renderer = inject(Renderer2);

    readonly exercise = input.required<Exercise>();
    readonly participation = input.required<StudentParticipation>();
    readonly result = input<Result>();
    readonly exam = input<Exam>();
    readonly isCurrentUserSubmissionAuthor = input<boolean>();
    readonly testRun = input(false);

    submission!: Submission; // set in ngOnInit() from the participation's submissions before loadPotentialComplaint() reads it
    readonly complaint = signal<Complaint | undefined>(undefined);
    readonly course = signal<Course | undefined>(undefined);
    readonly formComplaintType = signal<ComplaintType | undefined>(undefined);
    readonly remainingNumberOfComplaints = signal(0);
    readonly isCorrectUserToFileAction = signal(false);
    readonly isExamMode = signal<boolean>(undefined!);
    readonly showSection = signal(false);
    readonly timeOfFeedbackRequestValid = signal(false);
    readonly timeOfComplaintValid = signal(false);

    ComplaintType = ComplaintType;

    faInfoCircle = faInfoCircle;

    ngOnInit(): void {
        this.course.set(getCourseFromExercise(this.exercise()));
        this.isExamMode.set(this.exam() != undefined);
        const participation = this.participation();
        const result = this.result();
        if (participation && result?.completionDate) {
            if (participation.submissions && participation.submissions.length > 0) {
                this.submission = participation.submissions.sort((a, b) => b.id! - a.id!)[0];
            }
            if (this.course()?.complaintsEnabled) {
                this.courseService.getNumberOfAllowedComplaintsInCourse(this.course()!.id!, this.exercise().teamMode).subscribe((allowedComplaints: number) => {
                    this.remainingNumberOfComplaints.set(allowedComplaints);
                });
            }
            this.loadPotentialComplaint();
            const isCurrentUserSubmissionAuthor = this.isCurrentUserSubmissionAuthor();
            if (isCurrentUserSubmissionAuthor !== undefined) {
                this.isCorrectUserToFileAction.set(isCurrentUserSubmissionAuthor);
            } else {
                void this.accountService.identity().then((user) => {
                    if (user?.id) {
                        const participationValue = this.participation();
                        if (participationValue?.student) {
                            this.isCorrectUserToFileAction.set(participationValue.student.id === user.id);
                        } else if (participationValue.team?.students) {
                            this.isCorrectUserToFileAction.set(!!participationValue.team.students.find((student) => student.id === user.id));
                        }
                    }
                });
            }

            this.timeOfFeedbackRequestValid.set(this.isTimeOfFeedbackRequestValid());
            this.timeOfComplaintValid.set(this.isTimeOfComplaintValid());
            this.showSection.set(this.getSectionVisibility());
        }
    }

    loadPotentialComplaint(): void {
        this.complaintService
            .findBySubmissionId(this.submission.id!)
            .pipe(filter((res) => !!res.body))
            .subscribe((res: HttpResponse<ComplaintDTO>) => {
                this.complaint.set(this.complaintService.convertComplaintFromServer(res.body!, this.result()));
            });
    }

    private getSectionVisibility(): boolean {
        if (!this.isAboutAnAssessment()) {
            return false;
        }
        if (this.isExamMode()) {
            return this.isWithinExamReviewPeriod();
        } else {
            return !!(this.course()?.complaintsEnabled || this.course()?.requestMoreFeedbackEnabled);
        }
    }

    /**
     * Whether there is a tutor assessment to complain about at all.
     *
     * Practice participations are not graded, so there is nothing to review, and the server rejects a complaint on one.
     * Preliminary Athena feedback is a suggestion the student requested rather than an assessment: it carries no
     * assessor, so a complaint about it would go to a tutor who has not looked at the submission yet.
     *
     * `testRun` on the participation means practice mode for a course exercise; the component's own `testRun` input is
     * the unrelated exam test run, which does allow complaints.
     */
    private isAboutAnAssessment(): boolean {
        if (!this.isExamMode() && isPracticeMode(this.participation())) {
            return false;
        }
        return this.result()?.assessmentType !== AssessmentType.AUTOMATIC_ATHENA;
    }

    private isTimeOfComplaintValid(): boolean {
        if (!this.isExamMode()) {
            const course = this.course();
            if (course?.maxComplaintTimeDays) {
                const dueDate = ComplaintService.getIndividualComplaintDueDate(this.exercise(), course.maxComplaintTimeDays, this.result(), this.participation());
                return !!dueDate && dayjs().isBefore(dueDate);
            }
            return false;
        }
        return this.isWithinExamReviewPeriod();
    }

    private isTimeOfFeedbackRequestValid(): boolean {
        const course = this.course();
        if (!this.isExamMode() && course?.maxRequestMoreFeedbackTimeDays) {
            const dueDate = ComplaintService.getIndividualComplaintDueDate(this.exercise(), course.maxRequestMoreFeedbackTimeDays, this.result(), this.participation());
            return !!dueDate && dayjs().isBefore(dueDate);
        }
        return false;
    }

    private isWithinExamReviewPeriod(): boolean {
        if (this.testRun()) {
            return true;
        } else if (this.exam()?.examStudentReviewStart && this.exam()?.examStudentReviewEnd) {
            return this.serverDateService.now().isBetween(dayjs(this.exam()?.examStudentReviewStart), dayjs(this.exam()?.examStudentReviewEnd));
        }
        return false;
    }

    openComplaintForm(complainType: ComplaintType): void {
        this.formComplaintType.set(complainType);
        // Scroll once the complaint form has rendered (signal write schedules CD; afterNextRender runs after that render).
        afterNextRender(() => this.scrollToComplaint(), { injector: this.injector });
    }

    private scrollToComplaint(): void {
        this.renderer.selectRootElement('#complaintScrollpoint', true).scrollIntoView({ behavior: 'smooth', block: 'end' });
    }
}
