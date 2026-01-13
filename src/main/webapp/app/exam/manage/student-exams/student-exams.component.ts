import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NgbModal, NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { StudentExamWorkingTimeComponent } from 'app/exam/overview/student-exam-working-time/student-exam-working-time.component';
import { TestExamWorkingTimeComponent } from 'app/exam/overview/testExam-workingTime/test-exam-working-time.component';
import { Subscription } from 'rxjs';
import { tap } from 'rxjs/operators';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { StudentExamStatusComponent } from './student-exam-status/student-exam-status.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

const getWebsocketChannel = (examId: number) => `/topic/exams/${examId}/exercise-start-status`;

export type ExamExerciseStartPreparationStatus = {
    finished?: number;
    failed?: number;
    overall?: number;
    participationCount?: number;
    startedAt?: dayjs.Dayjs;
};

@Component({
    selector: 'jhi-student-exams',
    templateUrl: './student-exams.component.html',
    imports: [
        TranslateDirective,
        FaIconComponent,
        StudentExamStatusComponent,
        NgbProgressbar,
        DataTableComponent,
        NgxDatatableModule,
        RouterLink,
        TestExamWorkingTimeComponent,
        StudentExamWorkingTimeComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
    ],
})
export class StudentExamsComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private examManagementService = inject(ExamManagementService);
    private studentExamService = inject(StudentExamService);
    private courseService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private accountService = inject(AccountService);
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);
    private websocketService = inject(WebsocketService);
    private exercisePreparationSubscription?: Subscription;

    courseId: number;
    examId: number;
    studentExams: StudentExam[] = [];
    course: Course;
    exam: Exam;
    hasStudentsWithoutExam: boolean;
    isTestExam: boolean;

    eventSubscriber: Subscription;
    paramSub: Subscription;
    isLoading: boolean;
    filteredStudentExamsSize = 0;
    isExamStarted = false;
    isExamOver = false;
    longestWorkingTime?: number;
    isAdmin = false;

    exercisePreparationStatus?: ExamExerciseStartPreparationStatus;
    exercisePreparationRunning = false;
    exercisePreparationPercentage = 0;
    exercisePreparationEta?: string;

    // Icons
    faExclamationTriangle = faExclamationTriangle;

    /**
     * Initialize the courseId and examId
     */
    ngOnInit(): void {
        this.isLoading = true;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        this.loadAll();

        const channel = getWebsocketChannel(this.examId);
        this.exercisePreparationSubscription = this.websocketService
            .subscribe<ExamExerciseStartPreparationStatus>(channel)
            .pipe(tap((status: ExamExerciseStartPreparationStatus) => (status.startedAt = convertDateFromServer(status.startedAt))))
            .subscribe((status: ExamExerciseStartPreparationStatus) => this.setExercisePreparationStatus(status));
    }

    ngOnDestroy() {
        this.exercisePreparationSubscription?.unsubscribe();
    }

    private loadAll() {
        this.paramSub = this.route.params.subscribe(() => {
            this.isAdmin = this.accountService.isAdmin();
            this.courseService.find(this.courseId).subscribe((courseResponse) => {
                this.course = courseResponse.body!;
            });

            this.examManagementService.find(this.courseId, this.examId, true).subscribe((examResponse) => {
                this.exam = examResponse.body!;
                this.isTestExam = this.exam.testExam!;
                this.isExamStarted = this.exam.startDate ? this.exam.startDate.isBefore(dayjs()) : false;

                this.studentExamService.findAllForExam(this.courseId, this.examId).subscribe((res) => {
                    this.setStudentExams(res.body);
                    this.longestWorkingTime = Math.max.apply(
                        null,
                        this.studentExams.map((studentExam) => studentExam.workingTime),
                    );
                    this.calculateIsExamOver();
                    this.isLoading = false;
                    if (this.exam.examUsers) {
                        this.hasStudentsWithoutExam = this.studentExams.length < this.exam.examUsers.length;
                    }
                });
            });

            this.examManagementService.getExerciseStartStatus(this.courseId, this.examId).subscribe((res) => this.setExercisePreparationStatus(res.body ?? undefined));
        });
    }

    calculateIsExamOver() {
        if (this.longestWorkingTime && this.exam) {
            const startDate = dayjs(this.exam.startDate);
            let endDate = startDate.add(this.longestWorkingTime, 'seconds');
            if (this.exam.gracePeriod) {
                endDate = endDate.add(this.exam.gracePeriod!, 'seconds');
            }
            this.isExamOver = endDate.isBefore(dayjs());
        }
    }

    /**
     * Generate all student exams for the exam on the server and handle the result.
     * Asks for confirmation if some exams already exist.
     */
    handleGenerateStudentExams() {
        // If student exams already exists, inform the instructor about it and get confirmations for re-creation
        if (this.studentExams.length) {
            const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
            modalRef.componentInstance.title = 'artemisApp.studentExams.generateStudentExams';
            modalRef.componentInstance.text = this.artemisTranslatePipe.transform('artemisApp.studentExams.studentExamGenerationModalText');
            modalRef.result.then(() => {
                this.generateStudentExams();
            });
        } else {
            this.generateStudentExams();
        }
    }

    private generateStudentExams() {
        this.isLoading = true;
        this.examManagementService.generateStudentExams(this.courseId, this.examId).subscribe({
            next: (res) => {
                this.alertService.success('artemisApp.studentExams.studentExamGenerationSuccess', { number: res?.body?.length ?? 0 });
                this.loadAll();
            },
            error: (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.studentExamGenerationError', err);
                this.isLoading = false;
            },
        });
    }

    /**
     * Generate missing student exams for the exam on the server and handle the result.
     * Student exams can be missing if a student was added after the initial generation of all student exams.
     */
    generateMissingStudentExams() {
        this.isLoading = true;
        this.examManagementService.generateMissingStudentExams(this.courseId, this.examId).subscribe({
            next: (res) => {
                this.alertService.success('artemisApp.studentExams.missingStudentExamGenerationSuccess', { number: res?.body?.length ?? 0 });
                this.loadAll();
            },
            error: (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.missingStudentExamGenerationError', err);
                this.isLoading = false;
            },
        });
    }

    /**
     * Starts all the exercises of the student exams that belong to the exam
     */
    startExercises() {
        this.isLoading = true;
        this.examManagementService.startExercises(this.courseId, this.examId).subscribe({
            next: () => {
                this.alertService.success('artemisApp.studentExams.startExerciseSuccess');
                this.isLoading = false;
            },
            error: (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.startExerciseFailure', err);
                this.isLoading = false;
            },
        });
    }

    setExercisePreparationStatus(newStatus?: ExamExerciseStartPreparationStatus) {
        this.exercisePreparationStatus = newStatus;
        const processedExams = (newStatus?.finished ?? 0) + (newStatus?.failed ?? 0);
        this.exercisePreparationRunning = !!(newStatus && processedExams < newStatus.overall!);
        this.exercisePreparationPercentage = newStatus ? (newStatus.overall! ? Math.round((processedExams / newStatus.overall!) * 100) : 100) : 0;
        this.exercisePreparationEta = undefined;
        if (this.exercisePreparationRunning && processedExams) {
            const remainingExams = newStatus!.overall! - processedExams;

            const passedSeconds = dayjs().diff(newStatus!.startedAt!, 's');
            const remainingSeconds = (passedSeconds / processedExams) * remainingExams;

            const h = Math.floor(remainingSeconds / 60 / 60);
            const min = Math.floor((remainingSeconds - h * 60 * 60) / 60);
            const s = Math.floor(remainingSeconds - h * 60 * 60 - min * 60);

            this.exercisePreparationEta = (h ? h + 'h' : '') + (min || h ? min + 'm' : '') + (s || min || h ? s + 's' : '');
        }
    }

    /**
     * Update the number of filtered participations
     *
     * @param filteredStudentExamsSize Total number of participations after filters have been applied
     */
    handleStudentExamsSizeChange = (filteredStudentExamsSize: number) => {
        this.filteredStudentExamsSize = filteredStudentExamsSize;
    };

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param studentExam
     */
    searchResultFormatter = (studentExam: StudentExam) => {
        if (studentExam.user) {
            return `${studentExam.user.login} (${studentExam.user.name})`;
        } else {
            return '';
        }
    };

    /**
     * Converts a student exam object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param studentExam Student exam
     */
    searchTextFromStudentExam = (studentExam: StudentExam): string => {
        return studentExam.user?.login || '';
    };

    private setStudentExams(studentExams: StudentExam[] | null): void {
        if (!studentExams) {
            return;
        }
        this.studentExams = studentExams;
        this.studentExams.forEach((studentExam: StudentExam) => {
            studentExam.exam = this.exam;
            studentExam.numberOfExamSessions = studentExam.examSessions?.length ?? 0;
        });
    }

    /**
     * Shows the translated error message if an error key is available in the error response. Otherwise it defaults to the generic alert.
     * @param translationString the string identifier in the translation service for the text. This is ignored if the response does not contain an error message or error key.
     * @param err the error response
     */
    private handleError(translationString: string, err: HttpErrorResponse) {
        let errorDetail;
        if (err?.error && err.error.errorKey) {
            errorDetail = this.artemisTranslatePipe.transform(err.error.errorKey);
        } else {
            errorDetail = err?.error?.message;
        }
        if (errorDetail) {
            this.alertService.error(translationString, { message: errorDetail });
        } else {
            // Sometimes the response does not have an error field, so we default to generic error handling
            onError(this.alertService, err);
        }
    }
}
