import { Location } from '@angular/common';
import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { faQuestionCircle, faSort, faSortDown, faSortUp, faSquare } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { IssuesMap, ProgrammingExerciseGradingStatistics } from 'app/entities/programming/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming/programming-exercise-test-case.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/programming/static-code-analysis-category.model';
import { SubmissionPolicy, SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { ProgrammingGradingChartsDirective } from 'app/exercises/programming/manage/grading/charts/programming-grading-charts.directive';
import { ProgrammingExerciseGradingService, StaticCodeAnalysisCategoryUpdate } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { ProgrammingExerciseWebsocketService } from 'app/exercises/programming/manage/services/programming-exercise-websocket.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { SubmissionPolicyService } from 'app/exercises/programming/manage/services/submission-policy.service';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { differenceBy as _differenceBy, differenceWith as _differenceWith, intersectionWith as _intersectionWith, unionBy as _unionBy } from 'lodash-es';
import { Observable, Subscription, of, zip } from 'rxjs';
import { catchError, distinctUntilChanged, map, take, tap } from 'rxjs/operators';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';

/**
 * Describes the editableField
 */
export enum EditableField {
    WEIGHT = 'weight',
    BONUS_MULTIPLIER = 'bonusMultiplier',
    BONUS_POINTS = 'bonusPoints',
    VISIBILITY = 'visibility',
    PENALTY = 'penalty',
    MAX_PENALTY = 'maxPenalty',
    STATE = 'state',
}
export enum ChartFilterType {
    TEST_CASES,
    CATEGORIES,
}

enum TestCaseView {
    TABLE,
    CHART,
    BACKUP,
    SAVE_VALUES,
}

const DefaultFieldValues: { [key: string]: number } = {
    [EditableField.WEIGHT]: 1,
    [EditableField.BONUS_MULTIPLIER]: 1,
    [EditableField.BONUS_POINTS]: 0,
    [EditableField.PENALTY]: 0,
    [EditableField.MAX_PENALTY]: 0,
};

export type GradingTab = 'test-cases' | 'code-analysis' | 'submission-policy' | 'feedback-analysis';
export type Table = 'testCases' | 'codeAnalysis';

@Component({
    selector: 'jhi-programming-exercise-configure-grading',
    templateUrl: './programming-exercise-configure-grading.component.html',
    styleUrls: ['./programming-exercise-configure-grading.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [ProgrammingExerciseTaskService],
})
export class ProgrammingExerciseConfigureGradingComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    readonly EditableField = EditableField;
    readonly CategoryState = StaticCodeAnalysisCategoryState;
    readonly Visibility = Visibility;

    course: Course;
    programmingExercise: ProgrammingExercise;
    testCaseSubscription: Subscription;
    testCaseChangedSubscription: Subscription;
    paramSub: Subscription;

    testCasesValue: ProgrammingExerciseTestCase[] = [];
    changedTestCaseIds: number[] = [];
    // We have to separate these test cases in order to separate the table and chart presentation if the table is filtered by the chart
    filteredTestCasesForTable: ProgrammingExerciseTestCase[] = [];
    filteredTestCasesForCharts: ProgrammingExerciseTestCase[] = [];
    // backup in order to restore the setting before filtering by chart interaction
    backupTestCases: ProgrammingExerciseTestCase[] = [];

    testCasePoints: { [testCase: string]: number } = {};
    testCasePointsRelative: { [testCase: string]: number } = {};

    // The event emitters emit this value in order to indicate this component to reset the corresponding table view
    readonly RESET_TABLE = ProgrammingGradingChartsDirective.RESET_TABLE;
    readonly chartFilterType = ChartFilterType;
    readonly ProgrammingLanguage = ProgrammingLanguage;

    // We have to separate these test cases in order to separate the table and chart presentation if the table is filtered by the chart
    staticCodeAnalysisCategoriesForTable: StaticCodeAnalysisCategory[] = [];
    staticCodeAnalysisCategoriesForCharts: StaticCodeAnalysisCategory[] = [];
    // backup in order to restore the setting before filtering by chart interaction
    backupStaticCodeAnalysisCategories: StaticCodeAnalysisCategory[] = [];
    changedCategoryIds: number[] = [];

    buildAfterDueDateActive: boolean;
    isReleasedAndHasResults: boolean;
    showInactiveValue = false;
    isSaving = false;
    isLoading = false;
    // This flag means that the grading config were edited, but no submission run was triggered yet.
    hasUpdatedGradingConfig = false;
    activeTab: GradingTab;

    gradingStatistics?: ProgrammingExerciseGradingStatistics;
    gradingStatisticsObservable: Observable<ProgrammingExerciseGradingStatistics>;
    maxIssuesPerCategory = 0;

    categoryStateList = Object.entries(StaticCodeAnalysisCategoryState).map(([name, value]) => ({ value, name }));

    testCaseColors = {};
    categoryColors: { [key: string]: string } = {};
    totalWeight = 0;

    submissionPolicy?: SubmissionPolicy;
    hadPolicyBefore: boolean;

    // Icons
    faQuestionCircle = faQuestionCircle;
    faSquare = faSquare;

    /**
     * Returns the value of testcases
     */
    get testCases() {
        return this.testCasesValue;
    }

    get activeTestCases() {
        return this.testCases.filter(({ active }) => active);
    }

    get hasUnsavedChanges() {
        return this.programmingExerciseTaskService.hasUnsavedChanges();
    }

    /**
     * Sets value of the testcases
     * @param testCases the test cases which should be set
     */
    set testCases(testCases: ProgrammingExerciseTestCase[]) {
        this.testCasesValue = testCases;
        this.updateTestCaseFilter();
        this.updateTestPoints();
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
        private accountService: AccountService,
        private gradingService: ProgrammingExerciseGradingService,
        private programmingExerciseService: ProgrammingExerciseService,
        private programmingExerciseSubmissionPolicyService: SubmissionPolicyService,
        private programmingExerciseWebsocketService: ProgrammingExerciseWebsocketService,
        private programmingExerciseTaskService: ProgrammingExerciseTaskService,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private translateService: TranslateService,
        private location: Location,
        private router: Router,
        private courseManagementService: CourseManagementService,
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
            this.courseManagementService.find(params['courseId']).subscribe((courseResponse) => (this.course = courseResponse.body!));

            if (this.programmingExercise == undefined || this.programmingExercise.id !== exerciseId) {
                if (this.testCaseSubscription) {
                    this.testCaseSubscription.unsubscribe();
                }
                if (this.testCaseChangedSubscription) {
                    this.testCaseChangedSubscription.unsubscribe();
                }

                const loadExercise = this.programmingExerciseService.find(exerciseId).pipe(
                    map((res) => res.body!),
                    tap((exercise) => (this.programmingExercise = exercise)),
                    tap(() => {
                        if (this.programmingExercise.staticCodeAnalysisEnabled) {
                            this.loadStaticCodeAnalysisCategories();
                        }
                        this.hadPolicyBefore = this.programmingExercise.submissionPolicy !== undefined;
                    }),
                    catchError(() => of(null)),
                );

                const loadExerciseTestCaseState = this.getExerciseTestCaseState(exerciseId).pipe(
                    tap((releaseState) => {
                        this.hasUpdatedGradingConfig = releaseState.testCasesChanged;
                        this.isReleasedAndHasResults = releaseState.released && releaseState.hasStudentResult;
                        this.buildAfterDueDateActive = !!releaseState.buildAndTestStudentSubmissionsAfterDueDate;
                    }),
                    catchError(() => of(null)),
                );

                this.loadStatistics(exerciseId);

                zip(loadExercise, loadExerciseTestCaseState)
                    .pipe(take(1))
                    .subscribe(() => {
                        // This subscription e.g. adds new tests to the table that were just created.
                        this.subscribeForTestCaseUpdates();
                        // This subscription is used to determine if the programming exercise's properties necessitate build runs after the test cases are changed.
                        this.subscribeForExerciseTestCasesChangedUpdates();
                        this.isLoading = false;
                    });
            } else {
                this.isLoading = false;
            }

            const gradingTabs: GradingTab[] = ['test-cases', 'code-analysis', 'submission-policy', 'feedback-analysis'];
            if (gradingTabs.includes(params['tab'])) {
                this.selectTab(params['tab']);
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
        this.testCaseSubscription = this.gradingService
            .subscribeForTestCases(this.programmingExercise.id!)
            .pipe(
                tap((testCases: ProgrammingExerciseTestCase[]) => {
                    this.testCases = testCases;
                }),
                tap(() => this.loadStatistics(this.programmingExercise.id!)),
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
            .getTestCaseState(this.programmingExercise.id!)
            .pipe(tap((testCasesChanged: boolean) => (this.hasUpdatedGradingConfig = testCasesChanged)))
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
     * @param editedTestCase    the edited test case;
     * @param field             the edited field;
     */
    updateEditedField(editedTestCase: ProgrammingExerciseTestCase, field: EditableField) {
        return (newValue: any) => {
            newValue = this.checkFieldValue(newValue, editedTestCase[field as keyof ProgrammingExerciseTestCase], field);
            // Only mark the testcase as changed, if the field has changed.
            if (newValue !== editedTestCase[field as keyof ProgrammingExerciseTestCase]) {
                this.changedTestCaseIds = this.changedTestCaseIds.includes(editedTestCase.id!) ? this.changedTestCaseIds : [...this.changedTestCaseIds, editedTestCase.id!];
                this.updateAllTestCaseViewsAfterEditing(editedTestCase, field, newValue);
                this.updateTestPoints(editedTestCase, field, newValue);
            }
            return newValue;
        };
    }

    /**
     * Update a field of a sca category in the component state (does not persist the value on the server!).
     * Adds the currently edited category to the list of unsaved changes.
     *
     * @param editedCategory    the edited category;
     * @param field             the edited field;
     */
    updateEditedCategoryField(editedCategory: StaticCodeAnalysisCategory, field: EditableField) {
        return (newValue: any) => {
            newValue = this.checkFieldValue(newValue, editedCategory[field as keyof StaticCodeAnalysisCategory], field);
            // Only mark the category as changed, if the field has changed.
            if (newValue !== editedCategory[field as keyof StaticCodeAnalysisCategory]) {
                this.changedCategoryIds = this.changedCategoryIds.includes(editedCategory.id) ? this.changedCategoryIds : [...this.changedCategoryIds, editedCategory.id];
                this.updateStaticCodeAnalysisCategories(editedCategory, field, newValue);
            }
            return newValue;
        };
    }

    /**
     * Check and validate the new value for an editable field. Optionally applies the default value for the field.
     * @param newValue  The new edited value
     * @param oldValue  The previous value
     * @param field     The edited field
     */
    checkFieldValue(newValue: any, oldValue: any, field: EditableField) {
        // Don't allow an empty string as a value!
        if (newValue === '') {
            newValue = DefaultFieldValues[field];
        }
        if (typeof oldValue === 'number') {
            newValue = Number(newValue);
            if (isNaN(newValue)) {
                newValue = oldValue;
            }
        }

        return newValue;
    }

    saveCategories() {
        this.isSaving = true;

        this.backupStaticCodeAnalysisCategories = this.backupStaticCodeAnalysisCategories.map((category) =>
            category.state === StaticCodeAnalysisCategoryState.Graded ? category : { ...category, penalty: 0, maxPenalty: 0 },
        );

        const categoriesToUpdate = _intersectionWith(
            this.backupStaticCodeAnalysisCategories,
            this.changedCategoryIds,
            (codeAnalysisCategory: StaticCodeAnalysisCategory, id: number) => codeAnalysisCategory.id === id,
        );
        const categoryUpdates = categoriesToUpdate.map((category) => StaticCodeAnalysisCategoryUpdate.from(category));

        const saveCodeAnalysis = this.gradingService.updateCodeAnalysisCategories(this.programmingExercise.id!, categoryUpdates).pipe(
            tap((updatedCategories: StaticCodeAnalysisCategory[]) => {
                // From successfully updated categories from dirty checking list.
                this.changedCategoryIds = _differenceWith(
                    this.changedCategoryIds,
                    updatedCategories,
                    (categoryId: number, category: StaticCodeAnalysisCategory) => category.id === categoryId,
                );

                // Generate the new list of categories.
                this.staticCodeAnalysisCategoriesForTable = _unionBy(updatedCategories, this.backupStaticCodeAnalysisCategories, 'id');
                this.setChartAndBackupCategoryView();

                // Find out if there are test cases that were not updated, show an error.
                const notUpdatedCategories = _differenceBy(categoriesToUpdate, updatedCategories, 'id');
                if (notUpdatedCategories.length) {
                    this.alertService.error(`artemisApp.programmingExercise.configureGrading.categories.couldNotBeUpdated`, {
                        categories: notUpdatedCategories.map((c) => c.name).join(', '),
                    });
                } else {
                    this.alertService.success(`artemisApp.programmingExercise.configureGrading.categories.updated`);
                }
            }),
            catchError(() => {
                this.alertService.error(`artemisApp.programmingExercise.configureGrading.categories.couldNotBeUpdated`, {
                    categories: categoriesToUpdate.map((c) => c.name).join(', '),
                });
                return of(null);
            }),
        );

        saveCodeAnalysis.subscribe(() => {
            this.isSaving = false;
        });
    }

    importCategories(sourceExerciseId: number) {
        this.isSaving = true;

        this.gradingService
            .importCategoriesFromExercise(this.programmingExercise.id!, sourceExerciseId)
            .pipe(
                tap((newConfiguration: StaticCodeAnalysisCategory[]) => {
                    this.staticCodeAnalysisCategoriesForTable = newConfiguration;
                    this.setChartAndBackupCategoryView();

                    this.alertService.success('artemisApp.programmingExercise.configureGrading.categories.importSuccessful', { exercise: sourceExerciseId });
                }),
                catchError(() => {
                    this.alertService.error(`artemisApp.programmingExercise.configureGrading.categories.importFailed`, { exercise: sourceExerciseId });
                    return of(null);
                }),
            )
            .subscribe(() => (this.isSaving = false));
    }

    resetCategories() {
        this.isSaving = true;
        this.gradingService
            .resetCategories(this.programmingExercise.id!)
            .pipe(
                tap((categories: StaticCodeAnalysisCategory[]) => {
                    this.alertService.success(`artemisApp.programmingExercise.configureGrading.categories.resetSuccessful`);
                    this.staticCodeAnalysisCategoriesForTable = categories;
                    this.setChartAndBackupCategoryView();
                    this.loadStatistics(this.programmingExercise.id!);
                }),
                catchError(() => {
                    this.alertService.error(`artemisApp.programmingExercise.configureGrading.categories.resetFailed`);
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isSaving = false;
                this.changedCategoryIds = [];
            });
    }

    /**
     * Removes the submission policy of the programming exercise.
     */
    removeSubmissionPolicy() {
        this.isSaving = true;
        this.programmingExerciseSubmissionPolicyService
            .removeSubmissionPolicyFromProgrammingExercise(this.programmingExercise.id!)
            .pipe(
                tap(() => {
                    this.programmingExercise.submissionPolicy = undefined;
                    this.hadPolicyBefore = false;
                }),
                catchError(() => {
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isSaving = false;
            });
    }

    /**
     * Adds the submission policy of the programming exercise.
     */
    addSubmissionPolicy() {
        this.isSaving = true;
        this.programmingExerciseSubmissionPolicyService
            .addSubmissionPolicyToProgrammingExercise(this.programmingExercise.submissionPolicy!, this.programmingExercise.id!)
            .pipe(
                tap((submissionPolicy: SubmissionPolicy) => {
                    this.programmingExercise.submissionPolicy = submissionPolicy;
                    this.hadPolicyBefore = true;
                }),
                catchError(() => {
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isSaving = false;
            });
    }

    /**
     * Updates the submission policy of the programming exercise.
     */
    updateSubmissionPolicy() {
        if (this.programmingExercise.submissionPolicy?.type === SubmissionPolicyType.NONE && this.hadPolicyBefore) {
            this.removeSubmissionPolicy();
            return;
        } else if (!this.hadPolicyBefore && this.programmingExercise.submissionPolicy?.type !== SubmissionPolicyType.NONE) {
            this.addSubmissionPolicy();
            return;
        }
        this.isSaving = true;
        this.programmingExerciseSubmissionPolicyService
            .updateSubmissionPolicyToProgrammingExercise(this.programmingExercise.submissionPolicy!, this.programmingExercise.id!)
            .pipe(
                catchError(() => {
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isSaving = false;
            });
    }

    /**
     * Enable/Disable the submission policy of the programming exercise.
     */
    toggleSubmissionPolicy() {
        this.isSaving = true;
        const deactivateSaving = () => {
            this.isSaving = false;
        };
        if (this.programmingExercise.submissionPolicy!.active) {
            this.programmingExerciseSubmissionPolicyService
                .disableSubmissionPolicyOfProgrammingExercise(this.programmingExercise.id!)
                .pipe(
                    tap(() => {
                        this.programmingExercise!.submissionPolicy!.active = false;
                    }),
                )
                .subscribe(deactivateSaving);
        } else {
            this.programmingExerciseSubmissionPolicyService
                .enableSubmissionPolicyOfProgrammingExercise(this.programmingExercise.id!)
                .pipe(
                    tap(() => {
                        this.programmingExercise!.submissionPolicy!.active = true;
                    }),
                )
                .subscribe(deactivateSaving);
        }
    }

    /**
     * Executes filtering on all available test cases with the specified params.
     */
    updateTestCaseFilter() {
        this.filteredTestCasesForTable = !this.showInactiveValue && this.testCases ? this.testCases.filter(({ active }) => active) : this.testCases;
        this.filteredTestCasesForCharts = this.filteredTestCasesForTable;
        this.backupTestCases = this.filteredTestCasesForTable;
    }

    /**
     * Calculates the rounded points awarded for passing each test
     */
    updateTestPoints(editedTestCase?: ProgrammingExerciseTestCase, field?: EditableField, newValue?: any) {
        if (!this.testCases) {
            return;
        }

        const maxPoints = this.programmingExercise.maxPoints!;
        if (!this.totalWeight || !editedTestCase || !field || field === EditableField.WEIGHT || newValue === undefined) {
            this.testCasePoints = {};
            this.testCasePointsRelative = {};
            this.totalWeight = this.activeTestCases.reduce((sum, testCase) => sum + testCase.weight!, 0);
            this.activeTestCases.forEach((testCase) => {
                const points = (this.totalWeight > 0 ? (testCase.weight! * testCase.bonusMultiplier!) / this.totalWeight : 0) * maxPoints + (testCase.bonusPoints ?? 0);
                this.testCasePoints[testCase.testName!] = roundValueSpecifiedByCourseSettings(points, this.course);
                const relativePoints = (points / maxPoints) * 100;
                this.testCasePointsRelative[testCase.testName!] = roundValueSpecifiedByCourseSettings(relativePoints, this.course);
            });
        } else {
            const editedTestCaseNewValue = { ...editedTestCase, [field]: newValue };
            const points =
                (this.totalWeight > 0 ? (editedTestCaseNewValue.weight! * editedTestCaseNewValue.bonusMultiplier!) / this.totalWeight : 0) * maxPoints +
                (editedTestCaseNewValue.bonusPoints ?? 0);
            this.testCasePoints[editedTestCaseNewValue.testName!] = roundValueSpecifiedByCourseSettings(points, this.course);
            const relativePoints = (points / maxPoints) * 100;
            this.testCasePointsRelative[editedTestCaseNewValue.testName!] = roundValueSpecifiedByCourseSettings(relativePoints, this.course);
        }
    }

    /**
     * Checks if there are unsaved test cases or there was no submission run after the test cases were changed.
     * Provides a fitting text for the confirm.
     */
    canDeactivate() {
        if (!this.changedTestCaseIds.length && (!this.isReleasedAndHasResults || !this.hasUpdatedGradingConfig)) {
            return true;
        }
        const warning = this.changedTestCaseIds.length
            ? this.translateService.instant('pendingChanges')
            : this.translateService.instant('artemisApp.programmingExercise.configureGrading.updatedGradingConfig');
        return confirm(warning);
    }

    /**
     * Switch tabs
     * @param tab The target tab
     */
    selectTab(tab: GradingTab) {
        const parentUrl = this.router.url.substring(0, this.router.url.lastIndexOf('/'));
        this.location.replaceState(`${parentUrl}/${tab}`);
        this.activeTab = tab;
    }

    /**
     * Get the issues map for a specific category
     * @param categoryName The name of the category
     */
    getIssuesMap(categoryName: string): IssuesMap | undefined {
        return this.gradingStatistics?.categoryIssuesMap ? this.gradingStatistics.categoryIssuesMap[categoryName] : undefined;
    }

    tableSorts = { testCases: [{ prop: 'testName', dir: 'asc' }], codeAnalysis: [{ prop: 'name', dir: 'asc' }] };
    onSort(table: Table, config: any) {
        this.tableSorts[table] = config.sorts;
    }

    /**
     * Returns the correct sort-icon for the specified property
     * @param table The table of the property
     * @param prop The sorted property
     */
    iconForSortPropField(table: Table, prop: string) {
        const propSort = this.tableSorts[table].find((e) => e.prop === prop);
        if (!propSort) {
            return faSort;
        }
        return propSort.dir === 'asc' ? faSortUp : faSortDown;
    }

    valForState = (s: StaticCodeAnalysisCategoryState) => (s === StaticCodeAnalysisCategoryState.Inactive ? 0 : s === StaticCodeAnalysisCategoryState.Feedback ? 1 : 2);

    /**
     * Comparator function for the state of a sca category.
     */
    compareCategoryState = (_: any, __: any, rowA: StaticCodeAnalysisCategory, rowB: StaticCodeAnalysisCategory) => {
        return this.valForState(rowA.state) - this.valForState(rowB.state);
    };

    /**
     * Comparator function for the penalty of a sca category.
     */
    comparePenalty = (_: any, __: any, rowA: StaticCodeAnalysisCategory, rowB: StaticCodeAnalysisCategory) => {
        const valForPenalty = (c: StaticCodeAnalysisCategory) => this.valForState(c.state) + (c.state === StaticCodeAnalysisCategoryState.Graded ? c.penalty : 0);
        return valForPenalty(rowA) - valForPenalty(rowB);
    };

    /**
     * Comparator function for the max-penalty of a sca category.
     */
    compareMaxPenalty = (_: any, __: any, rowA: StaticCodeAnalysisCategory, rowB: StaticCodeAnalysisCategory) => {
        const valForMaxPenalty = (c: StaticCodeAnalysisCategory) => this.valForState(c.state) + (c.state === StaticCodeAnalysisCategoryState.Graded ? c.maxPenalty : 0);
        return valForMaxPenalty(rowA) - valForMaxPenalty(rowB);
    };

    /**
     * Comparator function for the detected issues of a sca category.
     */
    compareDetectedIssues = (_: any, __: any, rowA: StaticCodeAnalysisCategory, rowB: StaticCodeAnalysisCategory) => {
        const issuesA = this.getIssuesMap(rowA.name);
        const issuesB = this.getIssuesMap(rowB.name);
        const totalIssuesA = Object.values(issuesA ?? {}).reduce((sum, n) => sum + n, 0);
        const totalIssuesB = Object.values(issuesB ?? {}).reduce((sum, n) => sum + n, 0);
        return totalIssuesA !== totalIssuesB ? totalIssuesA - totalIssuesB : this.compareCategoryState(_, __, rowA, rowB);
    };

    /**
     * Load the static code analysis categories
     */
    private loadStaticCodeAnalysisCategories() {
        this.gradingService
            .getCodeAnalysisCategories(this.programmingExercise.id!)
            .pipe(
                tap((categories) => {
                    this.staticCodeAnalysisCategoriesForTable = categories;
                    this.setChartAndBackupCategoryView();
                }),
                catchError(() => of(null)),
            )
            .subscribe();
    }

    /**
     * Load the statistics for this exercise and calculate the
     * maximum number of issues in one category
     * @param exerciseId The current exercise id
     */
    private loadStatistics(exerciseId: number) {
        this.gradingStatisticsObservable = this.gradingService.getGradingStatistics(exerciseId);

        this.gradingStatisticsObservable.subscribe((statistics) => {
            this.gradingStatistics = statistics;
            this.maxIssuesPerCategory = 0;
            if (statistics?.categoryIssuesMap) {
                // calculate the maximum number of issues in one category
                for (const issuesMap of Object.values(statistics?.categoryIssuesMap)) {
                    const maxIssues = Object.keys(issuesMap).reduce((max, issues) => Math.max(max, parseInt(issues, 10)), 0);
                    if (maxIssues > this.maxIssuesPerCategory) {
                        this.maxIssuesPerCategory = maxIssues;
                    }
                }
            }
        });
    }

    getEventValue(event: Event) {
        const element = event.target as HTMLInputElement;
        return element.value;
    }

    /**
     * Auxiliary method that handles the filtering of a table if the user clicks a specific test case or sca category in the respective chart
     * @param testCaseId the id of the test case that is clicked
     * @param filterType enum indicating whether test cases or static code analysis categories are filtered
     */
    filterByChart(testCaseId: number, filterType: ChartFilterType): void {
        const filterFunction = (part: any) => part.id === testCaseId;
        if (filterType === ChartFilterType.TEST_CASES) {
            this.filteredTestCasesForTable = this.backupTestCases;
            if (testCaseId !== this.RESET_TABLE) {
                this.filteredTestCasesForTable = this.filteredTestCasesForTable.filter(filterFunction);
            }
        } else {
            this.staticCodeAnalysisCategoriesForTable = this.backupStaticCodeAnalysisCategories;
            if (testCaseId !== this.RESET_TABLE) {
                this.staticCodeAnalysisCategoriesForTable = this.staticCodeAnalysisCategoriesForTable.filter(filterFunction);
            }
        }
    }

    /**
     * Updates all different views on the test cases after a test case is edited by the user in the table
     * @param editedTestCase the edited test case
     * @param field the field that is edited
     * @param newValue the newly inserted value
     */
    private updateAllTestCaseViewsAfterEditing(editedTestCase: ProgrammingExerciseTestCase, field: EditableField, newValue: any): void {
        const testCaseDisplayTypes = [TestCaseView.TABLE, TestCaseView.CHART, TestCaseView.BACKUP, TestCaseView.SAVE_VALUES];
        testCaseDisplayTypes.forEach((testCaseDisplayType) => this.updateTestCases(editedTestCase, field, newValue, testCaseDisplayType));
    }

    /**
     * Auxiliary method in order to prevent further code duplication for the updating of the test case views;
     * @param editedTestCase the edited test case
     * @param field the field that is edited
     * @param newValue the newly inserted value
     * @param displayType enum indicating which view is updated
     */
    private updateTestCases(editedTestCase: ProgrammingExerciseTestCase, field: EditableField, newValue: any, displayType: TestCaseView): void {
        const mapFunction = (testCase: ProgrammingExerciseTestCase) => (testCase.id !== editedTestCase.id ? testCase : { ...testCase, [field]: newValue });
        switch (displayType) {
            case TestCaseView.TABLE:
                this.filteredTestCasesForTable = this.filteredTestCasesForTable.map(mapFunction);
                break;
            case TestCaseView.CHART:
                this.filteredTestCasesForCharts = this.filteredTestCasesForCharts.map(mapFunction);
                break;
            case TestCaseView.BACKUP:
                this.backupTestCases = this.backupTestCases.map(mapFunction);
                break;
            case TestCaseView.SAVE_VALUES:
                this.testCasesValue = this.testCases.map(mapFunction);
                break;
        }
    }

    /**
     * Auxiliary method that updates all different views on the static code analysis categories if a category is updated by the user in the table
     * @param editedCategory the edited category
     * @param field the field that is edited
     * @param newValue the newly inserted value
     */
    private updateStaticCodeAnalysisCategories(editedCategory: StaticCodeAnalysisCategory, field: EditableField, newValue: any): void {
        const filterFunction = (category: StaticCodeAnalysisCategory) => (category.id !== editedCategory.id ? category : { ...category, [field]: newValue });

        this.staticCodeAnalysisCategoriesForTable = this.staticCodeAnalysisCategoriesForTable.map(filterFunction);
        this.backupStaticCodeAnalysisCategories = this.backupStaticCodeAnalysisCategories.map(filterFunction);
        this.staticCodeAnalysisCategoriesForCharts = this.backupStaticCodeAnalysisCategories;
    }

    /**
     * Auxiliary method that sets the chart and backup view on the static code analysis categories
     */
    private setChartAndBackupCategoryView(): void {
        this.staticCodeAnalysisCategoriesForCharts = this.staticCodeAnalysisCategoriesForTable;
        this.backupStaticCodeAnalysisCategories = this.staticCodeAnalysisCategoriesForTable;
    }
}
