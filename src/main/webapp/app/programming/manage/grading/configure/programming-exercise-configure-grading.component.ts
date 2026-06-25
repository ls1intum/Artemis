import { Location, NgClass, NgTemplateOutlet } from '@angular/common';
import { Component, OnDestroy, OnInit, ViewEncapsulation, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { faSquare } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course, isCommunicationEnabled } from 'app/course/shared/entities/course.model';
import { IssuesMap, ProgrammingExerciseGradingStatistics } from 'app/programming/shared/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTestCase, Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/programming/shared/entities/static-code-analysis-category.model';
import { SubmissionPolicy, SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { ProgrammingGradingChartsDirective } from 'app/programming/manage/grading/charts/programming-grading-charts.directive';
import { ProgrammingExerciseGradingService, StaticCodeAnalysisCategoryUpdate } from 'app/programming/manage/services/programming-exercise-grading.service';
import { ProgrammingExerciseWebsocketService } from 'app/programming/manage/services/programming-exercise-websocket.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { SubmissionPolicyUpdateComponent } from 'app/exercise/submission-policy/submission-policy-update.component';
import { ComponentCanDeactivate } from 'app/foundation/guard/can-deactivate.model';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { differenceBy as _differenceBy, differenceWith as _differenceWith, intersectionWith as _intersectionWith, unionBy as _unionBy } from 'lodash-es';
import { Observable, Subscription, of, zip } from 'rxjs';
import { catchError, distinctUntilChanged, map, take, tap } from 'rxjs/operators';
import { ProgrammingExerciseTaskService } from 'app/programming/manage/grading/tasks/programming-exercise-task.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProgrammingExerciseConfigureGradingStatusComponent } from '../configure-status/programming-exercise-configure-grading-status.component';
import { ProgrammingExerciseConfigureGradingActionsComponent } from '../configure-actions/programming-exercise-configure-grading-actions.component';
import { ProgrammingExerciseGradingSubmissionPolicyConfigurationActionsComponent } from '../configure-submission-policy/programming-exercise-grading-submission-policy-configuration-actions.component';
import { ProgrammingExerciseGradingTasksTableComponent } from '../tasks/programming-exercise-grading-tasks-table/programming-exercise-grading-tasks-table.component';
import { TestCaseDistributionChartComponent } from '../charts/test-case-distribution-chart.component';
import { ProgrammingExerciseGradingTableActionsComponent } from '../table-actions/programming-exercise-grading-table-actions.component';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { TableEditableFieldComponent } from 'app/shared-ui/table/editable-field/table-editable-field.component';
import { CategoryIssuesChartComponent } from '../charts/category-issues-chart.component';
import { ScaCategoryDistributionChartComponent } from '../charts/sca-category-distribution-chart.component';
import { FeedbackAnalysisComponent } from '../feedback-analysis/feedback-analysis.component';

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
    imports: [
        NgClass,
        TranslateDirective,
        NgTemplateOutlet,
        ProgrammingExerciseConfigureGradingStatusComponent,
        ProgrammingExerciseConfigureGradingActionsComponent,
        ProgrammingExerciseGradingSubmissionPolicyConfigurationActionsComponent,
        SubmissionPolicyUpdateComponent,
        ProgrammingExerciseGradingTasksTableComponent,
        TestCaseDistributionChartComponent,
        ProgrammingExerciseGradingTableActionsComponent,
        FaIconComponent,
        FormsModule,
        TableEditableFieldComponent,
        CategoryIssuesChartComponent,
        ScaCategoryDistributionChartComponent,
        FeedbackAnalysisComponent,
        TableViewComponent,
    ],
})
export class ProgrammingExerciseConfigureGradingComponent implements OnInit, OnDestroy, ComponentCanDeactivate {
    private gradingService = inject(ProgrammingExerciseGradingService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private programmingExerciseSubmissionPolicyService = inject(SubmissionPolicyService);
    private programmingExerciseWebsocketService = inject(ProgrammingExerciseWebsocketService);
    private programmingExerciseTaskService = inject(ProgrammingExerciseTaskService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private location = inject(Location);
    private router = inject(Router);
    private courseManagementService = inject(CourseManagementService);

    readonly EditableField = EditableField;
    readonly CategoryState = StaticCodeAnalysisCategoryState;
    readonly Visibility = Visibility;

    readonly course = signal<Course>(undefined!);
    // Backed by a signal but exposed via a getter/setter facade: the template binds `programmingExercise.prop`
    // directly and several subscribe handlers mutate nested properties in place, so we rebuild the reference
    // via commitProgrammingExercise() after deep mutations to keep template reads reactive under zoneless CD.
    private readonly _programmingExercise = signal<ProgrammingExercise>(undefined!);
    get programmingExercise(): ProgrammingExercise {
        return this._programmingExercise();
    }
    set programmingExercise(value: ProgrammingExercise) {
        this._programmingExercise.set(value);
    }
    private commitProgrammingExercise(): void {
        this._programmingExercise.update((exercise) => Object.assign(new ProgrammingExercise(undefined, undefined), exercise));
    }
    testCaseSubscription: Subscription;
    testCaseChangedSubscription: Subscription;
    paramSub: Subscription;

    readonly testCasesValue = signal<ProgrammingExerciseTestCase[]>([]);
    readonly changedTestCaseIds = signal<number[]>([]);
    // We have to separate these test cases in order to separate the table and chart presentation if the table is filtered by the chart
    filteredTestCasesForTable: ProgrammingExerciseTestCase[] = [];
    readonly filteredTestCasesForCharts = signal<ProgrammingExerciseTestCase[]>([]);
    // backup in order to restore the setting before filtering by chart interaction
    backupTestCases: ProgrammingExerciseTestCase[] = [];

    testCasePoints: { [testCase: string]: number } = {};
    testCasePointsRelative: { [testCase: string]: number } = {};

    // The event emitters emit this value in order to indicate this component to reset the corresponding table view
    readonly RESET_TABLE = ProgrammingGradingChartsDirective.RESET_TABLE;
    readonly chartFilterType = ChartFilterType;
    readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly isCommunicationEnabled = isCommunicationEnabled;

    // We have to separate these test cases in order to separate the table and chart presentation if the table is filtered by the chart
    readonly staticCodeAnalysisCategoriesForTable = signal<StaticCodeAnalysisCategory[]>([]);
    readonly staticCodeAnalysisCategoriesForCharts = signal<StaticCodeAnalysisCategory[]>([]);
    // backup in order to restore the setting before filtering by chart interaction
    backupStaticCodeAnalysisCategories: StaticCodeAnalysisCategory[] = [];
    readonly changedCategoryIds = signal<number[]>([]);

    buildAfterDueDateActive: boolean;
    readonly isReleasedAndHasResults = signal<boolean>(undefined!);
    showInactiveValue = false;
    readonly isSaving = signal(false);
    readonly isLoading = signal(false);
    // This flag means that the grading config were edited, but no submission run was triggered yet.
    readonly hasUpdatedGradingConfig = signal(false);
    readonly activeTab = signal<GradingTab>(undefined!);

    readonly gradingStatistics = signal<ProgrammingExerciseGradingStatistics | undefined>(undefined);
    readonly gradingStatisticsObservable = signal<Observable<ProgrammingExerciseGradingStatistics>>(undefined!);
    readonly maxIssuesPerCategory = signal(0);

    categoryStateList = Object.entries(StaticCodeAnalysisCategoryState).map(([name, value]) => ({ value, name }));

    testCaseColors = {};
    categoryColors: { [key: string]: string } = {};
    totalWeight = 0;

    submissionPolicy?: SubmissionPolicy;
    readonly hadPolicyBefore = signal<boolean>(undefined!);

    // Icons
    faSquare = faSquare;

    readonly categoryTemplate = viewChild<CellTemplateRef<StaticCodeAnalysisCategory>>('categoryTemplate');
    readonly stateTemplate = viewChild<CellTemplateRef<StaticCodeAnalysisCategory>>('stateTemplate');
    readonly penaltyTemplate = viewChild<CellTemplateRef<StaticCodeAnalysisCategory>>('penaltyTemplate');
    readonly maxPenaltyTemplate = viewChild<CellTemplateRef<StaticCodeAnalysisCategory>>('maxPenaltyTemplate');
    readonly detectedIssuesTemplate = viewChild<CellTemplateRef<StaticCodeAnalysisCategory>>('detectedIssuesTemplate');

    readonly codeAnalysisTableOptions: TableViewOptions = {
        lazy: false,
        pageSize: 20,
        showSearch: false,
        hidePageSizeOptions: true,
        striped: true,
        initialSortField: 'name',
        initialSortOrder: 1,
    };

    readonly codeAnalysisColumns = computed<ColumnDef<StaticCodeAnalysisCategory>[]>(() => [
        { field: 'name', header: 'Category', sort: true, templateRef: this.categoryTemplate() },
        {
            field: 'state',
            header: 'State',
            sort: true,
            width: '130px',
            headerTooltip: 'artemisApp.programmingExercise.configureGrading.help.state',
            templateRef: this.stateTemplate(),
            sortComparator: this.compareCategoryState,
        },
        {
            field: 'penalty',
            header: 'Penalty',
            sort: true,
            headerTooltip: 'artemisApp.programmingExercise.configureGrading.help.penalty',
            templateRef: this.penaltyTemplate(),
            sortComparator: this.comparePenalty,
        },
        {
            field: 'maxPenalty',
            header: 'Max Penalty',
            sort: true,
            headerTooltip: 'artemisApp.programmingExercise.configureGrading.help.maxPenalty',
            templateRef: this.maxPenaltyTemplate(),
            sortComparator: this.compareMaxPenalty,
        },
        {
            // Virtual field — does not exist on StaticCodeAnalysisCategory; used only as the sort-dispatch
            // key so PrimeNG knows which comparator to call. The cell template uses cell.data, not cell.value.
            field: 'detectedIssues',
            header: 'Detected Issues',
            sort: true,
            headerTooltip: 'artemisApp.programmingExercise.configureGrading.help.detectedIssues',
            templateRef: this.detectedIssuesTemplate(),
            sortComparator: this.compareDetectedIssues,
        },
    ]);

    /**
     * Returns the value of testcases
     */
    get testCases() {
        return this.testCasesValue();
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
        this.testCasesValue.set(testCases);
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

    /**
     * Subscribes to the route params to get the current exerciseId.
     * Uses the exerciseId to subscribe to the newest value of the exercise's test cases.
     *
     * Also checks if a change guard needs to be activated when the test cases where saved.
     */
    ngOnInit(): void {
        this.paramSub = this.route.params.pipe(distinctUntilChanged()).subscribe((params) => {
            this.isLoading.set(true);
            const exerciseId = Number(params['exerciseId']);
            this.courseManagementService.find(params['courseId']).subscribe((courseResponse) => {
                this.course.set(courseResponse.body!);
            });

            if (this.programmingExercise == undefined || this.programmingExercise.id !== exerciseId) {
                this.testCaseSubscription?.unsubscribe();
                this.testCaseChangedSubscription?.unsubscribe();

                const loadExercise = this.programmingExerciseService.find(exerciseId).pipe(
                    map((res) => res.body!),
                    tap((exercise) => (this.programmingExercise = exercise)),
                    tap(() => {
                        if (this.programmingExercise.staticCodeAnalysisEnabled) {
                            this.loadStaticCodeAnalysisCategories();
                        }
                        this.hadPolicyBefore.set(this.programmingExercise.submissionPolicy !== undefined);
                    }),
                    catchError(() => of(null)),
                );

                const loadExerciseTestCaseState = this.getExerciseTestCaseState(exerciseId).pipe(
                    tap((releaseState) => {
                        this.hasUpdatedGradingConfig.set(releaseState.testCasesChanged);
                        this.isReleasedAndHasResults.set(releaseState.released && releaseState.hasStudentResult);
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
                        this.isLoading.set(false);
                    });
            } else {
                this.isLoading.set(false);
            }

            const gradingTabs: GradingTab[] = ['test-cases', 'code-analysis', 'submission-policy', 'feedback-analysis'];
            if (gradingTabs.includes(params['tab'])) {
                this.selectTab(params['tab']);
            } else {
                this.selectTab('test-cases');
            }
            // The route params subscription runs outside Angular change detection; schedule CD so the view reflects isLoading/activeTab.
        });
    }

    /**
     * If there is an existing subscription, unsubscribe
     */
    ngOnDestroy(): void {
        this.testCaseSubscription?.unsubscribe();
        this.testCaseChangedSubscription?.unsubscribe();
        this.paramSub?.unsubscribe();
    }

    /**
     *  Subscribes to test case updates
     *  updates the list of test cases
     */
    private subscribeForTestCaseUpdates() {
        this.testCaseSubscription?.unsubscribe();
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
        this.testCaseChangedSubscription?.unsubscribe();
        this.testCaseChangedSubscription = this.programmingExerciseWebsocketService
            .getTestCaseState(this.programmingExercise.id!)
            .pipe(
                tap((testCasesChanged: boolean) => {
                    this.hasUpdatedGradingConfig.set(testCasesChanged);
                }),
            )
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
                this.changedTestCaseIds.update((ids) => (ids.includes(editedTestCase.id!) ? ids : [...ids, editedTestCase.id!]));
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
                this.changedCategoryIds.set(this.changedCategoryIds().includes(editedCategory.id) ? this.changedCategoryIds() : [...this.changedCategoryIds(), editedCategory.id]);
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
        this.isSaving.set(true);

        this.backupStaticCodeAnalysisCategories = this.backupStaticCodeAnalysisCategories.map((category) =>
            category.state === StaticCodeAnalysisCategoryState.Graded ? category : { ...category, penalty: 0, maxPenalty: 0 },
        );

        const categoriesToUpdate = _intersectionWith(
            this.backupStaticCodeAnalysisCategories,
            this.changedCategoryIds(),
            (codeAnalysisCategory: StaticCodeAnalysisCategory, id: number) => codeAnalysisCategory.id === id,
        );
        const categoryUpdates = categoriesToUpdate.map((category) => StaticCodeAnalysisCategoryUpdate.from(category));

        const saveCodeAnalysis = this.gradingService.updateCodeAnalysisCategories(this.programmingExercise.id!, categoryUpdates).pipe(
            tap((updatedCategories: StaticCodeAnalysisCategory[]) => {
                // From successfully updated categories from dirty checking list.
                this.changedCategoryIds.set(
                    _differenceWith(this.changedCategoryIds(), updatedCategories, (categoryId: number, category: StaticCodeAnalysisCategory) => category.id === categoryId),
                );

                // Generate the new list of categories.
                this.staticCodeAnalysisCategoriesForTable.set(_unionBy(updatedCategories, this.backupStaticCodeAnalysisCategories, 'id'));
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
            this.isSaving.set(false);
        });
    }

    importCategories(sourceExerciseId: number) {
        this.isSaving.set(true);

        this.gradingService
            .importCategoriesFromExercise(this.programmingExercise.id!, sourceExerciseId)
            .pipe(
                tap((newConfiguration: StaticCodeAnalysisCategory[]) => {
                    this.staticCodeAnalysisCategoriesForTable.set(newConfiguration);
                    this.setChartAndBackupCategoryView();

                    this.alertService.success('artemisApp.programmingExercise.configureGrading.categories.importSuccessful', { exercise: sourceExerciseId });
                }),
                catchError(() => {
                    this.alertService.error(`artemisApp.programmingExercise.configureGrading.categories.importFailed`, { exercise: sourceExerciseId });
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isSaving.set(false);
            });
    }

    resetCategories() {
        this.isSaving.set(true);
        this.gradingService
            .resetCategories(this.programmingExercise.id!)
            .pipe(
                tap((categories: StaticCodeAnalysisCategory[]) => {
                    this.alertService.success(`artemisApp.programmingExercise.configureGrading.categories.resetSuccessful`);
                    this.staticCodeAnalysisCategoriesForTable.set(categories);
                    this.setChartAndBackupCategoryView();
                    this.loadStatistics(this.programmingExercise.id!);
                }),
                catchError(() => {
                    this.alertService.error(`artemisApp.programmingExercise.configureGrading.categories.resetFailed`);
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isSaving.set(false);
                this.changedCategoryIds.set([]);
            });
    }

    /**
     * Removes the submission policy of the programming exercise.
     */
    removeSubmissionPolicy() {
        this.isSaving.set(true);
        this.programmingExerciseSubmissionPolicyService
            .removeSubmissionPolicyFromProgrammingExercise(this.programmingExercise.id!)
            .pipe(
                tap(() => {
                    this.programmingExercise.submissionPolicy = undefined;
                    this.commitProgrammingExercise();
                    this.hadPolicyBefore.set(false);
                }),
                catchError(() => {
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isSaving.set(false);
            });
    }

    /**
     * Adds the submission policy of the programming exercise.
     */
    addSubmissionPolicy() {
        this.isSaving.set(true);
        this.programmingExerciseSubmissionPolicyService
            .addSubmissionPolicyToProgrammingExercise(this.programmingExercise.submissionPolicy!, this.programmingExercise.id!)
            .pipe(
                tap((submissionPolicy: SubmissionPolicy) => {
                    this.programmingExercise.submissionPolicy = submissionPolicy;
                    this.commitProgrammingExercise();
                    this.hadPolicyBefore.set(true);
                }),
                catchError(() => {
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isSaving.set(false);
            });
    }

    /**
     * Updates the submission policy of the programming exercise.
     */
    updateSubmissionPolicy() {
        if (this.programmingExercise.submissionPolicy?.type === SubmissionPolicyType.NONE && this.hadPolicyBefore()) {
            this.removeSubmissionPolicy();
            return;
        } else if (!this.hadPolicyBefore() && this.programmingExercise.submissionPolicy?.type !== SubmissionPolicyType.NONE) {
            this.addSubmissionPolicy();
            return;
        }
        this.isSaving.set(true);
        this.programmingExerciseSubmissionPolicyService
            .updateSubmissionPolicyToProgrammingExercise(this.programmingExercise.submissionPolicy!, this.programmingExercise.id!)
            .pipe(
                catchError(() => {
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isSaving.set(false);
            });
    }

    /**
     * Enable/Disable the submission policy of the programming exercise.
     */
    toggleSubmissionPolicy() {
        this.isSaving.set(true);
        const deactivateSaving = () => {
            this.isSaving.set(false);
        };
        if (this.programmingExercise.submissionPolicy!.active) {
            this.programmingExerciseSubmissionPolicyService
                .disableSubmissionPolicyOfProgrammingExercise(this.programmingExercise.id!)
                .pipe(
                    tap(() => {
                        this.programmingExercise!.submissionPolicy!.active = false;
                        this.commitProgrammingExercise();
                    }),
                )
                .subscribe(deactivateSaving);
        } else {
            this.programmingExerciseSubmissionPolicyService
                .enableSubmissionPolicyOfProgrammingExercise(this.programmingExercise.id!)
                .pipe(
                    tap(() => {
                        this.programmingExercise!.submissionPolicy!.active = true;
                        this.commitProgrammingExercise();
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
        this.filteredTestCasesForCharts.set(this.filteredTestCasesForTable);
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
                this.testCasePoints[testCase.testName!] = roundValueSpecifiedByCourseSettings(points, this.course());
                const relativePoints = (points / maxPoints) * 100;
                this.testCasePointsRelative[testCase.testName!] = roundValueSpecifiedByCourseSettings(relativePoints, this.course());
            });
        } else {
            const editedTestCaseNewValue = { ...editedTestCase, [field]: newValue };
            const points =
                (this.totalWeight > 0 ? (editedTestCaseNewValue.weight! * editedTestCaseNewValue.bonusMultiplier!) / this.totalWeight : 0) * maxPoints +
                (editedTestCaseNewValue.bonusPoints ?? 0);
            this.testCasePoints[editedTestCaseNewValue.testName!] = roundValueSpecifiedByCourseSettings(points, this.course());
            const relativePoints = (points / maxPoints) * 100;
            this.testCasePointsRelative[editedTestCaseNewValue.testName!] = roundValueSpecifiedByCourseSettings(relativePoints, this.course());
        }
    }

    /**
     * Checks if there are unsaved test cases or there was no submission run after the test cases were changed.
     * Provides a fitting text for the confirm.
     */
    canDeactivate() {
        if (!this.changedTestCaseIds().length && (!this.isReleasedAndHasResults() || !this.hasUpdatedGradingConfig())) {
            return true;
        }
        const warning = this.changedTestCaseIds().length
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
        this.activeTab.set(tab);
    }

    /**
     * Get the issues map for a specific category
     * @param categoryName The name of the category
     */
    getIssuesMap(categoryName: string): IssuesMap | undefined {
        const categoryIssuesMap = this.gradingStatistics()?.categoryIssuesMap;
        return categoryIssuesMap ? categoryIssuesMap[categoryName] : undefined;
    }

    // ─── SCA category sort comparators ───────────────────────────────────────────
    // Arrow functions so `this` is captured correctly when the references are
    // passed to ColumnDef.sortComparator.

    /** Maps a category state to a numeric rank: Inactive(0) < Feedback(1) < Graded(2). */
    private readonly valForState = (s: StaticCodeAnalysisCategoryState): number =>
        s === StaticCodeAnalysisCategoryState.Inactive ? 0 : s === StaticCodeAnalysisCategoryState.Feedback ? 1 : 2;

    /** Sorts by semantic state order instead of alphabetical string value. */
    private readonly compareCategoryState = (rowA: StaticCodeAnalysisCategory, rowB: StaticCodeAnalysisCategory): number =>
        this.valForState(rowA.state) - this.valForState(rowB.state);

    /**
     * Sorts by state first; within Graded rows, sorts by penalty value.
     * Non-Graded rows show "N/A" text so their penalty number is meaningless for ordering.
     */
    private readonly comparePenalty = (rowA: StaticCodeAnalysisCategory, rowB: StaticCodeAnalysisCategory): number => {
        const val = (c: StaticCodeAnalysisCategory) => this.valForState(c.state) + (c.state === StaticCodeAnalysisCategoryState.Graded ? c.penalty : 0);
        return val(rowA) - val(rowB);
    };

    /**
     * Sorts by state first; within Graded rows, sorts by maxPenalty value.
     */
    private readonly compareMaxPenalty = (rowA: StaticCodeAnalysisCategory, rowB: StaticCodeAnalysisCategory): number => {
        const val = (c: StaticCodeAnalysisCategory) => this.valForState(c.state) + (c.state === StaticCodeAnalysisCategoryState.Graded ? c.maxPenalty : 0);
        return val(rowA) - val(rowB);
    };

    /**
     * Sorts by total detected issue count (sum of all issue-count buckets in the issuesMap).
     * Uses category state as a tiebreaker.
     */
    private readonly compareDetectedIssues = (rowA: StaticCodeAnalysisCategory, rowB: StaticCodeAnalysisCategory): number => {
        const totalIssues = (row: StaticCodeAnalysisCategory) => Object.values(this.getIssuesMap(row.name) ?? {}).reduce((sum, n) => sum + n, 0);
        const diff = totalIssues(rowA) - totalIssues(rowB);
        return diff !== 0 ? diff : this.compareCategoryState(rowA, rowB);
    };

    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Load the static code analysis categories
     */
    private loadStaticCodeAnalysisCategories() {
        this.gradingService
            .getCodeAnalysisCategories(this.programmingExercise.id!)
            .pipe(
                tap((categories) => {
                    this.staticCodeAnalysisCategoriesForTable.set(categories);
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
        this.gradingStatisticsObservable.set(this.gradingService.getGradingStatistics(exerciseId));

        this.gradingStatisticsObservable().subscribe((statistics) => {
            this.gradingStatistics.set(statistics);
            this.maxIssuesPerCategory.set(0);
            if (statistics?.categoryIssuesMap) {
                // calculate the maximum number of issues in one category
                for (const issuesMap of Object.values(statistics?.categoryIssuesMap)) {
                    const maxIssues = Object.keys(issuesMap).reduce((max, issues) => Math.max(max, parseInt(issues, 10)), 0);
                    if (maxIssues > this.maxIssuesPerCategory()) {
                        this.maxIssuesPerCategory.set(maxIssues);
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
            this.staticCodeAnalysisCategoriesForTable.set(this.backupStaticCodeAnalysisCategories);
            if (testCaseId !== this.RESET_TABLE) {
                this.staticCodeAnalysisCategoriesForTable.set(this.staticCodeAnalysisCategoriesForTable().filter(filterFunction));
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
                this.filteredTestCasesForCharts.update((testCases) => testCases.map(mapFunction));
                break;
            case TestCaseView.BACKUP:
                this.backupTestCases = this.backupTestCases.map(mapFunction);
                break;
            case TestCaseView.SAVE_VALUES:
                this.testCasesValue.set(this.testCases.map(mapFunction));
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

        this.staticCodeAnalysisCategoriesForTable.set(this.staticCodeAnalysisCategoriesForTable().map(filterFunction));
        this.backupStaticCodeAnalysisCategories = this.backupStaticCodeAnalysisCategories.map(filterFunction);
        this.staticCodeAnalysisCategoriesForCharts.set(this.backupStaticCodeAnalysisCategories);
    }

    /**
     * Auxiliary method that sets the chart and backup view on the static code analysis categories
     */
    private setChartAndBackupCategoryView(): void {
        this.staticCodeAnalysisCategoriesForCharts.set(this.staticCodeAnalysisCategoriesForTable());
        this.backupStaticCodeAnalysisCategories = this.staticCodeAnalysisCategoriesForTable();
    }
}
