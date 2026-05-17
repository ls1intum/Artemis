import { AfterViewInit, Component, OnChanges, OnDestroy, OnInit, QueryList, SimpleChanges, ViewChildren, computed, effect, inject, input, model, signal } from '@angular/core';
import { PROFILE_ATHENA } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component';
import dayjs, { Dayjs } from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { faCogs, faUserCheck, faUserSlash } from '@fortawesome/free-solid-svg-icons';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Subject, Subscription } from 'rxjs';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from './test-schedule-date-picker/programming-exercise-test-schedule-date-picker.component';
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
import { ExerciseTimeline, TimelineItem } from 'app/shared/exercise-timeline/exercise-timeline';

@Component({
    selector: 'jhi-programming-exercise-timeline',
    templateUrl: './programming-exercise-timeline.component.html',
    styleUrls: ['./test-schedule-date-picker/programming-exercise-test-schedule-picker.scss'],
    imports: [
        ProgrammingExerciseTestScheduleDatePickerComponent,
        FormsModule,
        TranslateDirective,
        HelpIconComponent,
        FaIconComponent,
        NgStyle,
        ExerciseFeedbackSuggestionOptionsComponent,
        ExerciseTimeline,
    ],
})
export class ProgrammingExerciseTimelineComponent implements AfterViewInit, OnDestroy, OnInit, OnChanges {
    private translateService = inject(TranslateService);
    private exerciseService = inject(ExerciseService);
    private profileService = inject(ProfileService);
    private activatedRoute = inject(ActivatedRoute);

    protected readonly AssessmentType = AssessmentType;
    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly faCogs = faCogs;
    protected readonly faUserCheck = faUserCheck;
    protected readonly faUserSlash = faUserSlash;
    protected readonly testTimelineItems: TimelineItem[] = [
        {
            kind: 'required',
            labelStringKey: 'artemisApp.exercise.releaseDate',
            date: signal<Date | undefined>(undefined),
        },
        {
            kind: 'optional',
            labelStringKey: 'artemisApp.exercise.startDate',
            date: signal<Date | undefined>(undefined),
        },
        {
            kind: 'required',
            labelStringKey: 'artemisApp.exercise.dueDate',
            date: signal<Date | undefined>(undefined),
        },
    ];

    releaseDate = model<Dayjs | undefined>();
    startDate = model<Dayjs | undefined>();
    dueDate = model<Dayjs | undefined>();
    buildAndTestStudentSubmissionsAfterDueDate = model<Dayjs | undefined>();
    assessmentDueDate = model<Dayjs | undefined>();
    exampleSolutionPublicationDate = model<Dayjs | undefined>();
    assessmentType = model<AssessmentType>();
    allowFeedbackRequests = model<boolean>();
    isExamMode = input.required<boolean>();
    exercise = input.required<ProgrammingExercise>();
    readOnly = input(false);
    importOptions = input<ImportOptions>();
    isEditFieldDisplayedRecord = input<Record<ProgrammingExerciseInputField, boolean>>();

    isDatePickableForRunningTestsAfterDueDate = signal(false);
    isEnablingToRunTestsAfterDueDateToggleEnabled = computed(() => {
        const isFieldDisplayed = this.isEditFieldDisplayedRecord();
        return (!isFieldDisplayed || isFieldDisplayed.runTestsAfterDueDate) && (this.isExamMode() || !!this.dueDate());
    });
    isDatePickableForSemiAutomaticAssessmentDueDate = signal(false);
    isSemiAutomaticAssessmentToggleEnabled = computed(() => {
        const isFieldDisplayed = this.isEditFieldDisplayedRecord();
        return (!isFieldDisplayed || isFieldDisplayed.assessmentDueDate) && (this.isImport() || !!this.dueDate() || this.isExamMode());
    });
    isDatePickableForExampleSolutionPublicationDate = signal(this.exampleSolutionPublicationDate() !== undefined);
    isExampleSolutionPublicationDateToggleEnabled = computed(() => {
        const isFieldDisplayed = this.isEditFieldDisplayedRecord();
        return (!isFieldDisplayed || isFieldDisplayed.exampleSolutionPublicationDate) && !this.isExamMode();
    });

    @ViewChildren(ProgrammingExerciseTestScheduleDatePickerComponent) datePickerComponents: QueryList<ProgrammingExerciseTestScheduleDatePickerComponent>;

    formValid: boolean;
    formEmpty: boolean;
    formValidChanges = new Subject<boolean>();

    inputfieldSubscriptions: (Subscription | undefined)[] = [];
    datePickerChildrenSubscription?: Subscription;

    isAthenaEnabled: boolean;

    isImport = signal(false);
    private urlSubscription: Subscription;

    constructor() {
        effect(() => {
            if (!this.isEnablingToRunTestsAfterDueDateToggleEnabled()) {
                this.isDatePickableForRunningTestsAfterDueDate.set(false);
                this.buildAndTestStudentSubmissionsAfterDueDate.set(undefined);
            }
        });
        effect(() => {
            if (!this.isDatePickableForRunningTestsAfterDueDate()) {
                this.buildAndTestStudentSubmissionsAfterDueDate.set(undefined);
            }
        });
        effect(() => {
            if (!this.isSemiAutomaticAssessmentToggleEnabled()) {
                this.isDatePickableForSemiAutomaticAssessmentDueDate.set(false);
                this.assessmentDueDate.set(undefined);
            }
        });
        effect(() => {
            if (this.isDatePickableForSemiAutomaticAssessmentDueDate()) {
                this.assessmentType.set(AssessmentType.SEMI_AUTOMATIC);
            } else {
                this.assessmentType.set(AssessmentType.AUTOMATIC);
                this.assessmentDueDate.set(undefined);
            }
        });
        effect(() => {
            if (!this.isExampleSolutionPublicationDateToggleEnabled()) {
                this.isDatePickableForExampleSolutionPublicationDate.set(false);
                this.exampleSolutionPublicationDate.set(undefined);
            }
        });
        effect(() => {
            if (!this.isDatePickableForExampleSolutionPublicationDate()) {
                this.exampleSolutionPublicationDate.set(undefined);
            }
        });
    }

    /**
     * If the programming exercise does not have an id, set the assessment Type to AUTOMATIC
     */
    ngOnInit(): void {
        this.isDatePickableForRunningTestsAfterDueDate.set(this.buildAndTestStudentSubmissionsAfterDueDate() !== undefined);
        this.isDatePickableForSemiAutomaticAssessmentDueDate.set(this.assessmentDueDate() !== undefined);
        this.isDatePickableForExampleSolutionPublicationDate.set(this.exampleSolutionPublicationDate() !== undefined);
        this.updateIsImportBasedOnUrl();

        const exercise = this.exercise();
        if (!exercise.id && !this.isImport()) {
            exercise.assessmentType = AssessmentType.AUTOMATIC;
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
                this.isImport.set(isImportFromExistingExercise || isImportFromFile);
            });
    }

    ngAfterViewInit() {
        this.setupDateFieldSubscriptions();
        this.datePickerChildrenSubscription = this.datePickerComponents.changes.subscribe(() => this.setupDateFieldSubscriptions());
    }

    ngOnChanges(simpleChanges: SimpleChanges) {
        if (simpleChanges.exercise) {
            const newExercise = simpleChanges.exercise.currentValue;
            if (this.exerciseService.hasDueDateError(newExercise)) {
                // Checking for due date errors and ordering the calls to avoid updating exampleSolutionPublicationDate twice.
                this.updateReleaseDate(newExercise.releaseDate);
                this.updateExampleSolutionPublicationDate(newExercise.dueDate);
            } else {
                this.updateExampleSolutionPublicationDate(newExercise.dueDate);
                this.updateReleaseDate(newExercise.releaseDate);
            }
        }
    }

    ngOnDestroy() {
        this.datePickerChildrenSubscription?.unsubscribe();
        this.unsubscribeDateFieldSubscriptions();
        this.urlSubscription?.unsubscribe();
    }

    calculateFormStatus() {
        const datePickers = this.datePickerComponents.toArray();
        this.formValid = every(datePickers, (picker) => picker?.dateInput?.valid ?? true);
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
        this.datePickerComponents
            .toArray()
            .forEach((picker) => this.inputfieldSubscriptions.push(picker.dateInput?.valueChanges?.subscribe(() => setTimeout(() => this.calculateFormStatus()))));
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
        const exercise = this.exercise();
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
        const exercise = this.exercise();
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
        const exercise = this.exercise();
        exercise.allowComplaintsForAutomaticAssessments = !exercise.allowComplaintsForAutomaticAssessments;
    }

    /**
     * Toggles the value for allowing complaints for automatic assessment between true and false
     */
    toggleReleaseTests() {
        const exercise = this.exercise();
        exercise.releaseTestsWithExampleSolution = !exercise.releaseTestsWithExampleSolution;
    }

    /**
     * Sets the new release date and updates "start date", "due date" and "after due date" if the release date is after them
     * Does not propagate changes to dates other than release date if readOnly is true.
     *
     * @param newReleaseDate The new release date
     */
    updateReleaseDate(newReleaseDate?: dayjs.Dayjs) {
        const exercise = this.exercise();
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
        const exercise = this.exercise();
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
        const exercise = this.exercise();
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
        const exercise = this.exercise();
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
