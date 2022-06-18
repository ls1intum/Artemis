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
import { faBan, faCheckDouble, faExclamationTriangle, faFileUpload, faKeyboard, faProjectDiagram, faSave, faFont } from '@fortawesome/free-solid-svg-icons';
import { AccountService } from 'app/core/auth/account.service';
import { tap } from 'rxjs/operators';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

@Component({
    selector: 'jhi-exam-update',
    templateUrl: './exam-update.component.html',
    styleUrls: ['./exam-update.component.scss'],
})
export class ExamUpdateComponent implements OnInit {
    exam: Exam;
    course: Course;
    isSaving: boolean;
    // The exam.workingTime is stored in seconds, but the working time should be displayed in minutes to the user
    workingTimeInMinutes: number;
    // The maximum working time in Minutes (used as a dynamic max-value for the working time Input)
    maxWorkingTimeInMinutes: number;
    // Interims-boolean to hide the option to create an TestExam in production, as the feature is not yet fully implemented
    isAdmin: boolean;
    isImport = false;
    // Map to determine, which exercises should be imported alongside an exam
    selectedExercises?: Map<ExerciseGroup, Set<Exercise>>;
    // Expose enums to the template
    exerciseType = ExerciseType;

    // Icons
    faSave = faSave;
    faBan = faBan;
    faExclamationTriangle = faExclamationTriangle;
    faCheckDouble = faCheckDouble;
    faFileUpload = faFileUpload;
    faProjectDiagram = faProjectDiagram;
    faKeyboard = faKeyboard;
    faFont = faFont;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private alertService: AlertService,
        private courseManagementService: CourseManagementService,
        private navigationUtilService: ArtemisNavigationUtilService,
        private accountService: AccountService,
    ) {}

    ngOnInit(): void {
        this.route.data.subscribe(({ exam }) => {
            this.exam = exam;

            // Tap the URL to determine, if the Exam should be imported
            this.route.url.pipe(tap((segments) => (this.isImport = segments.some((segment) => segment.path === 'import')))).subscribe();

            if (this.isImport) {
                this.resetIdAndDatesForImport();
                this.selectedExercises = new Map<ExerciseGroup, Set<Exercise>>();
                this.exam.exerciseGroups?.forEach((exerciseGroup) => {
                    this.selectedExercises!.set(exerciseGroup, new Set<Exercise>(exerciseGroup.exercises!));
                });
            }

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
            if (!this.exam.numberOfCorrectionRoundsInExam) {
                this.exam.numberOfCorrectionRoundsInExam = 1;
            }
        });
        // Initialize helper attributes
        this.workingTimeInMinutes = this.exam.workingTime! / 60;
        this.calculateMaxWorkingTime();
        this.isAdmin = this.accountService.isAdmin();
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
        if (this.isImport) {
            this.subscribeToSaveResponse(this.examManagementService.import(this.course.id!, this.exam));
        } else if (this.exam.id !== undefined) {
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
        return this.exam?.numberOfCorrectionRoundsInExam! < 3 && this.exam?.numberOfCorrectionRoundsInExam! > 0;
    }

    get isValidMaxPoints(): boolean {
        return this.exam?.maxPoints !== undefined && this.exam?.maxPoints > 0;
    }

    /**
     * Validates the given StartDate.
     * For RealExams, the visibleDate has to be strictly prior the startDate.
     * For TestExams, the visibleDate has to be prior or equal to the startDate.
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
     * Calculates the WorkingTime for RealExams based on the start- and end-time.
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
     * For TestExams, the WorkingTime should be at least 1 and smaller / equal to the working window
     * For RealExams, the WorkingTime is calculated based on the startDate and EndDate and should match the time difference.
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
     * working time for a TestExam
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

    /**
     * Helper-Method to reset the Exam Id and Exam dates when importing the Exam
     * @private
     */
    private resetIdAndDatesForImport() {
        this.exam.id = undefined;
        this.exam.visibleDate = undefined;
        this.exam.startDate = undefined;
        this.exam.endDate = undefined;
        this.exam.publishResultsDate = undefined;
        this.exam.examStudentReviewStart = undefined;
        this.exam.examStudentReviewEnd = undefined;
    }

    /**
     * Sets the selected exercise for an exercise group in the testRunConfiguration dictionary, {@link testRunConfiguration}.
     * There, the exerciseGroups' id is used as a key to track the selected exercises for this test run.
     * @param exercise The selected exercise
     * @param exerciseGroup The exercise group for which the user selected an exercise
     */
    onSelectExercise(exercise: Exercise, exerciseGroup: ExerciseGroup) {
        if (this.selectedExercises!.get(exerciseGroup)!.has(exercise)) {
            // Case Exercise is already selected -> delete
            this.selectedExercises!.get(exerciseGroup)!.delete(exercise);
        } else {
            this.selectedExercises!.get(exerciseGroup)!.add(exercise);
        }
    }

    exerciseIsSelected(exercise: Exercise, exerciseGroup: ExerciseGroup): boolean {
        return this.selectedExercises!.get(exerciseGroup)!.has(exercise);
    }

    /**
     * Get an icon for the type of the given exercise.
     * @param exercise {Exercise}
     */
    getExerciseIcon(exercise: Exercise): IconProp {
        switch (exercise.type) {
            case ExerciseType.QUIZ:
                return this.faCheckDouble;
            case ExerciseType.FILE_UPLOAD:
                return this.faFileUpload;
            case ExerciseType.MODELING:
                return this.faProjectDiagram;
            case ExerciseType.PROGRAMMING:
                return this.faKeyboard;
            default:
                return this.faFont;
        }
    }
}
