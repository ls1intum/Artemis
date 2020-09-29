import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router } from '@angular/router';
import { of, Subscription, zip } from 'rxjs';
import { catchError, distinctUntilChanged, map, take, tap } from 'rxjs/operators';
import { differenceBy as _differenceBy, differenceWith as _differenceWith, intersectionWith as _intersectionWith, unionBy as _unionBy } from 'lodash';
import { AlertService } from 'app/core/alert/alert.service';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExerciseWebsocketService } from 'app/exercises/programming/manage/services/programming-exercise-websocket.service';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseTestCaseService, ProgrammingExerciseTestCaseUpdate } from 'app/exercises/programming/manage/services/programming-exercise-test-case.service';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { Location } from '@angular/common';
import { Mapping } from './codeAnalysisMapping';

/**
 * Describes the editableField
 */
export enum EditableField {
    WEIGHT = 'weight',
    BONUS_MULTIPLIER = 'bonusMultiplier',
    BONUS_POINTS = 'bonusPoints',
    PENALTY = 'penalty',
    MAX_PENALTY = 'maxPenalty',
    STATE = 'state',
}

@Component({
    selector: 'jhi-programming-exercise-configure-grading',
    templateUrl: './programming-exercise-configure-grading.component.html',
    styleUrls: ['./programming-exercise-configure-grading.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ProgrammingExerciseConfigureGradingComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    EditableField = EditableField;

    courseId: number;
    exercise: ProgrammingExercise;
    testCaseSubscription: Subscription;
    testCaseChangedSubscription: Subscription;
    paramSub: Subscription;

    testCasesValue: ProgrammingExerciseTestCase[] = [];
    changedTestCaseIds: number[] = [];
    filteredTestCases: ProgrammingExerciseTestCase[] = [];

    staticCodeAnalysisCategories: StaticCodeAnalysisCategory[] = [];
    changedCategoryIds: number[] = [];

    buildAfterDueDateActive: boolean;
    isReleasedAndHasResults: boolean;
    showInactiveValue = false;
    isSaving = false;
    isLoading = false;
    // This flag means that the test cases were edited, but no submission run was triggered yet.
    hasUpdatedTestCases = false;
    activeTab: string;

    categoryStateList = Object.entries(StaticCodeAnalysisCategoryState).map(([name, value]) => ({ value, name }));

    /**
     * Returns the value of testcases
     */
    get testCases() {
        return this.testCasesValue;
    }

    /**
     * Sets value of the testcases
     * @param testCases the test cases which should be set
     */
    set testCases(testCases: ProgrammingExerciseTestCase[]) {
        this.testCasesValue = testCases;
        this.updateTestCaseFilter();
    }

    /**
     * Returns the value of showInactive
     */
    get showInactive() {
        return this.showInactiveValue;
    }

    /**
     * Sets the value of showInactive
     * @param showInactive the value which should be set
     */
    set showInactive(showInactive: boolean) {
        this.showInactiveValue = showInactive;
        this.updateTestCaseFilter();
    }

    constructor(
        private testCaseService: ProgrammingExerciseTestCaseService,
        private programmingExerciseService: ProgrammingExerciseService,
        private programmingExerciseWebsocketService: ProgrammingExerciseWebsocketService,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private translateService: TranslateService,
        private location: Location,
        private router: Router,
    ) {}

    /**
     * Subscribes to the route params to get the current exerciseId.
     * Uses the exerciseId to subscribe to the newest value of the exercise's test cases.
     *
     * Also checks if a change guard needs to be activated when the test cases where saved.
     */
    ngOnInit(): void {
        this.paramSub = this.route.params.pipe(distinctUntilChanged()).subscribe((params) => {
            this.isLoading = true;
            const exerciseId = Number(params['exerciseId']);
            this.courseId = Number(params['courseId']);

            if (this.exercise == null || this.exercise.id !== exerciseId) {
                if (this.testCaseSubscription) {
                    this.testCaseSubscription.unsubscribe();
                }
                if (this.testCaseChangedSubscription) {
                    this.testCaseChangedSubscription.unsubscribe();
                }

                const loadExercise = this.programmingExerciseService.find(exerciseId).pipe(
                    map((res) => res.body!),
                    tap((exercise) => (this.exercise = exercise)),
                    tap(() => {
                        if (!this.exercise.staticCodeAnalysisEnabled) {
                            this.selectTab('test-cases');
                        }
                    }),
                    catchError(() => of(null)),
                );

                const loadExerciseTestCaseState = this.getExerciseTestCaseState(exerciseId).pipe(
                    tap((releaseState) => {
                        this.hasUpdatedTestCases = releaseState.testCasesChanged;
                        this.isReleasedAndHasResults = releaseState.released && releaseState.hasStudentResult;
                        this.buildAfterDueDateActive = !!releaseState.buildAndTestStudentSubmissionsAfterDueDate;
                    }),
                    catchError(() => of(null)),
                );

                zip(loadExercise, loadExerciseTestCaseState)
                    .pipe(take(1))
                    .subscribe(() => {
                        // This subscription e.g. adds new new tests to the table that were just created.
                        this.subscribeForTestCaseUpdates();
                        // This subscription is used to determine if the programming exercise's properties necessitate build runs after the test cases are changed.
                        this.subscribeForExerciseTestCasesChangedUpdates();
                        this.loadCodeAnalysisMapping();
                        this.isLoading = false;
                    });
            } else {
                this.isLoading = false;
            }

            if (params['tab'] === 'test-cases' || params['tab'] === 'code-analysis') {
                this.activeTab = params['tab'];
            } else {
                this.selectTab('test-cases');
            }
        });
    }

    /**
     * If there is an existing subscription, unsubscribe
     */
    ngOnDestroy(): void {
        if (this.testCaseSubscription) {
            this.testCaseSubscription.unsubscribe();
        }
        if (this.testCaseChangedSubscription) {
            this.testCaseChangedSubscription.unsubscribe();
        }
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    /**
     *  Subscribes to test case updates
     *  updates the list of test cases
     */
    private subscribeForTestCaseUpdates() {
        if (this.testCaseSubscription) {
            this.testCaseSubscription.unsubscribe();
        }
        this.testCaseSubscription = this.testCaseService
            .subscribeForTestCases(this.exercise.id!)
            .pipe(
                tap((testCases: ProgrammingExerciseTestCase[]) => {
                    this.testCases = testCases;
                }),
            )
            .subscribe();
    }

    /**
     *  Subscribes to test case changes
     *  checks if the test cases have changed
     */
    private subscribeForExerciseTestCasesChangedUpdates() {
        if (this.testCaseChangedSubscription) {
            this.testCaseChangedSubscription.unsubscribe();
        }
        this.testCaseChangedSubscription = this.programmingExerciseWebsocketService
            .getTestCaseState(this.exercise.id!)
            .pipe(tap((testCasesChanged: boolean) => (this.hasUpdatedTestCases = testCasesChanged)))
            .subscribe();
    }

    /**
     * Checks if the exercise is released and has at least one student result.
     */
    getExerciseTestCaseState(exerciseId: number) {
        return this.programmingExerciseService.getProgrammingExerciseTestCaseState(exerciseId).pipe(map(({ body }) => body!));
    }

    /**
     * Update a field of a test case in the component state (does not persist the value on the server!).
     * Adds the currently edited test case to the list of unsaved changes.
     *
     * @param newValue          of updated field;
     * @param editedTestCase    the edited test case;
     * @param field             the edited field;
     */
    updateEditedField(newValue: any, editedTestCase: ProgrammingExerciseTestCase, field: EditableField) {
        // Don't allow an empty string as a value!
        if (!newValue) {
            return;
        }
        if (typeof editedTestCase[field] === 'number') {
            newValue = Number(newValue);
        }
        // If the weight has not changed, don't do anything besides closing the input.
        if (newValue === editedTestCase[field]) {
            return;
        }
        this.changedTestCaseIds = this.changedTestCaseIds.includes(editedTestCase.id) ? this.changedTestCaseIds : [...this.changedTestCaseIds, editedTestCase.id];
        this.testCases = this.testCases.map((testCase) => (testCase.id !== editedTestCase.id ? testCase : { ...testCase, [field]: newValue }));
    }

    /**
     * Update a field of a sca category in the component state (does not persist the value on the server!).
     * Adds the currently edited category to the list of unsaved changes.
     *
     * @param newValue          of updated field;
     * @param editedCategory    the edited category;
     * @param field             the edited field;
     */
    updateEditedCategoryField(newValue: any, editedCategory: StaticCodeAnalysisCategory, field: EditableField) {
        // Don't allow an empty string as a value!
        if (!newValue) {
            return;
        }
        if (typeof editedCategory[field] === 'number') {
            newValue = Number(newValue);
        }
        // If the field has not changed, don't do anything
        if (newValue === editedCategory[field]) {
            return;
        }
        this.changedCategoryIds = this.changedCategoryIds.includes(editedCategory.id) ? this.changedCategoryIds : [...this.changedCategoryIds, editedCategory.id];
        this.staticCodeAnalysisCategories = this.staticCodeAnalysisCategories.map((category) =>
            category.id !== editedCategory.id ? category : { ...category, [field]: newValue },
        );
    }

    /**
     * Save the unsaved (edited) changes of the test cases.
     */
    saveChanges() {
        this.isSaving = true;

        const testCasesToUpdate = _intersectionWith(this.testCases, this.changedTestCaseIds, (testCase: ProgrammingExerciseTestCase, id: number) => testCase.id === id);
        const testCaseUpdates = testCasesToUpdate.map((testCase) => ProgrammingExerciseTestCaseUpdate.from(testCase));

        this.testCaseService
            .updateTestCase(this.exercise.id!, testCaseUpdates)
            .pipe(
                tap((updatedTestCases: ProgrammingExerciseTestCase[]) => {
                    // From successfully updated test cases from dirty checking list.
                    this.changedTestCaseIds = _differenceWith(this.changedTestCaseIds, updatedTestCases, (id: number, testCase: ProgrammingExerciseTestCase) => testCase.id === id);

                    // Generate the new list of test cases with the updated weights and notify the test case service.
                    const newTestCases = _unionBy(updatedTestCases, this.testCases, 'id');
                    this.testCaseService.notifyTestCases(this.exercise.id!, newTestCases);

                    // Find out if there are test cases that were not updated, show an error.
                    const notUpdatedTestCases = _differenceBy(testCasesToUpdate, updatedTestCases, 'id');
                    if (notUpdatedTestCases.length) {
                        this.alertService.error(`artemisApp.programmingExercise.manageTestCases.testCasesCouldNotBeUpdated`, { testCases: notUpdatedTestCases });
                    } else {
                        this.alertService.success(`artemisApp.programmingExercise.manageTestCases.testCasesUpdated`);
                    }
                }),
                catchError(() => {
                    this.alertService.error(`artemisApp.programmingExercise.manageTestCases.testCasesCouldNotBeUpdated`, { testCases: testCasesToUpdate });
                    return of(undefined);
                }),
            )
            .subscribe(() => {
                this.isSaving = false;
            });
    }

    /**
     * Toggle the after due date of the test case related to the provided row of the datatable.
     * @param rowIndex
     */
    toggleAfterDueDate(rowIndex: number) {
        const testCase = this.filteredTestCases[rowIndex];
        this.changedTestCaseIds = this.changedTestCaseIds.includes(testCase.id!) ? this.changedTestCaseIds : [...this.changedTestCaseIds, testCase.id!];
        this.testCases = this.testCases.map((t) => (t.id === testCase.id ? { ...t, afterDueDate: !t.afterDueDate } : t));
    }

    /**
     * Reset all test cases.
     */
    resetChanges() {
        this.isSaving = true;
        this.testCaseService
            .reset(this.exercise.id!)
            .pipe(
                tap((testCases: ProgrammingExerciseTestCase[]) => {
                    this.alertService.success(`artemisApp.programmingExercise.manageTestCases.resetSuccessful`);
                    this.testCaseService.notifyTestCases(this.exercise.id!, testCases);
                }),
                catchError(() => {
                    this.alertService.error(`artemisApp.programmingExercise.manageTestCases.resetFailed`);
                    return of(undefined);
                }),
            )
            .subscribe(() => {
                this.isSaving = false;
                this.changedTestCaseIds = [];
            });
    }

    /**
     * Executes filtering on all availabile test cases with the specified params.
     */
    updateTestCaseFilter = () => {
        this.filteredTestCases = !this.showInactiveValue && this.testCases ? this.testCases.filter(({ active }) => active) : this.testCases;
    };

    /**
     * Makes inactive test cases grey.
     *
     * @param row
     */
    getRowClass(row: ProgrammingExerciseTestCase) {
        return !row.active ? 'test-case--inactive' : '';
    }

    /**
     * Checks if there are unsaved test cases or there was no submission run after the test cases were changed.
     * Provides a fitting text for the confirm.
     */
    canDeactivate() {
        if (!this.changedTestCaseIds.length && (!this.isReleasedAndHasResults || !this.hasUpdatedTestCases)) {
            return true;
        }
        const warning = this.changedTestCaseIds.length
            ? this.translateService.instant('pendingChanges')
            : this.translateService.instant('artemisApp.programmingExercise.manageTestCases.updatedTestCases');
        return confirm(warning);
    }

    loadCodeAnalysisMapping() {
        this.staticCodeAnalysisCategories = Mapping;
    }

    selectTab(tab: string) {
        const parentUrl = this.router.url.substring(0, this.router.url.lastIndexOf('/'));
        this.location.replaceState(`${parentUrl}/${tab}`);
        this.activeTab = tab;
    }
}
