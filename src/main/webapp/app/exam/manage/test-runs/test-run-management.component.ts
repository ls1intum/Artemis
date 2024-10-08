import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { SortService } from 'app/shared/service/sort.service';
import { Exam } from 'app/entities/exam/exam.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { CreateTestRunModalComponent } from 'app/exam/manage/test-runs/create-test-run-modal.component';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { Subject } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { onError } from 'app/shared/util/global.utils';
import { faSort, faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-test-run-management',
    templateUrl: './test-run-management.component.html',
})
export class TestRunManagementComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private examManagementService = inject(ExamManagementService);
    private accountService = inject(AccountService);
    private sortService = inject(SortService);
    private modalService = inject(NgbModal);

    course: Course;
    exam: Exam;
    isLoading: boolean;
    isExamStarted: boolean;
    testRuns: StudentExam[] = [];
    instructor?: User;
    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();
    predicate: string;
    ascending: boolean;

    // Icons
    faSort = faSort;
    faTimes = faTimes;

    constructor() {
        this.predicate = 'id';
        this.ascending = true;
    }

    ngOnInit(): void {
        this.examManagementService.find(Number(this.route.snapshot.paramMap.get('courseId')), Number(this.route.snapshot.paramMap.get('examId')), false, true).subscribe({
            next: (response: HttpResponse<Exam>) => {
                this.exam = response.body!;
                this.isExamStarted = this.exam.started!;
                this.course = this.exam.course!;
                this.course.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course);
                this.examManagementService.findAllTestRunsForExam(this.course.id!, this.exam.id!).subscribe({
                    next: (res: HttpResponse<StudentExam[]>) => {
                        this.testRuns = res.body!;
                    },
                    error: (error: HttpErrorResponse) => onError(this.alertService, error),
                });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
        this.accountService.identity().then((user) => {
            if (user) {
                this.instructor = user;
            }
        });
    }

    /**
     * Open modal to configure a new test run
     */
    openCreateTestRunModal() {
        const modalRef: NgbModalRef = this.modalService.open(CreateTestRunModalComponent as Component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exam = this.exam;
        modalRef.result
            .then((testRunConfiguration: StudentExam) => {
                this.examManagementService.createTestRun(this.course.id!, this.exam.id!, testRunConfiguration).subscribe({
                    next: (response: HttpResponse<StudentExam>) => {
                        if (response.body != undefined) {
                            this.testRuns.push(response.body!);
                        }
                    },
                    error: (error: HttpErrorResponse) => {
                        onError(this.alertService, error);
                    },
                });
            })
            .catch(() => {});
    }

    /**
     * Delete the test run with the given id.
     * @param testRunId {number}
     */
    deleteTestRun(testRunId: number) {
        this.examManagementService.deleteTestRun(this.course.id!, this.exam.id!, testRunId).subscribe({
            next: () => {
                this.testRuns = this.testRuns!.filter((testRun) => testRun.id !== testRunId);
                this.dialogErrorSource.next('');
            },
            error: (error) => this.dialogErrorSource.next(error.message),
        });
    }

    /**
     * Track the items on the testruns Table
     * @param index {number}
     * @param item {StudentExam}
     */
    trackId(index: number, item: StudentExam) {
        return item.id;
    }

    sortRows() {
        this.sortService.sortByProperty(this.testRuns, this.predicate, this.ascending);
    }

    /**
     * Get function to determine if a test run has been submitted.
     * Used to enable the assess test run button.
     */
    get testRunCanBeAssessed(): boolean {
        if (!!this.testRuns && this.testRuns.length > 0) {
            for (const testRun of this.testRuns) {
                if (testRun.user?.id === this.instructor?.id && testRun.submitted) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get function to determine if at least one exercise has been configured for the exam
     */
    get examContainsExercises(): boolean {
        return !!this.exam?.exerciseGroups && this.exam.exerciseGroups.some((exerciseGroup) => exerciseGroup.exercises && exerciseGroup.exercises.length > 0);
    }
}
