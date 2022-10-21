import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { forkJoin, Subscription } from 'rxjs';
import { tap } from 'rxjs/operators';
import { StudentExam } from 'app/entities/student-exam.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { faClipboard } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exam-assessment-buttons',
    templateUrl: './exam-assessment-buttons.component.html',
})
export class ExamAssessmentButtonsComponent implements OnInit {
    courseId: number;
    examId: number;
    studentExams: StudentExam[];
    course: Course;
    exam: Exam;

    paramSub: Subscription;
    isLoading: boolean;
    isEvaluatingQuizExercises: boolean;
    isAssessingUnsubmittedExams: boolean;
    isExamOver = false;
    longestWorkingTime?: number;
    isAdmin = false;

    // icons
    faClipboard = faClipboard;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private studentExamService: StudentExamService,
        private courseService: CourseManagementService,
        private alertService: AlertService,
        private accountService: AccountService,
        private artemisTranslatePipe: ArtemisTranslatePipe,
    ) {}

    /**
     * Initialize the courseId and examId
     */
    ngOnInit(): void {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        this.loadAll();
    }

    private loadAll() {
        this.isLoading = true;
        this.paramSub = this.route.params.subscribe(() => {
            this.isAdmin = this.accountService.isAdmin();
            this.courseService.find(this.courseId).subscribe((courseResponse) => {
                this.course = courseResponse.body!;
            });
            const studentExamObservable = this.studentExamService.findAllForExam(this.courseId, this.examId).pipe(
                tap((res) => {
                    this.setStudentExams(res.body);
                    this.longestWorkingTime = Math.max.apply(
                        null,
                        this.studentExams.map((studentExam) => studentExam.workingTime),
                    );
                    this.calculateIsExamOver();
                }),
            );

            const examObservable = this.examManagementService.find(this.courseId, this.examId, true).pipe(
                tap((examResponse) => {
                    this.exam = examResponse.body!;
                    this.calculateIsExamOver();
                }),
            );

            // Calculate hasStudentsWithoutExam only when both observables emitted
            forkJoin([studentExamObservable, examObservable]).subscribe(() => {
                this.isLoading = false;
            });
        });
    }

    /**
     * Evaluates all the quiz exercises that belong to the exam
     */
    evaluateQuizExercises() {
        this.isEvaluatingQuizExercises = true;
        this.examManagementService.evaluateQuizExercises(this.courseId, this.examId).subscribe({
            next: (res) => {
                this.alertService.success('artemisApp.studentExams.evaluateQuizExerciseSuccess', { number: res?.body });
                this.isEvaluatingQuizExercises = false;
            },
            error: (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.evaluateQuizExerciseFailure', err);
                this.isEvaluatingQuizExercises = false;
            },
        });
    }

    assessUnsubmittedExamModelingAndTextParticipations() {
        this.isAssessingUnsubmittedExams = true;
        this.examManagementService.assessUnsubmittedExamModelingAndTextParticipations(this.courseId, this.examId).subscribe({
            next: (res) => {
                this.alertService.success('artemisApp.studentExams.assessUnsubmittedStudentExamsSuccess', { number: res?.body });
                this.isAssessingUnsubmittedExams = false;
            },
            error: (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.assessUnsubmittedStudentExamsFailure', err);
                this.isAssessingUnsubmittedExams = false;
            },
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

    private setStudentExams(studentExams: any): void {
        if (studentExams) {
            this.studentExams = studentExams;
        }
    }
}
