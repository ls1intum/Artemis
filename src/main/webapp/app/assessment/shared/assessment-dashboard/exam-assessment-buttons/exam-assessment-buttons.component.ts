import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { Subscription, forkJoin } from 'rxjs';
import { tap } from 'rxjs/operators';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { faClipboard } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-exam-assessment-buttons',
    templateUrl: './exam-assessment-buttons.component.html',
    imports: [RouterLink, FaIconComponent, TranslateDirective],
})
export class ExamAssessmentButtonsComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private examManagementService = inject(ExamManagementService);
    private studentExamService = inject(StudentExamService);
    private courseService = inject(CourseManagementService);
    private alertService = inject(AlertService);
    private accountService = inject(AccountService);
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);

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

            /*
            Prepare workingTimeObservable to perform the following on subscribe:
            - set the longestWorkingTime
            - trigger (re)calculation of whether the exam is over
             */
            const workingTimeObservable = this.studentExamService.getLongestWorkingTimeForExam(this.courseId, this.examId).pipe(
                tap((value) => {
                    this.longestWorkingTime = value;
                    this.calculateIsExamOver();
                }),
            );

            /*
            Prepare examObservable to perform the following on subscribe:
            - set the exam
            - trigger (re)calculation of whether the exam is over
             */
            const examObservable = this.examManagementService.find(this.courseId, this.examId, true).pipe(
                tap((examResponse) => {
                    this.exam = examResponse.body!;
                    this.calculateIsExamOver();
                }),
            );

            // Calculate hasStudentsWithoutExam only when both observables emitted
            forkJoin([workingTimeObservable, examObservable]).subscribe(() => {
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
}
