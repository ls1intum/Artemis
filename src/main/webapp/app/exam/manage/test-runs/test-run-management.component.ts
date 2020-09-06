import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { SortService } from 'app/shared/service/sort.service';
import { Exam } from 'app/entities/exam.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { CreateTestRunModal } from 'app/exam/manage/test-runs/create-test-run-modal.component';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-test-run-management',
    templateUrl: './test-run-management.component.html',
})
export class TestRunManagementComponent implements OnInit {
    course: Course;
    exam: Exam;
    isLoading: boolean;
    isExamStarted: boolean;
    testRuns: [StudentExam];
    predicate: string;
    ascending: boolean;

    constructor(
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        private examManagementService: ExamManagementService,
        private accountService: AccountService,
        private sortService: SortService,
        private modalService: NgbModal,
    ) {
        this.predicate = 'id';
        this.ascending = true;
    }

    ngOnInit(): void {
        this.examManagementService.find(Number(this.route.snapshot.paramMap.get('courseId')), Number(this.route.snapshot.paramMap.get('examId')), false, true).subscribe(
            (response: HttpResponse<Exam>) => {
                this.exam = response.body!;
                this.isExamStarted = this.exam.started;
                this.course = this.exam.course;
                this.course.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course);
                // TODO delete, replace with call to get all studentExams with testRun flag
                let testRun1 = new StudentExam();
                let testRun2 = new StudentExam();
                testRun1.id = 1;
                testRun1.started = true;
                testRun1.workingTime = 4000;
                testRun1.submitted = true;
                testRun1.exam = this.exam;
                this.testRuns = [testRun1];
                testRun2.id = 2;
                testRun2.started = false;
                testRun2.workingTime = 6000;
                testRun2.exam = this.exam;
                this.testRuns.push(testRun2);
            },
            (error) => this.onError(error),
        );
    }

    /**
     * Open modal to configure a new test run
     */
    openCreateTestRunModal() {
        const modalRef: NgbModalRef = this.modalService.open(CreateTestRunModal as Component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.exam = this.exam;
        modalRef.result.then((testRun: StudentExam) => {
            !!this.testRuns ? this.testRuns.push(testRun) : (this.testRuns = [testRun]);
            console.log('Test run created');
            // TODO: Create the configured student exam => make the server call
        });
    }

    startTestRun(testRun: StudentExam) {
        // TODO: Launch conduction
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
            for (let testRun of this.testRuns) {
                if (testRun.submitted) {
                    return true;
                }
            }
        }
        return false;
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
    }
}
