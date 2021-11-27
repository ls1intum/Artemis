import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { forkJoin } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse } from '@angular/common/http';
import { Exam } from 'app/entities/exam.model';
import dayjs from 'dayjs';
import { AccountService } from 'app/core/auth/account.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
    hasStudentsWithoutExam: boolean;

    eventSubscriber: Subscription;
    paramSub: Subscription;
    isLoading: boolean;
    isExamStarted = false;
    isExamOver = false;
    longestWorkingTime?: number;
    isAdmin = false;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private studentExamService: StudentExamService,
        private courseService: CourseManagementService,
        private alertService: AlertService,
        private modalService: NgbModal,
        private accountService: AccountService,
        private artemisTranslatePipe: ArtemisTranslatePipe,
    ) {}

    /**
     * Initialize the courseId and examId
     */
    ngOnInit(): void {
        this.isLoading = true;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        this.loadAll();
    }

    private loadAll() {
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
                    this.isExamStarted = this.exam.startDate ? this.exam.startDate.isBefore(dayjs()) : false;
                    this.calculateIsExamOver();
                }),
            );

            // Calculate hasStudentsWithoutExam only when both observables emitted
            forkJoin(studentExamObservable, examObservable).subscribe(() => {
                this.isLoading = false;
                if (this.exam.registeredUsers) {
                    this.hasStudentsWithoutExam = this.studentExams.length < this.exam.registeredUsers.length;
                }
            });
        });
    }

    /**
     * Evaluates all the quiz exercises that belong to the exam
     */
    evaluateQuizExercises() {
        this.isLoading = true;
        this.examManagementService.evaluateQuizExercises(this.courseId, this.examId).subscribe(
            (res) => {
                this.alertService.success('artemisApp.studentExams.evaluateQuizExerciseSuccess', { number: res?.body });
                this.isLoading = false;
            },
            (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.evaluateQuizExerciseFailure', err);
                this.isLoading = false;
            },
        );
    }

    assessUnsubmittedExamModelingAndTextParticipations() {
        this.isLoading = true;
        this.examManagementService.assessUnsubmittedExamModelingAndTextParticipations(this.courseId, this.examId).subscribe(
            (res) => {
                this.alertService.success('artemisApp.studentExams.assessUnsubmittedStudentExamsSuccess', { number: res?.body });
                this.isLoading = false;
            },
            (err: HttpErrorResponse) => {
                this.handleError('artemisApp.studentExams.assessUnsubmittedStudentExamsFailure', err);
                this.isLoading = false;
            },
        );
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
