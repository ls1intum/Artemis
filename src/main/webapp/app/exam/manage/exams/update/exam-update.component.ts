import { WorkingTimeChangeComponent } from 'app/exam/shared/working-time-change/working-time-change.component';
import dayjs from 'dayjs/esm';
import { omit } from 'lodash-es';
import { combineLatest, takeWhile } from 'rxjs';
import { map } from 'rxjs/operators';
import { AfterViewInit, Component, OnDestroy, OnInit, computed, inject, signal, viewChild, viewChildren } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { Dialog } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { SelectButtonModule } from 'primeng/selectbutton';
import { faBan, faExclamationTriangle, faSave } from '@fortawesome/free-solid-svg-icons';
import { Exam, hasTestExamType } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/foundation/service/alert.service';
import { Course, isCommunicationEnabled } from 'app/course/shared/entities/course.model';
import { onError } from 'app/foundation/util/global.utils';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ExamImportProgressDialogComponent } from 'app/exam/manage/exams/exam-import/exam-import-progress-dialog.component';
import { DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { normalWorkingTime } from 'app/exam/overview/exam.utils';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ExamModePickerComponent } from '../exam-mode-picker/exam-mode-picker.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { ConfirmEntityNameComponent } from 'app/shared-ui/confirm-entity-name/confirm-entity-name.component';
import { ExamConductionComponent } from 'app/exam/manage/exams/update/exam-conduction/exam-conduction.component';
import { TitleChannelNameComponent } from 'app/shared-ui/form/title-channel-name/title-channel-name.component';

@Component({
    selector: 'jhi-exam-update',
    templateUrl: './exam-update.component.html',
    imports: [
        FormsModule,
        TranslateDirective,
        DocumentationButtonComponent,
        TitleChannelNameComponent,
        HelpIconComponent,
        ExamModePickerComponent,
        NgbTooltip,
        FaIconComponent,
        WorkingTimeChangeComponent,
        FormDateTimePickerComponent,
        ExamExerciseImportComponent,
        MarkdownEditorMonacoComponent,
        ArtemisTranslatePipe,
        ButtonComponent,
        ConfirmEntityNameComponent,
        Dialog,
        ExamImportProgressDialogComponent,
        MessageModule,
        SelectButtonModule,
        ExamConductionComponent,
    ],
})
export class ExamUpdateComponent implements OnInit, OnDestroy, AfterViewInit {
    private route = inject(ActivatedRoute);
    private examManagementService = inject(ExamManagementService);
    private alertService = inject(AlertService);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private calendarService = inject(CalendarService);
    private router = inject(Router);

    protected readonly faSave = faSave;
    protected readonly faBan = faBan;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly documentationType: DocumentationType = 'Exams';
    protected readonly ButtonType = ButtonType;
    protected readonly ButtonSize = ButtonSize;

    // exam is template-bound (directly and through many getters) and populated asynchronously from the route
    // resolver, so it is backed by a signal to schedule change detection. The getter/setter facade keeps the
    // many synchronous reads/writes (getters, [(ngModel)] bindings) unchanged.
    private readonly _exam = signal<Exam>(undefined!);
    get exam(): Exam {
        return this._exam();
    }
    set exam(value: Exam) {
        this._exam.set(value);
    }
    course: Course;
    readonly isSaving = signal(false);
    readonly isImport = signal(false);
    readonly isImportInSameCourse = signal(false);

    readonly hideChannelNameInput = signal(false);
    private originalStartDate?: dayjs.Dayjs;

    private originalEndDate?: dayjs.Dayjs;

    private componentActive = true;

    confirmEntityNameValue = signal('');
    confirmDateChangeVisible = signal(false);
    confirmDisabled = computed(() => {
        const value = this.confirmEntityNameValue();
        return !value || !this.exam?.title || value !== this.exam.title;
    });
    readonly examTimelineValid = signal(false);

    // Link to the component enabling the selection of exercise groups and exercises for import
    examExerciseImportComponent = viewChild.required(ExamExerciseImportComponent);
    examImportProgressDialog = viewChild.required(ExamImportProgressDialogComponent);

    readonly datePickers = viewChildren(FormDateTimePickerComponent);

    private viewInitialized = false;

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
                if (hasTestExamType(exam)) {
                    exam.numberOfCorrectionRoundsInExam = 0;
                } else if (!exam.numberOfCorrectionRoundsInExam) {
                    exam.numberOfCorrectionRoundsInExam = 1;
                }

                this.exam = exam;
                this.isImport.set(isImport);
                this.isImportInSameCourse.set(isImport && exam.course?.id === data.course.id);
                this.originalStartDate = exam.startDate?.clone();
                this.originalEndDate = exam.endDate?.clone();

                this.course = data.course;
                this.exam.course = data.course;

                // Prefill course name with course title for new exams
                if (!this.exam.id && !this.exam.courseName && this.course.title) {
                    this.exam.courseName = this.course.title;
                }

                if (!this.exam.startText) {
                    this.exam.startText = this.examDefaultStartText;
                }
                this.hideChannelNameInput.set((!!exam.id && !exam.channelName) || !isCommunicationEnabled(this.course));
                this.refreshDatePickerValidation();
            });
    }

    ngAfterViewInit() {
        this.viewInitialized = true;
        this.refreshDatePickerValidation();
    }

    ngOnDestroy() {
        this.componentActive = false;
    }

    get oldWorkingTime(): number | undefined {
        return normalWorkingTime(this.originalStartDate, this.originalEndDate);
    }

    get newWorkingTime(): number | undefined {
        return this.exam.workingTime;
    }

    private refreshDatePickerValidation() {
        if (!this.viewInitialized) {
            return;
        }
        // Delay until the current change detection cycle completed so the pickers have the latest ngModel values.
        setTimeout(() => this.datePickers().forEach((picker) => picker.updateSignals()), 0);
    }

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     * Returns to the detail page if there is no previous state, and we edited an existing exam
     * Returns to the overview page if there is no previous state, and we created a new exam
     */
    resetToPreviousState() {
        this.navigationUtilService.navigateBackWithOptional(['course-management', this.course.id!.toString(), 'exams'], this.exam.id?.toString());
    }

    onExamModeChange() {
        if (hasTestExamType(this.exam)) {
            this.exam.examWithAttendanceCheck = false;
        }
    }

    /**
     * Saves the exam. If the dates have changed and the exam is ongoing, a confirmation modal is shown to the user.
     * If either the user confirms the modal, the exam is not ongoing or the dates have not changed, the exam is saved.
     */
    handleSubmit() {
        const datesChanged = !(this.exam.startDate?.isSame(this.originalStartDate) && this.exam.endDate?.isSame(this.originalEndDate));

        if (datesChanged && this.isOngoingExam) {
            this.confirmEntityNameValue.set('');
            this.confirmDateChangeVisible.set(true);
        } else {
            this.save();
        }
    }

    /**
     * Updates the entered exam title used to enable/disable the confirm button.
     */
    onConfirmNameChange(value: string) {
        this.confirmEntityNameValue.set(value);
    }

    confirmDateChange() {
        if (this.confirmDisabled()) {
            return;
        }
        this.confirmDateChangeVisible.set(false);
        this.save();
    }

    cancelDateChange() {
        this.confirmDateChangeVisible.set(false);
    }

    /**
     * Saves the exam and navigates to the detail page of the exam if the save was successful.
     * If the save was not successful, an error is shown to the user.
     */
    save() {
        // Guard against re-entry (e.g. pressing Enter in the form while a save/import is already running): the save button is
        // disabled while saving, but ngSubmit can still fire. A second import would reset the in-flight progress dialog.
        if (this.isSaving()) {
            return;
        }
        this.isSaving.set(true);

        // Importing an exam can fail per exercise and take a while (programming repository copies). It therefore runs behind
        // a progress dialog that shows live websocket progress and a persistent, must-dismiss summary of skipped/incomplete
        // exercises, after which we navigate to the imported exam.
        if (this.isImport() && this.exam?.exerciseGroups) {
            this.importExam();
            return;
        }

        const request$ = this.exam.id ? this.examManagementService.update(this.course.id!, this.exam) : this.examManagementService.create(this.course.id!, this.exam);
        request$
            .pipe(
                map((response: HttpResponse<Exam>) => response.body!),
                takeWhile(() => this.componentActive),
            )
            .subscribe({
                next: this.onSaveSuccess.bind(this),
                error: this.onSaveError.bind(this),
            });
    }

    /**
     * Imports the exam behind a progress dialog and navigates to the imported exam once the user dismisses the dialog.
     * @private
     */
    private importExam() {
        // We validate the user input for the exercise group selection here, so it is only called once the user desires to import the exam
        if (!this.examExerciseImportComponent().validateUserInput()) {
            this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.invalidExerciseConfiguration');
            this.isSaving.set(false);
            return;
        }
        this.exam.exerciseGroups = this.examExerciseImportComponent().mapSelectedExercisesToExerciseGroups();
        const totalExercises = (this.exam.exerciseGroups ?? []).reduce((sum, group) => sum + (group.exercises?.length ?? 0), 0);
        const importId = this.examManagementService.generateImportId();
        const request$ = this.examManagementService.import(this.course.id!, this.exam, importId);
        this.examImportProgressDialog()
            .runImport(importId, totalExercises, request$)
            .then((response) => {
                const importedExam = response.body?.exam;
                if (importedExam) {
                    this.onSaveSuccess(importedExam);
                } else {
                    this.isSaving.set(false);
                }
            })
            .catch((httpErrorResponse: HttpErrorResponse) => this.onSaveError(httpErrorResponse));
    }

    /**
     * Navigates to the detail page of the exam if the save was successful.
     * @param exam
     * @private
     */
    private async onSaveSuccess(exam: Exam) {
        this.isSaving.set(false);
        this.calendarService.reloadEvents();
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
            this.examExerciseImportComponent().updateMapsAfterRejectedImportDueToInvalidProjectKey();
            const numberOfInvalidProgrammingExercises = httpErrorResponse.error.numberOfInvalidProgrammingExercises;
            this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.invalidKey', { number: numberOfInvalidProgrammingExercises });
        } else if (errorKey === 'duplicatedProgrammingExerciseShortName' || errorKey === 'duplicatedProgrammingExerciseTitle') {
            this.exam!.exerciseGroups = httpErrorResponse.error.params.exerciseGroups!;
            this.examExerciseImportComponent().updateMapsAfterRejectedImportDueToDuplicatedShortNameOrTitle();
            this.alertService.error('artemisApp.examManagement.exerciseGroup.importModal.' + errorKey);
        } else {
            if (httpErrorResponse.error && httpErrorResponse.error.title) {
                this.alertService.addErrorAlert(httpErrorResponse.error.title, httpErrorResponse.error.message, httpErrorResponse.error.params);
            } else {
                onError(this.alertService, httpErrorResponse);
            }
        }
        this.isSaving.set(false);
    }

    /**
     * Returns true if the original exam is currently ongoing before any changes, false otherwise.
     */
    get isOngoingExam(): boolean {
        return !!(this.exam.id && this.originalStartDate && this.originalEndDate && dayjs().isBetween(this.originalStartDate, this.originalEndDate));
    }

    get isValidConfiguration(): boolean {
        const examConductionValid = this.examTimelineValid();
        const examReviewDatesValid = this.isValidPublishResultsDate && this.isValidExamStudentReviewStart && this.isValidExamStudentReviewEnd;
        const examNumberOfCorrectionsValid = this.isValidNumberOfCorrectionRounds;
        const examMaxPointsValid = this.isValidMaxPoints;
        const examValidExampleSolutionPublicationDate = this.isValidExampleSolutionPublicationDate;
        const examValidNumberOfExercises = this.isValidNumberOfExercises;
        return (
            examConductionValid &&
            examReviewDatesValid &&
            examNumberOfCorrectionsValid &&
            examMaxPointsValid &&
            examValidExampleSolutionPublicationDate &&
            examValidNumberOfExercises
        );
    }

    /**
     * Returns a boolean indicating whether the exam's number of exercises is valid.
     * The number of exercises is valid if it's not set, or if it's between 1 and 100.
     *
     * @returns {boolean} `true` if the exam's number of exercises is valid, `false` otherwise.
     */
    get isValidNumberOfExercises(): boolean {
        if (this.exam.numberOfExercisesInExam === undefined || this.exam.numberOfExercisesInExam === null) {
            return true;
        }
        return this.exam.numberOfExercisesInExam >= 1 && this.exam.numberOfExercisesInExam <= 100;
    }

    get isValidNumberOfCorrectionRounds(): boolean {
        if (hasTestExamType(this.exam)) {
            return this.exam.numberOfCorrectionRoundsInExam === 0;
        } else {
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            return this.exam?.numberOfCorrectionRoundsInExam! < 3 && this.exam?.numberOfCorrectionRoundsInExam! > 0;
        }
    }

    get isValidMaxPoints(): boolean {
        return !!this.exam?.examMaxPoints && this.exam?.examMaxPoints > 0 && this.exam?.examMaxPoints <= 9999;
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

    /**
     * Returns the appropriate translation key for the save button title.
     *
     * If the exam is being imported, the title reflects an import action;
     * otherwise, it reflects a standard save action.
     *
     * @returns {string} The translation key for the save button title.
     */
    get saveTitle(): string {
        return this.isImport() ? 'entity.action.import' : 'entity.action.save';
    }
}

/**
 * Prepares the exam for import by omitting all properties that should not be imported.
 */
export const prepareExamForImport = (exam: Exam): Exam => ({
    ...omit(exam, ['id', 'visibleDate', 'startDate', 'endDate', 'publishResultsDate', 'examStudentReviewStart', 'examStudentReviewEnd', 'examUsers', 'studentExams']),
    workingTime: 0,
});
