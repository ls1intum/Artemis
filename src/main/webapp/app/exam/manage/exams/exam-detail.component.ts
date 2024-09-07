import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, Subject, map } from 'rxjs';
import { Exam } from 'app/entities/exam/exam.model';
import { ActionType, EntitySummary } from 'app/shared/delete-dialog/delete-dialog.model';
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
import { ExerciseType } from 'app/entities/exercise.model';

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

    private getExistingSummaryEntries(): EntitySummary {
        const numberOfProgrammingExerciseParticipations =
            this.exam.exerciseGroups
                ?.flatMap((exerciseGroup) => exerciseGroup.exercises)
                .filter((exercise) => exercise?.type === ExerciseType.PROGRAMMING)
                .map((exercise) => exercise?.numberOfParticipations ?? 0)
                .reduce((repositorySum, numberOfParticipationsForRepository) => repositorySum + numberOfParticipationsForRepository, 0) ?? 0;

        const numberOfExercisesPerType = new Map<ExerciseType, number>();
        this.exam.exerciseGroups?.forEach((exerciseGroup) => {
            exerciseGroup.exercises?.forEach((exercise) => {
                if (exercise.type === undefined) {
                    return;
                }
                const oldValue = numberOfExercisesPerType.get(exercise.type) ?? 0;
                numberOfExercisesPerType.set(exercise.type, oldValue + 1);
            });
        });

        const numberOfExerciseGroups = this.exam.exerciseGroups?.length ?? 0;
        const isTestExam = this.exam.testExam ?? false;
        const isTestCourse = this.exam.course?.testCourse ?? false;

        return {
            'artemisApp.examManagement.delete.summary.numberExerciseGroups': numberOfExerciseGroups,
            'artemisApp.examManagement.delete.summary.numberProgrammingExercises': numberOfExercisesPerType.get(ExerciseType.PROGRAMMING),
            'artemisApp.examManagement.delete.summary.numberModelingExercises': numberOfExercisesPerType.get(ExerciseType.MODELING),
            'artemisApp.examManagement.delete.summary.numberTextExercises': numberOfExercisesPerType.get(ExerciseType.TEXT),
            'artemisApp.examManagement.delete.summary.numberFileUploadExercises': numberOfExercisesPerType.get(ExerciseType.FILE_UPLOAD),
            'artemisApp.examManagement.delete.summary.numberQuizExercises': numberOfExercisesPerType.get(ExerciseType.QUIZ),
            'artemisApp.examManagement.delete.summary.numberRepositories': numberOfProgrammingExerciseParticipations,
            'artemisApp.examManagement.delete.summary.isTestExam': isTestExam,
            'artemisApp.examManagement.delete.summary.isTestCourse': isTestCourse,
        };
    }

    fetchExamDeletionSummary(): Observable<EntitySummary> {
        return this.examManagementService.getDeletionSummary(this.exam.course!.id!, this.exam.id!).pipe(
            map((response) => {
                const summary = response.body;

                if (summary === null) {
                    return {};
                }

                return {
                    ...this.getExistingSummaryEntries(),
                    'artemisApp.examManagement.delete.summary.numberBuilds': summary.numberOfBuilds,
                    'artemisApp.examManagement.delete.summary.numberRegisteredStudents': summary.numberRegisteredStudents,
                    'artemisApp.examManagement.delete.summary.numberNotStartedExams': summary.numberNotStartedExams,
                    'artemisApp.examManagement.delete.summary.numberStartedExams': summary.numberStartedExams,
                    'artemisApp.examManagement.delete.summary.numberSubmittedExams': summary.numberSubmittedExams,
                    'artemisApp.examManagement.delete.summary.numberCommunicationPosts': summary.numberOfCommunicationPosts,
                    'artemisApp.examManagement.delete.summary.numberAnswerPosts': summary.numberOfAnswerPosts,
                };
            }),
        );
    }
}
