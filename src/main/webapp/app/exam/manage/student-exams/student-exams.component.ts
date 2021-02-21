import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { forkJoin } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Subscription } from 'rxjs/Subscription';
import { StudentExam } from 'app/entities/student-exam.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { JhiAlertService } from 'ng-jhipster';
import { HttpErrorResponse } from '@angular/common/http';
import { Exam } from 'app/entities/exam.model';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-button.component';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { Moment } from 'moment';

@Component({
    selector: 'jhi-student-exams',
    templateUrl: './student-exams.component.html',
})
export class StudentExamsComponent implements OnInit {
    courseId: number;
    examId: number;
    studentExams: StudentExam[];
    course: Course;
    exam: Exam;
    hasStudentsWithoutExam: boolean;

    eventSubscriber: Subscription;
    paramSub: Subscription;
    isLoading: boolean;
    filteredStudentExamsSize = 0;
    isExamStarted = false;
    isExamOver = false;
    longestWorkingTime: number;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private studentExamService: StudentExamService,
        private courseService: CourseManagementService,
        private jhiAlertService: JhiAlertService,
        private modalService: NgbModal,
        private translateService: TranslateService,
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
                    this.isExamStarted = this.exam.startDate ? this.exam.startDate.isBefore(moment()) : false;
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

    calculateIsExamOver() {
        if (this.longestWorkingTime && this.exam) {
            const examEndDate = moment(this.exam.startDate);
            examEndDate.add(this.longestWorkingTime, 'seconds');
            examEndDate.add(this.exam.gracePeriod, 'seconds');
            this.isExamOver = examEndDate.isBefore(moment());
        }
    }

    /**
     * Generate all student exams for the exam on the server and handle the result.
     * Asks for confirmation if some exams already exist.
     */
    handleGenerateStudentExams() {
        // If student exams already exists, inform the instructor about it and get confirmations for re-creation
        if (this.studentExams && this.studentExams.length) {
            const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
            modalRef.componentInstance.title = 'artemisApp.studentExams.generateStudentExams';
            modalRef.componentInstance.text = this.translateService.instant('artemisApp.studentExams.studentExamGenerationModalText');
            modalRef.result.then(() => {
                this.generateStudentExams();
            });
        } else {
            this.generateStudentExams();
        }
    }

    private generateStudentExams() {
        this.isLoading = true;
        this.examManagementService.generateStudentExams(this.courseId, this.examId).subscribe(
            (res) => {
                this.jhiAlertService.addAlert(
                    {
                        type: 'success',
                        msg: 'artemisApp.studentExams.studentExamGenerationSuccess',
                        params: { number: res?.body?.length },
                        timeout: 10000,
                    },
                    [],
                );
                this.loadAll();
            },
            (err: HttpErrorResponse) => {
                this.onError(err.error);
                this.isLoading = false;
            },
        );
    }

    /**
     * Generate missing student exams for the exam on the server and handle the result.
     * Student exams can be missing if a student was added after the initial generation of all student exams.
     */
    generateMissingStudentExams() {
        this.isLoading = true;
        this.examManagementService.generateMissingStudentExams(this.courseId, this.examId).subscribe(
            (res) => {
                this.jhiAlertService.addAlert(
                    {
                        type: 'success',
                        msg: 'artemisApp.studentExams.missingStudentExamGenerationSuccess',
                        params: { number: res?.body?.length },
                        timeout: 10000,
                    },
                    [],
                );
                this.loadAll();
            },
            (err: HttpErrorResponse) => {
                this.onError(err.error);
                this.isLoading = false;
            },
        );
    }

    /**
     * Starts all the exercises of the student exams that belong to the exam
     */
    startExercises() {
        this.isLoading = true;
        this.examManagementService.startExercises(this.courseId, this.examId).subscribe(
            (res) => {
                this.jhiAlertService.addAlert(
                    {
                        type: 'success',
                        msg: 'artemisApp.studentExams.startExerciseSuccess',
                        params: { number: res?.body },
                        timeout: 10000,
                    },
                    [],
                );
                this.loadAll();
            },
            (err: HttpErrorResponse) => {
                this.onError(err.error);
                this.isLoading = false;
            },
        );
    }

    /**
     * Evaluates all the quiz exercises that belong to the exam
     */
    evaluateQuizExercises() {
        this.isLoading = true;
        this.examManagementService.evaluateQuizExercises(this.courseId, this.examId).subscribe(
            (res) => {
                this.jhiAlertService.addAlert(
                    {
                        type: 'success',
                        msg: 'artemisApp.studentExams.evaluateQuizExerciseSuccess',
                        params: { number: res?.body },
                        timeout: 10000,
                    },
                    [],
                );
                this.isLoading = false;
            },
            (err: HttpErrorResponse) => {
                this.isLoading = false;
                this.onError(err.error);
            },
        );
    }

    assessUnsubmittedExamModelingAndTextParticipations() {
        this.isLoading = true;
        this.examManagementService.assessUnsubmittedExamModelingAndTextParticipations(this.courseId, this.examId).subscribe(
            (res) => {
                this.jhiAlertService.addAlert(
                    {
                        type: 'success',
                        msg: 'artemisApp.studentExams.assessUnsubmittedStudentExamsSuccess',
                        params: { number: res?.body },
                        timeout: 10000,
                    },
                    [],
                );
                this.isLoading = false;
            },
            () => {
                this.isLoading = false;
                this.jhiAlertService.error('artemisApp.studentExams.assessUnsubmittedStudentExamsFailure');
            },
        );
    }

    /**
     * Unlock all repositories immediately. Asks for confirmation.
     */
    handleUnlockAllRepositories() {
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.title = 'artemisApp.studentExams.unlockAllRepositories';
        modalRef.componentInstance.text = this.translateService.instant('artemisApp.studentExams.unlockAllRepositoriesModalText');
        modalRef.result.then(() => {
            this.unlockAllRepositories();
        });
    }

    /**
     * Unlocks all programming exercises that belong to the exam
     */
    private unlockAllRepositories() {
        this.isLoading = true;
        this.examManagementService.unlockAllRepositories(this.courseId, this.examId).subscribe(
            (res) => {
                this.jhiAlertService.addAlert(
                    {
                        type: 'success',
                        msg: 'artemisApp.studentExams.unlockAllRepositoriesSuccess',
                        params: { number: res?.body },
                        timeout: 10000,
                    },
                    [],
                );
                this.isLoading = false;
            },
            (err: HttpErrorResponse) => {
                this.onError(err.error);
                this.isLoading = false;
            },
        );
    }

    /**
     * Lock all repositories immediately. Asks for confirmation.
     */
    handleLockAllRepositories() {
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
        modalRef.componentInstance.title = 'artemisApp.studentExams.lockAllRepositories';
        modalRef.componentInstance.text = this.translateService.instant('artemisApp.studentExams.lockAllRepositoriesModalText');
        modalRef.result.then(() => {
            this.lockAllRepositories();
        });
    }

    /**
     * Locks all programming exercises that belong to the exam
     */
    private lockAllRepositories() {
        this.isLoading = true;
        this.examManagementService.lockAllRepositories(this.courseId, this.examId).subscribe(
            (res) => {
                this.jhiAlertService.addAlert(
                    {
                        type: 'success',
                        msg: 'artemisApp.studentExams.lockAllRepositoriesSuccess',
                        params: { number: res?.body },
                        timeout: 10000,
                    },
                    [],
                );
                this.isLoading = false;
            },
            (err: HttpErrorResponse) => {
                this.onError(err.error);
                this.isLoading = false;
            },
        );
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

    private setStudentExams(studentExams: any): void {
        if (studentExams) {
            this.studentExams = studentExams;
        }
    }

    private onError(error: any) {
        this.jhiAlertService.error(error.errorKey);
    }

    formatDate(date: Moment | Date | undefined) {
        // TODO: we should try to use the artemis date pipe here
        return date ? moment(date).format('long') : '';
    }
}
