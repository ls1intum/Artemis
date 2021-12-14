import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject } from 'rxjs';
import { Exam } from 'app/entities/exam.model';
import { ActionType } from 'app/shared/delete-dialog/delete-dialog.model';
import { ButtonSize } from 'app/shared/components/button.component';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import dayjs from 'dayjs';
import { faClipboard, faEye, faListAlt, faTable, faThList, faUndo, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';

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
    isAtLeastEditor = false;
    isAtLeastInstructor = false;
    isExamOver = true;
    resetType = ActionType.Reset;
    buttonSize = ButtonSize.MEDIUM;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    // Icons
    faUndo = faUndo;
    faEye = faEye;
    faWrench = faWrench;
    faUser = faUser;
    faTable = faTable;
    faListAlt = faListAlt;
    faClipboard = faClipboard;
    faThList = faThList;

    constructor(
        private route: ActivatedRoute,
        private artemisMarkdown: ArtemisMarkdownService,
        private accountService: AccountService,
        private examManagementService: ExamManagementService,
    ) {}

    /**
     * Initialize the exam
     */
    ngOnInit(): void {
        this.route.data.subscribe(({ exam }) => {
            this.exam = exam;
            this.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(this.exam.course);
            this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exam.course);
            this.formattedStartText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.startText);
            this.formattedConfirmationStartText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationStartText);
            this.formattedEndText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.endText);
            this.formattedConfirmationEndText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam.confirmationEndText);
            this.isExamOver = !!this.exam.endDate?.isBefore(dayjs());
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
        this.examManagementService.reset(this.exam.course!.id!, this.exam.id!).subscribe(
            (res: HttpResponse<Exam>) => {
                this.dialogErrorSource.next('');
                this.exam = res.body!;
            },
            (error: HttpErrorResponse) => this.dialogErrorSource.next(error.message),
        );
    }
}
