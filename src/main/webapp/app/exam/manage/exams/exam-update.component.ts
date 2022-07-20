import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { Observable } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import dayjs from 'dayjs/esm';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faBan, faExclamationTriangle, faSave } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exam-update',
    templateUrl: './exam-update.component.html',
})
export class ExamUpdateComponent implements OnInit {
    exam: Exam;
    course: Course;
    isSaving: boolean;
    // The exam.workingTime is stored in seconds, but the working time should be displayed in minutes to the user
    workingTimeInMinutes: number;
    // The maximum working time in Minutes (used as a dynamic max-value for the working time Input)
    maxWorkingTimeInMinutes: number;

    // Icons
    faSave = faSave;
    faBan = faBan;
    faExclamationTriangle = faExclamationTriangle;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private alertService: AlertService,
        private courseManagementService: CourseManagementService,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {}

    ngOnInit(): void {
        this.route.data.subscribe(({ exam }) => {
            this.exam = exam;
            this.courseManagementService.find(Number(this.route.snapshot.paramMap.get('courseId'))).subscribe({
                next: (response: HttpResponse<Course>) => {
                    this.exam.course = response.body!;
                    this.course = response.body!;
                },
                error: (err: HttpErrorResponse) => onError(this.alertService, err),
            });
            if (!this.exam.gracePeriod) {
                this.exam.gracePeriod = 180;
            }
            // test exam only feature automatic assessment
            if (this.exam.testExam) {
                this.exam.numberOfCorrectionRoundsInExam = 0;
            } else if (!this.exam.numberOfCorrectionRoundsInExam) {
                this.exam.numberOfCorrectionRoundsInExam = 1;
            }
        });
        // Initialize helper attributes
        this.workingTimeInMinutes = this.exam.workingTime! / 60;
        this.calculateMaxWorkingTime();
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
        result.subscribe({
            next: () => this.onSaveSuccess(),
            error: (err: HttpErrorResponse) => this.onSaveError(err),
        });
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError(error: HttpErrorResponse) {
        onError(this.alertService, error);
        this.isSaving = false;
    }

    get isValidConfiguration(): boolean {
        const examConductionDatesValid = this.isValidVisibleDate && this.isValidStartDate && this.isValidEndDate;
        const examReviewDatesValid = this.isValidPublishResultsDate && this.isValidExamStudentReviewStart && this.isValidExamStudentReviewEnd;
        const examNumberOfCorrectionsValid = this.isValidNumberOfCorrectionRounds;
        const examMaxPointsValid = this.isValidMaxPoints;
        const examValidWorkingTime = this.validateWorkingTime;
        return examConductionDatesValid && examReviewDatesValid && examNumberOfCorrectionsValid && examMaxPointsValid && examValidWorkingTime;
    }

    get isValidVisibleDate(): boolean {
        return this.exam.visibleDate !== undefined;
    }

    get isValidNumberOfCorrectionRounds(): boolean {
        if (this.exam.testExam) {
            return this.exam.numberOfCorrectionRoundsInExam === 0;
        } else {
            return this.exam?.numberOfCorrectionRoundsInExam! < 3 && this.exam?.numberOfCorrectionRoundsInExam! > 0;
        }
    }

    get isValidMaxPoints(): boolean {
        return this.exam?.maxPoints !== undefined && this.exam?.maxPoints > 0;
    }

    /**
     * Validates the given StartDate.
     * For real exams, the visibleDate has to be strictly prior the startDate.
     * For test exams, the visibleDate has to be prior or equal to the startDate.
     */
    get isValidStartDate(): boolean {
        if (this.exam.startDate === undefined) {
            return false;
        }
        if (this.exam.testExam) {
            return dayjs(this.exam.startDate).isSameOrAfter(this.exam.visibleDate);
        } else {
            return dayjs(this.exam.startDate).isAfter(this.exam.visibleDate);
        }
    }

    /**
     * Validates the EndDate inputted by the user.
     */
    get isValidEndDate(): boolean {
        return this.exam.endDate !== undefined && dayjs(this.exam.endDate).isAfter(this.exam.startDate);
    }

    /**
     * Calculates the WorkingTime for real exams based on the start- and end-time.
     */
    get calculateWorkingTime(): number {
        if (!this.exam.testExam) {
            if (this.exam.startDate && this.exam.endDate) {
                this.exam.workingTime = dayjs(this.exam.endDate).diff(this.exam.startDate, 's');
            } else {
                this.exam.workingTime = 0;
            }
            this.workingTimeInMinutes = this.exam.workingTime / 60;
        }
        return this.workingTimeInMinutes;
    }

    /**
     * Validates the WorkingTime.
     * For test exams, the WorkingTime should be at least 1 and smaller / equal to the working window
     * For real exams, the WorkingTime is calculated based on the startDate and EndDate and should match the time difference.
     */
    get validateWorkingTime(): boolean {
        if (this.exam.testExam) {
            if (this.exam.workingTime === undefined || this.exam.workingTime < 1) {
                return false;
            }
            if (this.exam.startDate && this.exam.endDate) {
                return this.exam.workingTime <= dayjs(this.exam.endDate).diff(this.exam.startDate, 's');
            }
            return false;
        }
        if (this.exam.workingTime && this.exam.startDate && this.exam.endDate) {
            return this.exam.workingTime === dayjs(this.exam.endDate).diff(this.exam.startDate, 's');
        }
        return false;
    }

    /**
     * Used to convert workingTimeInMinutes into exam.workingTime (in seconds) every time, the user inputs a new
     * working time for a test exam
     * @param event when the user inputs a new working time
     */
    convertWorkingTimeFromMinutesToSeconds(event: any) {
        this.workingTimeInMinutes = event.target.value;
        this.exam.workingTime = this.workingTimeInMinutes * 60;
    }

    /**
     * Used to determine the maximum working time every time, the user changes the start- or endDate.
     * Used to show a graphical warning at the working time input field
     */
    calculateMaxWorkingTime() {
        if (this.exam.testExam) {
            if (this.exam.startDate && this.exam.endDate) {
                this.maxWorkingTimeInMinutes = dayjs(this.exam.endDate).diff(this.exam.startDate, 's') / 60;
            } else {
                this.maxWorkingTimeInMinutes = 0;
            }
        }
    }

    get isValidPublishResultsDate(): boolean {
        // allow instructors to set publishResultsDate later
        if (!this.exam.publishResultsDate) {
            return true;
        }
        // check for undefined because undefined is otherwise treated as the now dayjs
        return this.exam.endDate !== undefined && dayjs(this.exam.publishResultsDate).isAfter(this.exam.endDate);
    }

    get isValidExamStudentReviewStart(): boolean {
        // allow instructors to set examStudentReviewStart later
        if (!this.exam.examStudentReviewStart) {
            return true;
        }
        // check for undefined because undefined is otherwise treated as the now dayjs
        return this.exam.publishResultsDate !== undefined && dayjs(this.exam.examStudentReviewStart).isAfter(this.exam.publishResultsDate);
    }

    get isValidExamStudentReviewEnd(): boolean {
        // checks whether the end date can be undefined depending on if there is an undefined or manually deleted start date
        if (!this.exam.examStudentReviewEnd) {
            return !this.exam.examStudentReviewStart || !this.exam.examStudentReviewStart.isValid();
        }
        // check for undefined because undefined is otherwise treated as the now dayjs
        return this.exam.examStudentReviewStart !== undefined && dayjs(this.exam.examStudentReviewEnd).isAfter(this.exam.examStudentReviewStart);
    }
}
