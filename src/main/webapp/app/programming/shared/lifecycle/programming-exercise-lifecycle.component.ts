import { AfterViewInit, Component, Injector, OnDestroy, OnInit, Signal, effect, inject, input, viewChildren } from '@angular/core';
import { PROFILE_ATHENA } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { faCogs, faUserCheck, faUserSlash } from '@fortawesome/free-solid-svg-icons';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Subject, Subscription } from 'rxjs';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/programming/shared/lifecycle/test-schedule-date-picker/programming-exercise-test-schedule-date-picker.component';
import { every } from 'lodash-es';
import { ActivatedRoute } from '@angular/router';
import { tap } from 'rxjs/operators';
import { ImportOptions } from 'app/programming/manage/programming-exercises';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgStyle } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-programming-exercise-lifecycle',
    templateUrl: './programming-exercise-lifecycle.component.html',
    styleUrls: ['./test-schedule-date-picker/programming-exercise-test-schedule-picker.scss'],
    imports: [
        ProgrammingExerciseTestScheduleDatePickerComponent,
        FormsModule,
        TranslateDirective,
        HelpIconComponent,
        FaIconComponent,
        NgStyle,
        ExerciseFeedbackSuggestionOptionsComponent,
        ArtemisTranslatePipe,
    ],
})
export class ProgrammingExerciseLifecycleComponent implements AfterViewInit, OnDestroy, OnInit {
    private translateService = inject(TranslateService);
    private exerciseService = inject(ExerciseService);
    private profileService = inject(ProfileService);
    private activatedRoute = inject(ActivatedRoute);
    private injector = inject(Injector);

    protected readonly assessmentType = AssessmentType;
    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly faCogs = faCogs;
    protected readonly faUserCheck = faUserCheck;
    protected readonly faUserSlash = faUserSlash;

    readonly exercise = input.required<ProgrammingExercise>();
    readonly isExamMode = input<boolean>(false);
    readonly readOnly = input<boolean>(false);
    readonly importOptions = input<ImportOptions | undefined>(undefined);
    isEditFieldDisplayedRecord = input<Record<ProgrammingExerciseInputField, boolean>>();

    readonly datePickerComponents: Signal<readonly ProgrammingExerciseTestScheduleDatePickerComponent[]> = viewChildren(ProgrammingExerciseTestScheduleDatePickerComponent);

    formValid: boolean;
    formEmpty: boolean;
    formValidChanges = new Subject<boolean>();

    inputfieldSubscriptions: (Subscription | undefined)[] = [];
    datePickerChildrenSubscription?: Subscription;

    isAthenaEnabled: boolean;

    isImport = false;
    private urlSubscription: Subscription;

    private previousExerciseRef?: ProgrammingExercise;

    constructor() {
        // Re-run the date cascade whenever the parent swaps in a new `exercise` reference.
        // ngOnInit handles the first pass synchronously; this effect handles subsequent updates.
        effect(() => {
            const newExercise = this.exercise();
            if (newExercise === this.previousExerciseRef) {
                return;
            }
            this.previousExerciseRef = newExercise;
            if (newExercise) {
                this.applyExerciseDateCascade(newExercise);
            }
        });
    }

    private applyExerciseDateCascade(newExercise: ProgrammingExercise) {
        if (this.exerciseService.hasDueDateError(newExercise)) {
            // Checking for due date errors and ordering the calls to avoid updating exampleSolutionPublicationDate twice.
            this.updateReleaseDate(newExercise.releaseDate);
            this.updateExampleSolutionPublicationDate(newExercise.dueDate);
        } else {
            this.updateExampleSolutionPublicationDate(newExercise.dueDate);
            this.updateReleaseDate(newExercise.releaseDate);
        }
    }

    /**
     * If the programming exercise does not have an id, set the assessmentType to AUTOMATIC and
     * apply the initial date cascade (legacy ngOnChanges fired on the first input pass too).
     */
    ngOnInit(): void {
        this.updateIsImportBasedOnUrl();

        const exercise = this.exercise();
        if (exercise && !exercise.id && !this.isImport) {
            exercise.assessmentType = AssessmentType.AUTOMATIC;
        }
        if (exercise) {
            this.previousExerciseRef = exercise;
            this.applyExerciseDateCascade(exercise);
        }
        this.isAthenaEnabled = this.profileService.isProfileActive(PROFILE_ATHENA);
    }

    private updateIsImportBasedOnUrl() {
        let isImportFromExistingExercise = false;
        let isImportFromFile = false;
        this.urlSubscription = this.activatedRoute.url
            .pipe(
                tap((segments) => {
                    isImportFromExistingExercise = segments.some((segment) => segment.path === 'import');
                    isImportFromFile = segments.some((segment) => segment.path === 'import-from-file');
                }),
            )
            .subscribe(() => {
                this.isImport = isImportFromExistingExercise || isImportFromFile;
            });
    }

    ngAfterViewInit() {
        this.setupDateFieldSubscriptions();
        // Re-subscribe whenever the signal-based picker list changes (replaces legacy QueryList.changes).
        effect(
            () => {
                this.datePickerComponents();
                this.setupDateFieldSubscriptions();
            },
            { injector: this.injector },
        );
    }

    ngOnDestroy() {
        this.datePickerChildrenSubscription?.unsubscribe();
        this.unsubscribeDateFieldSubscriptions();
        this.urlSubscription?.unsubscribe();
    }

    calculateFormStatus() {
        const datePickers = this.datePickerComponents();
        this.formValid = every(datePickers, (picker) => picker?.dateInput()?.valid ?? true);
        this.formEmpty = !every(datePickers, (picker) => {
            if (picker instanceof ProgrammingExerciseTestScheduleDatePickerComponent) {
                return picker.selectedDate;
            }
            return false;
        });
        this.formValidChanges.next(this.formValid);
    }

    setupDateFieldSubscriptions() {
        this.unsubscribeDateFieldSubscriptions();
        const pickers = this.datePickerComponents();
        pickers.forEach((picker) => this.inputfieldSubscriptions.push(picker.dateInput()?.valueChanges?.subscribe(() => setTimeout(() => this.calculateFormStatus()))));
    }

    unsubscribeDateFieldSubscriptions() {
        for (const subscription of this.inputfieldSubscriptions) {
            subscription?.unsubscribe();
        }
    }

    toggleSetTestCaseVisibilityToAfterDueDate() {
        const importOptions = this.importOptions();
        if (importOptions) {
            importOptions.setTestCaseVisibilityToAfterDueDate = !importOptions.setTestCaseVisibilityToAfterDueDate;
        }
    }

    toggleFeedbackRequests() {
        const exercise = this.exercise()!;
        exercise.allowFeedbackRequests = !exercise.allowFeedbackRequests;
        if (exercise.allowFeedbackRequests) {
            exercise.assessmentDueDate = undefined;
            exercise.buildAndTestStudentSubmissionsAfterDueDate = undefined;
        }
    }

    /**
     * Toggles the assessment type between AUTOMATIC (only tests in repo will be run using build plans) and
     * SEMI_AUTOMATIC (After all automatic tests have been run, the tutors will have to make a final manual assessment)
     *
     */
    toggleAssessmentType() {
        const exercise = this.exercise()!;
        if (exercise.assessmentType === AssessmentType.SEMI_AUTOMATIC) {
            exercise.assessmentType = AssessmentType.AUTOMATIC;
            exercise.assessmentDueDate = undefined;
            exercise.allowComplaintsForAutomaticAssessments = false;
            exercise.feedbackSuggestionModule = undefined;
        } else {
            exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
            exercise.allowComplaintsForAutomaticAssessments = false;
            exercise.allowFeedbackRequests = false;
        }
    }

    /**
     * Toggles the value for allowing complaints for automatic assessment between true and false
     */
    toggleComplaintsType() {
        const exercise = this.exercise()!;
        exercise.allowComplaintsForAutomaticAssessments = !exercise.allowComplaintsForAutomaticAssessments;
    }

    /**
     * Toggles the value for allowing complaints for automatic assessment between true and false
     */
    toggleReleaseTests() {
        const exercise = this.exercise()!;
        exercise.releaseTestsWithExampleSolution = !exercise.releaseTestsWithExampleSolution;
    }

    /**
     * Sets the new release date and updates "start date", "due date" and "after due date" if the release date is after them
     * Does not propagate changes to dates other than release date if readOnly is true.
     *
     * @param newReleaseDate The new release date
     */
    updateReleaseDate(newReleaseDate?: dayjs.Dayjs) {
        const exercise = this.exercise()!;
        exercise.releaseDate = newReleaseDate;
        if (this.readOnly()) {
            // Changes from parent component are allowed but no cascading changes should be made in read-only mode.
            return;
        }
        if (this.exerciseService.hasStartDateError(exercise)) {
            this.updateStartDate(newReleaseDate);
            // Will handle due date and example solution
            return;
        }
        const safeStartOrReleaseDate = exercise.startDate ?? newReleaseDate;
        if (this.exerciseService.hasDueDateError(exercise) && safeStartOrReleaseDate) {
            this.updateDueDate(safeStartOrReleaseDate);
        }
        this.updateExampleSolutionPublicationDate(safeStartOrReleaseDate);
    }

    /**
     * Sets the new start date and updates "due date" and "after due date" if the start date is after the due date
     * Does not propagate changes to dates other than start date if readOnly is true.
     *
     * @param newStartDate The new start date
     */
    updateStartDate(newStartDate?: dayjs.Dayjs) {
        const exercise = this.exercise()!;
        exercise.startDate = newStartDate;
        if (this.readOnly()) {
            // Changes from parent component are allowed but no cascading changes should be made in read-only mode.
            return;
        }
        if (this.exerciseService.hasDueDateError(exercise)) {
            this.updateDueDate(newStartDate!);
        }
        this.updateExampleSolutionPublicationDate(newStartDate);
    }

    /**
     * Updates the due Date of the programming exercise
     * @param dueDate the new dueDate
     */
    private updateDueDate(dueDate: dayjs.Dayjs) {
        const exercise = this.exercise()!;
        alert(this.translateService.instant('artemisApp.programmingExercise.timeline.alertNewDueDate'));
        exercise.dueDate = dueDate;

        // If the new due date is after the "After Due Date", then we have to set the "After Due Date" to the new due date
        const afterDue = exercise.buildAndTestStudentSubmissionsAfterDueDate;
        if (afterDue && dueDate.isAfter(afterDue)) {
            exercise.buildAndTestStudentSubmissionsAfterDueDate = dueDate;
            alert(this.translateService.instant('artemisApp.programmingExercise.timeline.alertNewAfterDueDate'));
        }
    }

    /**
     * Updates the example solution publication date of the programming exercise if it is set and not after release or due date.
     * Due date check is not performed if exercise is not included in the grade.
     * This method is a no-op if readOnly is true.
     *
     * @param newReleaseOrDueDate the new exampleSolutionPublicationDate if it is after the current exampleSolutionPublicationDate
     */
    updateExampleSolutionPublicationDate(newReleaseOrDueDate?: dayjs.Dayjs) {
        const exercise = this.exercise()!;
        if (!this.readOnly() && this.exerciseService.hasExampleSolutionPublicationDateError(exercise)) {
            const message =
                newReleaseOrDueDate && dayjs(newReleaseOrDueDate).isSame(exercise.dueDate)
                    ? 'artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsDueDate'
                    : 'artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsReleaseDate';
            alert(this.translateService.instant(message));
            exercise.exampleSolutionPublicationDate = newReleaseOrDueDate;
            if (!newReleaseOrDueDate) {
                exercise.releaseTestsWithExampleSolution = false;
            }
        }
    }
}
