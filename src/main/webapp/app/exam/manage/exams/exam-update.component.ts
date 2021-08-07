import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Observable } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import * as moment from 'moment';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
@Component({
    selector: 'jhi-exam-update',
    templateUrl: './exam-update.component.html',
})
export class ExamUpdateComponent implements OnInit {
    exam: Exam;
    course: Course;
    isSaving: boolean;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private jhiAlertService: JhiAlertService,
        private courseManagementService: CourseManagementService,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {}

    ngOnInit(): void {
        this.route.data.subscribe(({ exam }) => {
            this.exam = exam;
            this.courseManagementService.find(Number(this.route.snapshot.paramMap.get('courseId'))).subscribe(
                (response: HttpResponse<Course>) => {
                    this.exam.course = response.body!;
                    this.course = response.body!;
                },
                (err: HttpErrorResponse) => onError(this.jhiAlertService, err),
            );
            if (!this.exam.gracePeriod) {
                this.exam.gracePeriod = 180;
            }
            if (!this.exam.numberOfCorrectionRoundsInExam) {
                this.exam.numberOfCorrectionRoundsInExam = 1;
            }
        });
    }

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     * Returns to the detail page if there is no previous state and we edited an existing exam
     * Returns to the overview page if there is no previous state and we created a new exam
     */
    previousState() {
        this.navigationUtilService.navigateBackWithOptional(['course-management', this.course.id!.toString(), 'exams'], this.exam.id?.toString());
    }

    save() {
        this.isSaving = true;
        if (this.exam.id !== undefined) {
            this.subscribeToSaveResponse(this.examManagementService.update(this.course.id!, this.exam));
        } else {
            this.subscribeToSaveResponse(this.examManagementService.create(this.course.id!, this.exam));
        }
    }

    subscribeToSaveResponse(result: Observable<HttpResponse<Exam>>) {
        result.subscribe(
            () => this.onSaveSuccess(),
            (err: HttpErrorResponse) => this.onSaveError(err),
        );
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse) {
        onError(this.jhiAlertService, error);
        this.isSaving = false;
    }

    get isValidConfiguration(): boolean {
        const examConductionDatesValid = this.isValidVisibleDate && this.isValidStartDate && this.isValidEndDate;
        const examReviewDatesValid = this.isValidPublishResultsDate && this.isValidExamStudentReviewStart && this.isValidExamStudentReviewEnd;
        const examNumberOfCorrectionsValid = this.isValidNumberOfCorrectionRounds;
        const examMaxPointsValid = this.isValidMaxPoints;
        return examConductionDatesValid && examReviewDatesValid && examNumberOfCorrectionsValid && examMaxPointsValid;
    }

    get isValidVisibleDate(): boolean {
        return this.exam.visibleDate !== undefined;
    }

    get isValidNumberOfCorrectionRounds(): boolean {
        return this.exam?.numberOfCorrectionRoundsInExam! < 3 && this.exam?.numberOfCorrectionRoundsInExam! > 0;
    }

    get isValidMaxPoints(): boolean {
        return this.exam?.maxPoints !== undefined && this.exam?.maxPoints > 0;
    }

    get isValidStartDate(): boolean {
        return this.exam.startDate !== undefined && moment(this.exam.startDate).isAfter(this.exam.visibleDate);
    }

    get isValidEndDate(): boolean {
        return this.exam.endDate !== undefined && moment(this.exam.endDate).isAfter(this.exam.startDate);
    }

    get isValidPublishResultsDate(): boolean {
        // allow instructors to set publishResultsDate later
        if (!this.exam.publishResultsDate) {
            return true;
        }
        // check for undefined because undefined is otherwise treated as the now moment by moment.js
        return this.exam.endDate !== undefined && moment(this.exam.publishResultsDate).isAfter(this.exam.endDate);
    }

    get isValidExamStudentReviewStart(): boolean {
        // allow instructors to set examStudentReviewStart later
        if (!this.exam.examStudentReviewStart) {
            return true;
        }
        // check for undefined because undefined is otherwise treated as the now moment by moment.js
        return this.exam.publishResultsDate !== undefined && moment(this.exam.examStudentReviewStart).isAfter(this.exam.publishResultsDate);
    }

    get isValidExamStudentReviewEnd(): boolean {
        // allow instructors to set examStudentReviewEnd later
        if (!this.exam.examStudentReviewEnd) {
            if (!this.exam.examStudentReviewStart || !this.exam.examStudentReviewStart.isValid()) {
                return true;
            } else {
                return false;
            }
        }
        // check for undefined because undefined is otherwise treated as the now moment by moment.js
        return this.exam.examStudentReviewStart !== undefined && moment(this.exam.examStudentReviewEnd).isAfter(this.exam.examStudentReviewStart);
    }
}
