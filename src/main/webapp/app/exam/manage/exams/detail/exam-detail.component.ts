import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ActionType, EntitySummaryCategory } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { EntityDeletionProgress } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';
import { EntityDeletionService } from 'app/shared-ui/delete-dialog/service/entity-deletion.service';
import { examDeletionTarget, isDeletionCompleted, trackAlive } from 'app/shared-ui/delete-dialog/entity-deletion-target.util';
import { ArtemisMarkdownService } from 'app/foundation/service/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import dayjs from 'dayjs/esm';
import { faAward, faClipboard, faEye, faFlaskVial, faListAlt, faThList, faTrash, faUndo, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/foundation/service/alert.service';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { DetailOverviewListComponent, DetailOverviewSection, DetailType } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { scrollToTopOfPage } from 'app/foundation/util/utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExamDeleteDialogComponent } from 'app/exam/shared/delete-dialog/exam-delete-dialog.component';
import { CourseExamArchiveButtonComponent } from 'app/shared-ui/components/buttons/course-exam-archive-button/course-exam-archive-button.component';
import { ExamChecklistComponent } from '../exam-checklist-component/exam-checklist.component';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exam-detail',
    templateUrl: './exam-detail.component.html',
    imports: [
        ArtemisTranslatePipe,
        TranslateDirective,
        RouterLink,
        FaIconComponent,
        ExamDeleteDialogComponent,
        CourseExamArchiveButtonComponent,
        ExamChecklistComponent,
        DetailOverviewListComponent,
        CourseTitleBarActionsDirective,
        CourseTitleBarTitleDirective,
        TumAetUiButtonDirective,
    ],
    providers: [ArtemisDurationFromSecondsPipe],
})
export class ExamDetailComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private artemisMarkdown = inject(ArtemisMarkdownService);
    private accountService = inject(AccountService);
    private examManagementService = inject(ExamManagementService);
    private router = inject(Router);
    private alertService = inject(AlertService);
    private gradingService = inject(GradingService);
    private artemisDurationFromSecondsPipe = inject(ArtemisDurationFromSecondsPipe);
    private eventManager = inject(EventManager);
    private entityDeletionService = inject(EntityDeletionService);
    private destroyRef = inject(DestroyRef);
    private readonly isAlive = trackAlive(this.destroyRef);

    readonly exam = signal<Exam>(undefined!);
    /** One observable per exam, so that the delete dialog does not get a new input on every change detection pass. */
    readonly examDeletionSummary = computed(() => this.fetchExamDeletionSummary());
    formattedStartText?: SafeHtml;
    formattedConfirmationStartText?: SafeHtml;
    formattedEndText?: SafeHtml;
    formattedConfirmationEndText?: SafeHtml;
    readonly isExamOver = signal(true);
    resetType = ActionType.Reset;
    readonly resetDialogVisible = signal(false);
    readonly deleteDialogVisible = signal(false);
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
    faAward = faAward;
    faFlaskVial = faFlaskVial;

    isAdmin = false;
    readonly canHaveBonus = signal(false);

    readonly examDetailSections = signal<DetailOverviewSection[]>([]);

    /**
     * Initialize the exam
     */
    ngOnInit(): void {
        this.getExamRoutesByIdentifier = this.getExamRoutesByIdentifier.bind(this);
        this.route.data.subscribe(({ exam }) => {
            scrollToTopOfPage();
            this.exam.set(exam);
            this.formattedStartText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam().startText);
            this.formattedConfirmationStartText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam().confirmationStartText);
            this.formattedEndText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam().endText);
            this.formattedConfirmationEndText = this.artemisMarkdown.safeHtmlForMarkdown(this.exam().confirmationEndText);
            this.isExamOver.set(!!this.exam().endDate?.isBefore(dayjs()));
            this.isAdmin = this.accountService.isAdmin();
            this.getExamDetailSections();
            if (this.exam().course?.isAtLeastInstructor) {
                this.resumeRunningDeletion(this.exam());
            }

            this.gradingService.findGradingScaleForExam(this.exam().course!.id!, this.exam().id!).subscribe((gradingSystemResponse) => {
                if (gradingSystemResponse.body) {
                    this.canHaveBonus.set(gradingSystemResponse.body.gradeSteps.gradeType === GradeType.GRADE);
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
        const exam = this.exam();
        this.examDetailSections.set([
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
                    { type: DetailType.Date, title: 'artemisApp.exam.examSummaryPublicationDate', data: { date: exam.examSummaryPublicationDate } },
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
        ]);
    }

    /**
     * Returns the route for exam components by identifier
     */
    getExamRoutesByIdentifier(identifier: string): (string | number | undefined)[] {
        return ['/course-management', this.exam().course?.id, 'exams', this.exam().id, identifier];
    }

    /**
     * Reset an exam with examId by deleting all studentExams and participations and unregistering all students
     */
    resetExam(): void {
        this.examManagementService.reset(this.exam().course!.id!, this.exam().id!).subscribe({
            next: (res: HttpResponse<Exam>) => {
                this.dialogErrorSource.next('');
                this.exam.set(res.body!);
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
        const exam = this.exam();
        const courseId = exam.course!.id!;
        this.entityDeletionService.delete(examDeletionTarget(courseId, examId, exam.title), this.examManagementService.delete(courseId, examId)).subscribe({
            next: (progress) => {
                this.onDeletionEnded(progress, courseId, examId);
                // A failed deletion is reported by the progress dialog; this only ends the confirmation dialog.
                // The subject is closed with the component, and the deletion can end after the user left the page.
                if (this.isAlive()) {
                    this.dialogErrorSource.next('');
                }
            },
            error: (error: HttpErrorResponse) => {
                // The request can fail after the user left the page, when the subject is already closed.
                if (this.isAlive()) {
                    this.dialogErrorSource.next(error.message);
                }
            },
        });
    }

    /**
     * Shows the progress of an exam deletion that is already running, e.g. after the page was reloaded or another instructor started it.
     */
    private resumeRunningDeletion(exam: Exam): void {
        const courseId = exam.course!.id!;
        this.entityDeletionService
            .resume(examDeletionTarget(courseId, exam.id!, exam.title), this.destroyRef)
            .subscribe((progress) => this.onDeletionEnded(progress, courseId, exam.id));
    }

    private onDeletionEnded(progress: EntityDeletionProgress, courseId: number, deletedExamId: number | undefined): void {
        if (isDeletionCompleted(progress)) {
            this.eventManager.broadcast({ name: 'examListModification', content: 'dummy' });
            // Only leave the page while it still shows the deleted exam.
            if (this.isAlive() && this.exam().id === deletedExamId) {
                void this.router.navigate(['/course-management', courseId, 'exams']);
            }
        }
    }

    private fetchExamDeletionSummary(): Observable<EntitySummaryCategory[]> {
        return this.examManagementService.getDeletionSummary(this.exam().course!.id!, this.exam().id!);
    }
}
