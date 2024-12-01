import dayjs from 'dayjs/esm';
import { omit } from 'lodash-es';
import { combineLatest, takeWhile } from 'rxjs';
import { map } from 'rxjs/operators';
import { Component, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { faBan, faExclamationTriangle, faSave } from '@fortawesome/free-solid-svg-icons';
import { Exam } from 'app/entities/exam/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { Course, isCommunicationEnabled } from 'app/entities/course.model';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { examWorkingTime, normalWorkingTime } from 'app/exam/participate/exam.utils';

@Component({
    selector: 'jhi-exam-update',
    templateUrl: './exam-update.component.html',
})
export class ExamUpdateComponent implements OnInit, OnDestroy {
    protected readonly faSave = faSave;
    protected readonly faBan = faBan;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly documentationType: DocumentationType = 'Exams';

    exam: Exam;
    course: Course;
    isSaving: boolean;
    isImport = false;
    isImportInSameCourse = false;

    hideChannelNameInput = false;
    private originalStartDate?: dayjs.Dayjs;

    private originalEndDate?: dayjs.Dayjs;

    private componentActive = true;
    // Link to the component enabling the selection of exercise groups and exercises for import
    @ViewChild(ExamExerciseImportComponent) examExerciseImportComponent: ExamExerciseImportComponent;

    @ViewChild('workingTimeConfirmationContent') public workingTimeConfirmationContent: TemplateRef<any>;

    constructor(
        private route: ActivatedRoute,
        private examManagementService: ExamManagementService,
        private alertService: AlertService,
        private navigationUtilService: ArtemisNavigationUtilService,
        private modalService: NgbModal,
        private router: Router,
        private artemisTranslatePipe: ArtemisTranslatePipe,
    ) {}

    ngOnInit(): void {
        combineLatest([this.route.url, this.route.data])
            .pipe(takeWhile(() => this.componentActive))
            .subscribe(([segments, data]) => {
                const isImport = segments.some(({ path }) => path === 'import');
                const exam: Exam = isImport ? prepareExamForImport(data.exam) : data.exam;

                if (!exam.gracePeriod) {
                    exam.gracePeriod = 180;
                }

                // test exam only feature automatic assessment
                if (exam.testExam) {
                    exam.numberOfCorrectionRoundsInExam = 0;
                } else if (!exam.numberOfCorrectionRoundsInExam) {
                    exam.numberOfCorrectionRoundsInExam = 1;
                }

                this.exam = exam;
                this.isImport = isImport;
                this.isImportInSameCourse = isImport && exam.course?.id === data.course.id;
                this.originalStartDate = exam.startDate?.clone();
                this.originalEndDate = exam.endDate?.clone();

                this.course = data.course;
                this.exam.course = data.course;

                if (!this.exam.startText) {
                    this.exam.startText = this.examDefaultStartText;
                }
                this.hideChannelNameInput = (!!exam.id && !exam.channelName) || !isCommunicationEnabled(this.course);
            });
    }

    ngOnDestroy() {
        this.componentActive = false;
    }

    /**
     * Sets the exam working time in minutes.
     * @param minutes
     */
    set workingTimeInMinutes(minutes: number) {
        this.exam.workingTime = minutes * 60;
    }

    /**
     * Returns the exam working time in minutes.
     */
    get workingTimeInMinutes(): number {
        return this.exam.workingTime ? this.exam.workingTime / 60 : 0;
    }

    /**
     * Returns the exma working time in minutes, rounded to one decimal place.
     * Used for display purposes.
     */
    get workingTimeInMinutesRounded(): number {
        return Math.round(this.workingTimeInMinutes * 10) / 10;
    }

    get oldWorkingTime(): number | undefined {
        return normalWorkingTime(this.originalStartDate, this.originalEndDate);
    }

    get newWorkingTime(): number | undefined {
        return this.exam.workingTime;
    }

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     * Returns to the detail page if there is no previous state, and we edited an existing exam
     * Returns to the overview page if there is no previous state, and we created a new exam
     */
    resetToPreviousState() {
        this.navigationUtilService.navigateBackWithOptional(['course-management', this.course.id!.toString(), 'exams'], this.exam.id?.toString());
    }

    /**
     * Updates the working time for real exams based on the start and end dates.
     */
    updateExamWorkingTime() {
        if (this.exam.testExam) return;

        this.exam.workingTime = examWorkingTime(this.exam) ?? 0;
    }

    onExamModeChange() {
        if (this.exam.testExam) {
            // Preserve the rounded value
            this.exam.examWithAttendanceCheck = false;
            this.roundWorkingTime();
        } else {
            // Otherwise, the working time should depend on the dates as usual
            this.updateExamWorkingTime();
        }
    }

    /**
     * Rounds the working time of the exam in minutes such that it only has one decimal place.
     */
    roundWorkingTime() {
        this.workingTimeInMinutes = this.workingTimeInMinutesRounded;
    }

    /**
     * Checks if the exam visibility date is set too early relative to the exam start date.
     * If the visibility date is more than 4 hours (240 minutes) before the start date.
     * it indicates that the visibility date is set too early.
     *
     * @returns {boolean} true if the visibility date is more than 4 hours before the start date, false otherwise.
     */
    get checkExamVisibilityTime(): boolean {
        if (!this.isVisibleDateSet || !this.isStartDateSet) {
            return false;
        }

        const visibleDate = dayjs(this.exam.visibleDate);
        const startDate = dayjs(this.exam.startDate);

        // Calculate the difference in minutes
        const differenceInMinutes = startDate.diff(visibleDate, 'minute');

        // Check if the difference is more than 4 hours (240 minutes)
        return differenceInMinutes > 240;
    }

    /**
     * Returns the maximum working time in minutes for test exams.
     */
    get maxWorkingTimeInMinutes(): number {
        if (!this.exam.testExam) return 0;

        if (this.exam.startDate && this.exam.endDate) {
            // This considers decimal places as well.
            return dayjs(this.exam.endDate).diff(this.exam.startDate, 'm', true);
        } else {
            // In case of an import, the exam.workingTime is imported, but the start / end date are deleted -> no error should be shown to the user in this case
            return this.isImport ? this.workingTimeInMinutes : 0;
        }
    }

    /**
     * Saves the exam. If the dates have changed and the exam is ongoing, a confirmation modal is shown to the user.
     * If either the user confirms the modal, the exam is not ongoing or the dates have not changed, the exam is saved.
     */
    handleSubmit() {
        const datesChanged = !(this.exam.startDate?.isSame(this.originalStartDate) && this.exam.endDate?.isSame(this.originalEndDate));

        if (datesChanged && this.isOngoingExam) {
            const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'lg' });
            modalRef.componentInstance.title = 'artemisApp.examManagement.dateChange.title';
            modalRef.componentInstance.text = this.artemisTranslatePipe.transform('artemisApp.examManagement.dateChange.message');
            modalRef.componentInstance.contentRef = this.workingTimeConfirmationContent;
            modalRef.result.then(this.save.bind(this));
        } else {
            this.save();
        }
    }

    /**
     * Saves the exam and navigates to the detail page of the exam if the save was successful.
     * If the save was not successful, an error is shown to the user.
     */
    save() {
        this.isSaving = true;

        this.createOrUpdateOrImportExam()
            ?.pipe(
                map((response: HttpResponse<Exam>) => response.body!),
                takeWhile(() => this.componentActive),
            )
            .subscribe({
                next: this.onSaveSuccess.bind(this),
                error: this.onSaveError.bind(this),
            });
    }

    /**
     * Creates, updates or imports the exam depending on the current state of the component.
     * @private
     */
    private createOrUpdateOrImportExam() {
        if (this.isImport && this.exam?.exerciseGroups) {
            // We validate the user input for the exercise group selection here, so it is only called once the user desires to import the exam
            if (!this.examExerciseImportComponent.validateUserInput()) {
                this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.invalidExerciseConfiguration');
                this.isSaving = false;
                return;
            }
            this.exam.exerciseGroups = this.examExerciseImportComponent.mapSelectedExercisesToExerciseGroups();
            return this.examManagementService.import(this.course.id!, this.exam);
        } else if (this.exam.id) {
            return this.examManagementService.update(this.course.id!, this.exam);
        } else {
            return this.examManagementService.create(this.course.id!, this.exam);
        }
    }

    /**
     * Navigates to the detail page of the exam if the save was successful.
     * @param exam
     * @private
     */
    private async onSaveSuccess(exam: Exam) {
        this.isSaving = false;
        await this.router.navigate(['course-management', this.course.id, 'exams', exam.id]);
        window.scrollTo(0, 0);
    }

    /**
     * Shows an error to the user if the save was not successful.
     * @param httpErrorResponse
     * @private
     */
    private onSaveError(httpErrorResponse: HttpErrorResponse) {
        const errorKey = httpErrorResponse.error?.errorKey;
        if (errorKey === 'invalidKey') {
            this.exam.exerciseGroups = httpErrorResponse.error.params.exerciseGroups!;
            // The update() Method is called to update the exercises
            this.examExerciseImportComponent.updateMapsAfterRejectedImportDueToInvalidProjectKey();
            const numberOfInvalidProgrammingExercises = httpErrorResponse.error.numberOfInvalidProgrammingExercises;
            this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.invalidKey', { number: numberOfInvalidProgrammingExercises });
        } else if (errorKey === 'duplicatedProgrammingExerciseShortName' || errorKey === 'duplicatedProgrammingExerciseTitle') {
            this.exam!.exerciseGroups = httpErrorResponse.error.params.exerciseGroups!;
            this.examExerciseImportComponent.updateMapsAfterRejectedImportDueToDuplicatedShortNameOrTitle();
            this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.' + errorKey);
        } else {
            if (httpErrorResponse.error && httpErrorResponse.error.title) {
                this.alertService.addErrorAlert(httpErrorResponse.error.title, httpErrorResponse.error.message, httpErrorResponse.error.params);
            } else {
                onError(this.alertService, httpErrorResponse);
            }
        }
        this.isSaving = false;
    }

    /**
     * Returns true if the original exam is currently ongoing before any changes, false otherwise.
     */
    get isOngoingExam(): boolean {
        return !!(this.exam.id && this.originalStartDate && this.originalEndDate && dayjs().isBetween(this.originalStartDate, this.originalEndDate));
    }

    get isValidConfiguration(): boolean {
        const examConductionDatesValid =
            this.isVisibleDateSet && this.isStartDateSet && this.isValidStartDate && this.isEndDateSet && this.isValidEndDate && this.isValidVisibleDateValue;
        const examReviewDatesValid = this.isValidPublishResultsDate && this.isValidExamStudentReviewStart && this.isValidExamStudentReviewEnd;
        const examNumberOfCorrectionsValid = this.isValidNumberOfCorrectionRounds;
        const examMaxPointsValid = this.isValidMaxPoints;
        const examValidWorkingTime = this.validateWorkingTime;
        const examValidExampleSolutionPublicationDate = this.isValidExampleSolutionPublicationDate;
        return (
            examConductionDatesValid &&
            examReviewDatesValid &&
            examNumberOfCorrectionsValid &&
            examMaxPointsValid &&
            examValidWorkingTime &&
            examValidExampleSolutionPublicationDate
        );
    }

    /**
     * Returns a boolean indicating whether the exam's visible date is set.
     *
     * @returns {boolean} `true` if the exam's visible date is set, `false` otherwise.
     */
    get isVisibleDateSet(): boolean {
        return !!this.exam.visibleDate;
    }

    /**
     * Checks if the visible date of the exam is valid.
     *
     * @returns {boolean} `true` if the visible date is valid, `false` otherwise.
     */
    get isValidVisibleDateValue(): boolean {
        return dayjs(this.exam.visibleDate).isValid();
    }

    get isValidNumberOfCorrectionRounds(): boolean {
        if (this.exam.testExam) {
            return this.exam.numberOfCorrectionRoundsInExam === 0;
        } else {
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            return this.exam?.numberOfCorrectionRoundsInExam! < 3 && this.exam?.numberOfCorrectionRoundsInExam! > 0;
        }
    }

    get isValidMaxPoints(): boolean {
        return !!this.exam?.examMaxPoints && this.exam?.examMaxPoints > 0;
    }

    /**
     * Returns a boolean indicating whether the exam's start date is set.
     *
     * @returns {boolean} `true` if the exam's start date is set, `false` otherwise.
     */
    get isStartDateSet(): boolean {
        return !!this.exam.startDate;
    }

    /**
     * Checks if the start date of the exam is valid.
     *
     * @returns {boolean} `true` if the start date is valid, `false` otherwise.
     */
    get isValidStartDateValue(): boolean {
        return dayjs(this.exam.startDate).isValid();
    }

    /**
     * Validates the given StartDate.
     * For real exams, the visibleDate has to be strictly prior the startDate.
     * For test exams, the visibleDate has to be prior or equal to the startDate.
     */
    get isValidStartDate(): boolean {
        if (this.isVisibleDateSet && this.isValidVisibleDateValue) {
            if (this.exam.testExam) {
                return dayjs(this.exam.startDate).isSameOrAfter(this.exam.visibleDate);
            } else {
                return dayjs(this.exam.startDate).isAfter(this.exam.visibleDate);
            }
        }
        return true;
    }

    /**
     * Returns a boolean indicating whether the exam's end date is set.
     *
     * @returns {boolean} `true` if the exam's end date is set, `false` otherwise.
     */
    get isEndDateSet(): boolean {
        return !!this.exam.endDate;
    }

    /**
     * Checks if the end date of the exam is valid.
     *
     * @returns {boolean} `true` if the end date is valid, `false` otherwise.
     */
    get isValidEndDateValue(): boolean {
        return dayjs(this.exam.endDate).isValid();
    }

    /**
     * Validates the EndDate inputted by the user.
     */
    get isValidEndDate(): boolean {
        if (this.isStartDateSet && this.isValidStartDateValue) {
            return dayjs(this.exam.endDate).isAfter(this.exam.startDate);
        }
        return true;
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

    get isValidPublishResultsDate(): boolean {
        // allow instructors to set publishResultsDate later
        if (!this.exam.publishResultsDate) {
            return true;
        }
        // check for undefined because undefined is otherwise treated as the now dayjs
        return !!this.exam.endDate && dayjs(this.exam.publishResultsDate).isAfter(this.exam.endDate);
    }

    get isValidExamStudentReviewStart(): boolean {
        // allow instructors to set examStudentReviewStart later
        if (!this.exam.examStudentReviewStart) {
            return true;
        }
        // check for undefined because undefined is otherwise treated as the now dayjs
        return !!this.exam.publishResultsDate && dayjs(this.exam.examStudentReviewStart).isAfter(this.exam.publishResultsDate);
    }

    get isValidExamStudentReviewEnd(): boolean {
        // checks whether the end date can be undefined depending on if there is an undefined or manually deleted start date
        if (!this.exam.examStudentReviewEnd) {
            return !this.exam.examStudentReviewStart || !this.exam.examStudentReviewStart.isValid();
        }
        // check for undefined because undefined is otherwise treated as the now dayjs
        return !!this.exam.examStudentReviewStart && dayjs(this.exam.examStudentReviewEnd).isAfter(this.exam.examStudentReviewStart);
    }

    get isValidExampleSolutionPublicationDate(): boolean {
        // allow instructors to leave exampleSolutionPublicationDate unset
        if (!this.exam.exampleSolutionPublicationDate) {
            return true;
        }
        // dayjs.isBefore(null) is always false so exampleSolutionPublicationDate is valid if the visibleDate and endDate are not set
        return !(
            dayjs(this.exam.exampleSolutionPublicationDate).isBefore(this.exam.visibleDate || null) ||
            dayjs(this.exam.exampleSolutionPublicationDate).isBefore(this.exam.endDate || null)
        );
    }

    /**
     * Default exam start text, which can be edited by instructors in the text editor
     */
    get examDefaultStartText(): string {
        const warningForInstructionsText = '<!-- Please adapt this text for your exam -->\n\n';
        const readCarefullyText = "<span class='fw-bold'>Please read the following information carefully!</span>\n\n";
        const workOnYourOwnText =
            "You must work on the exam on your own. You <span class='fw-bold text-primary'>must not</span>, in any way, get support from someone else (in person, chat, forum, discussion group, artificial intelligence, etc.) Doing so is classified as <span class='fw-bold text-primary'>cheating</span> (\"Unterschleif\") and leads to consequences as mentioned in the APSO (\"Allgemeine Prüfungs- und Studienordnung\"). In particular, the corresponding module in TUMonline will be marked as <span class='fw-bold text-primary'>failed (w. cheating)</span>, and you will only be allowed to try to pass the module in one final attempt (cf. APSO §24).\n\n";
        const checkForPlagiarismText =
            "All your submissions will be checked for plagiarism. You are <span class='fw-bold text-primary'>not allowed</span> to copy code from any external sources, including books, websites, or other students. Any instance of copying will be classified as plagiarism.\n\n";
        const programmingSubmissionText =
            "Note that results in programming exercises only indicate whether your submission compiles or not. No actual tests will be run against your submission. All actual tests will be executed after the exam is over. You can only get points for programming exercises if your submission compiles. <span class='fw-bold text-primary'>Compile errors will result in 0 points</span>.\n\n";
        const submissionPeriodText = `The submission period will close <span><!--Enter your grace period here--> seconds</span> following the official end of the exam to compensate for potential technical problems. We encourage you to upload your submissions regularly and early. <span class='fw-bold text-primary'>Only your final submission will be graded and late submission will not be accepted</span>.\n\n`;

        const workingInstructionText =
            `<div class="fw-bold">Working instructions:</div>\n` +
            `<ul>\n` +
            `  <li><span class='fw-bold text-primary'>Important</span>: Before you start solving an exercise, read the problem statement carefully.</li>\n` +
            `  <li>The exam consists of <span class='fw-bold text-primary'><!--Enter your number of points here--> points</span> and is <span class='fw-bold text-primary'><!--Enter your working time here--> minutes</span> long. Use the amount of points to determine the appropriate working time for one exercise.</li>\n` +
            `  <li>It is <span class='fw-bold text-primary'>not</span> allowed to use any artificial intelligence to solve exercises of the exam. In particular the use of OpenAI, ChatGPT, GitHub Copilot, or any similar systems is <span class='fw-bold text-primary'>forbidden</span>!</li>\n` +
            `</ul>\n`;

        return (
            warningForInstructionsText + readCarefullyText + workOnYourOwnText + checkForPlagiarismText + programmingSubmissionText + submissionPeriodText + workingInstructionText
        );
    }
}

/**
 * Prepares the exam for import by omitting all properties that should not be imported.
 */
export const prepareExamForImport = (exam: Exam): Exam => ({
    ...omit(exam, ['id', 'visibleDate', 'startDate', 'endDate', 'publishResultsDate', 'examStudentReviewStart', 'examStudentReviewEnd', 'examUsers', 'studentExams']),
    workingTime: 0,
});
