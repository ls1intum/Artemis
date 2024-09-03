import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { Exam } from 'app/entities/exam/exam.model';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { ButtonSize } from 'app/shared/components/button.component';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import dayjs from 'dayjs/esm';
import { faAward, faClipboard, faEye, faFlaskVial, faHeartBroken, faListAlt, faThList, faTrash, faUndo, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/core/util/alert.service';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeType } from 'app/entities/grading-scale.model';
import { DetailOverviewSection, DetailType } from 'app/detail-overview-list/detail-overview-list.component';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { scrollToTopOfPage } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-exam-detail',
    templateUrl: './exam-detail.component.html',
})
export class ExamDetailComponent implements OnInit, OnDestroy {
    exam: Exam;
    formattedStartText?: SafeHtml;
    formattedConfirmationStartText?: SafeHtml;
    formattedEndText?: SafeHtml;
    formattedConfirmationEndText?: SafeHtml;
    isExamOver = true;
    resetType = ActionType.Reset;
    buttonSize = ButtonSize.MEDIUM;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faTrash = faTrash;
    faUndo = faUndo;
    faEye = faEye;
    faWrench = faWrench;
    faUser = faUser;
    faListAlt = faListAlt;
    faClipboard = faClipboard;
    faThList = faThList;
    faHeartBroken = faHeartBroken;
    faAward = faAward;
    faFlaskVial = faFlaskVial;

    isAdmin = false;
    canHaveBonus = false;

    examDetailSections: DetailOverviewSection[];

    constructor(
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
        private accountService: AccountService,
        private examManagementService: ExamManagementService,
        private router: Router,
        private alertService: AlertService,
        private gradingSystemService: GradingSystemService,
        private artemisDurationFromSecondsPipe: ArtemisDurationFromSecondsPipe,
    ) {}

    /**
     * Initialize the exam
     */
    ngOnInit(): void {
        this.route.data.subscribe(({ exam }) => {
            scrollToTopOfPage();
            this.exam = exam;
            this.formattedStartText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.startText);
            this.formattedConfirmationStartText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationStartText);
            this.formattedEndText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.endText);
            this.formattedConfirmationEndText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationEndText);
            this.isExamOver = !!this.exam.endDate?.isBefore(dayjs());
            this.isAdmin = this.accountService.isAdmin();
            this.getExamDetailSections();

            this.gradingSystemService.findGradingScaleForExam(this.exam.course!.id!, this.exam.id!).subscribe((gradingSystemResponse) => {
                if (gradingSystemResponse.body) {
                    this.canHaveBonus = gradingSystemResponse.body.gradeType === GradeType.GRADE;
                }
            });
        });
    }

    /**
     * unsubscribe on component destruction
     */
    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    getExamDetailSections() {
        const exam = this.exam;
        this.examDetailSections = [
            {
                headline: 'artemisApp.exam.detail.sections.general',
                details: [
                    { type: DetailType.Link, title: 'artemisApp.exam.course', data: { text: exam.course?.title, routerLink: ['/course-management', exam.course?.id] } },
                    { type: DetailType.Text, title: 'artemisApp.exam.title', data: { text: exam.title } },
                    { type: DetailType.Text, title: 'artemisApp.examManagement.examiner', data: { text: exam.examiner } },
                    { type: DetailType.Text, title: 'artemisApp.examManagement.moduleNumber', data: { text: exam.moduleNumber } },
                    { type: DetailType.Date, title: 'artemisApp.examManagement.visibleDate', data: { date: exam.visibleDate } },
                    { type: DetailType.Date, title: 'artemisApp.exam.startDate', data: { date: exam.startDate } },
                    { type: DetailType.Date, title: 'artemisApp.exam.endDate', data: { date: exam.endDate } },
                    { type: DetailType.Date, title: 'artemisApp.exam.publishResultsDate', data: { date: exam.publishResultsDate } },
                    { type: DetailType.Date, title: 'artemisApp.exam.examStudentReviewStart', data: { date: exam.examStudentReviewStart } },
                    { type: DetailType.Date, title: 'artemisApp.exam.examStudentReviewEnd', data: { date: exam.examStudentReviewEnd } },
                    { type: DetailType.Date, title: 'artemisApp.exam.exampleSolutionPublicationDate', data: { date: exam.exampleSolutionPublicationDate } },
                    { type: DetailType.Text, title: 'artemisApp.exam.workingTime', data: { text: this.artemisDurationFromSecondsPipe.transform(exam.workingTime!, true) } },
                    { type: DetailType.Text, title: 'artemisApp.examManagement.gracePeriod', data: { text: exam.gracePeriod } },
                    { type: DetailType.Text, title: 'artemisApp.examManagement.maxPoints.title', data: { text: exam.examMaxPoints } },
                    { type: DetailType.Text, title: 'artemisApp.examManagement.numberOfExercisesInExam', data: { text: exam.numberOfExercisesInExam } },
                    { type: DetailType.Text, title: 'artemisApp.examManagement.numberOfCorrectionRoundsInExam', data: { text: exam.numberOfCorrectionRoundsInExam } },
                    { type: DetailType.Boolean, title: 'artemisApp.examManagement.randomizeQuestionOrder', data: { boolean: exam.randomizeExerciseOrder } },
                    { type: DetailType.Text, title: 'artemisApp.examManagement.examStudents.registeredStudents', data: { text: exam.numberOfExamUsers ?? 0 } },
                    { type: DetailType.Markdown, title: 'artemisApp.examManagement.startText', data: { innerHtml: this.formattedStartText } },
                    { type: DetailType.Markdown, title: 'artemisApp.examManagement.confirmationStartText', data: { innerHtml: this.formattedConfirmationStartText } },
                    { type: DetailType.Markdown, title: 'artemisApp.examManagement.endText', data: { innerHtml: this.formattedEndText } },
                    { type: DetailType.Markdown, title: 'artemisApp.examManagement.confirmationEndText', data: { innerHtml: this.formattedConfirmationEndText } },
                ],
            },
        ];
    }

    /**
     * Returns the route for exam components by identifier
     */
    getExamRoutesByIdentifier(identifier: string) {
        return ['/course-management', this.exam.course?.id, 'exams', this.exam.id, identifier];
    }

    /**
     * Reset an exam with examId by deleting all studentExams and participations
     */
    resetExam(): void {
        this.examManagementService.reset(this.exam.course!.id!, this.exam.id!).subscribe({
            next: (res: HttpResponse<Exam>) => {
                this.dialogErrorSource.next('');
                this.exam = res.body!;
                this.alertService.success('artemisApp.examManagement.reset.success');
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Function is called when the delete button is pressed for an exam
     * @param examId Id to be deleted
     */
    deleteExam(examId: number): void {
        this.examManagementService.delete(this.exam.course!.id!, examId).subscribe({
            next: () => {
                this.dialogErrorSource.next('');
                this.router.navigate(['/course-management', this.exam.course!.id!, 'exams']);
            },
            error: (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        });
    }
}
