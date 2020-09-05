import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { SortService } from 'app/shared/service/sort.service';
import { Exam } from 'app/entities/exam.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { JhiAlertService } from 'ng-jhipster';

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
        private courseManagementService: CourseManagementService,
        private sortService: SortService,
    ) {
        this.predicate = 'id';
        this.ascending = true;
    }

    ngOnInit(): void {
        this.route.data.subscribe(({ exam }) => {
            this.exam = exam;
            this.courseManagementService.find(Number(this.route.snapshot.paramMap.get('courseId'))).subscribe(
                (response: HttpResponse<Course>) => {
                    this.exam.course = response.body!;
                    this.course = response.body!;
                },
                (err: HttpErrorResponse) => this.onError(err),
            );
        });
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
    }

    openTestRunModal() {
        // TODO
    }

    startTestRun(testRun: StudentExam) {
        // TODO
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
