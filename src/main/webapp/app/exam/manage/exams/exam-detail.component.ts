import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { Exam } from 'app/entities/exam.model';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { ButtonSize } from 'app/shared/components/button.component';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import dayjs from 'dayjs/esm';
import { faAward, faClipboard, faEye, faHeartBroken, faListAlt, faTable, faThList, faTimes, faUndo, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/core/util/alert.service';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeType } from 'app/entities/grading-scale.model';

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
    faTimes = faTimes;
    faUndo = faUndo;
    faEye = faEye;
    faWrench = faWrench;
    faUser = faUser;
    faTable = faTable;
    faListAlt = faListAlt;
    faClipboard = faClipboard;
    faThList = faThList;
    faHeartBroken = faHeartBroken;
    faAward = faAward;

    isAdmin = false;
    canHaveBonus = false;

    constructor(
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
        private accountService: AccountService,
        private examManagementService: ExamManagementService,
        private router: Router,
        private alertService: AlertService,
        private gradingSystemService: GradingSystemService,
    ) {}

    /**
     * Initialize the exam
     */
    ngOnInit(): void {
        this.route.data.subscribe(({ exam }) => {
            this.exam = exam;
            this.formattedStartText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.startText);
            this.formattedConfirmationStartText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationStartText);
            this.formattedEndText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.endText);
            this.formattedConfirmationEndText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationEndText);
            this.isExamOver = !!this.exam.endDate?.isBefore(dayjs());
            this.isAdmin = this.accountService.isAdmin();

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
