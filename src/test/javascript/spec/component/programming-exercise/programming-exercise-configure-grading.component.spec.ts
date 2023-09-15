import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { NgbModal, NgbModalRef, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExerciseGradingStatistics } from 'app/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { CategoryIssuesChartComponent } from 'app/exercises/programming/manage/grading/charts/category-issues-chart.component';
import { ScaCategoryDistributionChartComponent } from 'app/exercises/programming/manage/grading/charts/sca-category-distribution-chart.component';
import { TestCaseDistributionChartComponent } from 'app/exercises/programming/manage/grading/charts/test-case-distribution-chart.component';
import { TestCasePassedBuildsChartComponent } from 'app/exercises/programming/manage/grading/charts/test-case-passed-builds-chart.component';
import { ProgrammingExerciseConfigureGradingActionsComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading-actions.component';
import { ProgrammingExerciseConfigureGradingStatusComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading-status.component';
import {
    ChartFilterType,
    EditableField,
    ProgrammingExerciseConfigureGradingComponent,
    Table,
} from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading.component';
import { ProgrammingExerciseGradingSubmissionPolicyConfigurationActionsComponent } from 'app/exercises/programming/manage/grading/programming-exercise-grading-submission-policy-configuration-actions.component';
import { ProgrammingExerciseGradingTableActionsComponent } from 'app/exercises/programming/manage/grading/programming-exercise-grading-table-actions.component';
import { ProgrammingExerciseGradingService, StaticCodeAnalysisCategoryUpdate } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { ProgrammingExerciseWebsocketService } from 'app/exercises/programming/manage/services/programming-exercise-websocket.service';
import { ProgrammingExerciseService, ProgrammingExerciseTestCaseStateDTO } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingBuildRunService } from 'app/exercises/programming/participate/programming-build-run.service';
import { ProgrammingExerciseReEvaluateButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-re-evaluate-button.component';
import { ProgrammingExerciseTriggerAllButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-trigger-all-button.component';
import { SubmissionPolicyUpdateComponent } from 'app/exercises/shared/submission-policy/submission-policy-update.component';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Subject, of } from 'rxjs';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { MockProgrammingBuildRunService } from '../../helpers/mocks/service/mock-programming-build-run.service';
import { MockProgrammingExerciseGradingService } from '../../helpers/mocks/service/mock-programming-exercise-grading.service';
import { MockProgrammingExerciseWebsocketService } from '../../helpers/mocks/service/mock-programming-exercise-websocket.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { expectElementToBeEnabled, getElement } from '../../helpers/utils/general.utils';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseGradingTasksTableComponent } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-grading-tasks-table.component';

describe('ProgrammingExerciseConfigureGradingComponent', () => {
    let comp: ProgrammingExerciseConfigureGradingComponent;
    let fixture: ComponentFixture<ProgrammingExerciseConfigureGradingComponent>;
    let debugElement: DebugElement;

    let route: ActivatedRoute;
    let gradingService: ProgrammingExerciseGradingService;
    let programmingExerciseService: ProgrammingExerciseService;
    let modalService: NgbModal;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    let updateTestCasesStub: jest.SpyInstance;
    let updateCategoriesStub: jest.SpyInstance;
    let resetCategoriesStub: jest.SpyInstance;
    let testCasesChangedStub: jest.SpyInstance;
    let getExerciseTestCaseStateStub: jest.SpyInstance;
    let loadExerciseStub: jest.SpyInstance;
    let loadStatisticsStub: jest.SpyInstance;
    let importCategoriesFromExerciseStub: jest.SpyInstance;
    let programmingExerciseWebsocketService: ProgrammingExerciseWebsocketService;

    let routeSubject: Subject<Params>;
    let testCasesChangedSubject: Subject<boolean>;
    let getExerciseTestCaseStateSubject: Subject<{ body: ProgrammingExerciseTestCaseStateDTO }>;

    const tableEditingInput = '.table-editable-field__input';
    const saveTableButton = '#save-table-button';
    const resetTableButton = '#reset-table-button';
    const testCasesNoUnsavedChanges = '#test-case-status-no-unsaved-changes';
    const testCasesUpdated = '#test-case-status-updated';
    const testCasesNoUpdated = '#test-case-status-no-updated';
    const codeAnalysisTableId = '#codeAnalysisTable';

    const exerciseId = 1;
    const exercise = {
        id: exerciseId,
        staticCodeAnalysisEnabled: true,
        maxPoints: 42,
        programmingLanguage: ProgrammingLanguage.JAVA,
    } as ProgrammingExercise;
    const testCases = [
        {
            id: 1,
            testName: 'testBubbleSort',
            active: true,
            weight: 1,
            bonusMultiplier: 1,
            bonusPoints: 0,
            visibility: Visibility.Always,
        },
        {
            id: 2,
            testName: 'testMergeSort',
            active: true,
            weight: 1,
            bonusMultiplier: 1,
            bonusPoints: 0,
            visibility: Visibility.AfterDueDate,
        },
        {
            id: 3,
            testName: 'otherTest',
            active: false,
            weight: 1,
            bonusMultiplier: 1,
            bonusPoints: 0,
            visibility: Visibility.Always,
        },
        {
            id: 4,
            testName: 'invisibleTestToStudents',
            active: true,
            weight: 1,
            bonusMultiplier: 1,
            bonusPoints: 0,
            visibility: Visibility.Never,
        },
    ] as ProgrammingExerciseTestCase[];
    const codeAnalysisCategories1 = [
        {
            id: 1,
            name: 'Bad Practice',
            state: StaticCodeAnalysisCategoryState.Graded,
            penalty: 1,
            maxPenalty: 10,
        },
        {
            id: 2,
            name: 'Styling',
            state: StaticCodeAnalysisCategoryState.Feedback,
            penalty: 0,
            maxPenalty: 0,
        },
    ] as StaticCodeAnalysisCategory[];
    const gradingStatistics = {
        numParticipations: 5,
        testCaseStatsMap: {
            testBubbleSort: { numPassed: 2, numFailed: 3 },
            testMergeSort: { numPassed: 1, numFailed: 4 },
            otherTest: { numPassed: 1, numFailed: 0 },
        },
        categoryIssuesMap: {
            'Bad Practice': { 1: 2, 2: 2, 3: 1 },
            Styling: { 0: 3, 2: 1, 5: 1 },
        },
    } as ProgrammingExerciseGradingStatistics;

    const getExerciseTestCasteStateDTO = (released: boolean, hasStudentResult: boolean, testCasesChanged: boolean, buildAndTestStudentSubmissionsAfterDueDate?: dayjs.Dayjs) => ({
        body: {
            released,
            hasStudentResult,
            testCasesChanged,
            buildAndTestStudentSubmissionsAfterDueDate,
        },
    });

    const getSaveButton = () => {
        return getElement(debugElement, saveTableButton);
    };

    const getResetButton = () => {
        return getElement(debugElement, resetTableButton);
    };

    const getNoUnsavedChangesBadge = () => {
        return getElement(debugElement, testCasesNoUnsavedChanges);
    };

    const getUpdatedTestCaseBadge = () => {
        return getElement(debugElement, testCasesUpdated);
    };

    const getNoUpdatedTestCaseBadge = () => {
        return getElement(debugElement, testCasesNoUpdated);
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule, MockModule(TranslateTestingModule), MockModule(NgbTooltipModule)],
            declarations: [
                ProgrammingExerciseConfigureGradingComponent,
                ProgrammingExerciseConfigureGradingStatusComponent,
                ProgrammingExerciseGradingTableActionsComponent,
                TableEditableFieldComponent,
                MockComponent(ProgrammingExerciseConfigureGradingActionsComponent),
                MockComponent(ProgrammingExerciseGradingTasksTableComponent),
                MockComponent(ProgrammingExerciseGradingSubmissionPolicyConfigurationActionsComponent),
                MockComponent(SubmissionPolicyUpdateComponent),
                MockComponent(ProgrammingExerciseReEvaluateButtonComponent),
                MockComponent(ProgrammingExerciseTriggerAllButtonComponent),
                MockComponent(TestCasePassedBuildsChartComponent),
                MockComponent(TestCaseDistributionChartComponent),
                MockComponent(CategoryIssuesChartComponent),
                MockComponent(ScaCategoryDistributionChartComponent),
                MockPipe(RemoveKeysPipe),
                MockDirective(NgModel),
                MockDirective(TranslateDirective),
            ],
            providers: [
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: ProgrammingExerciseWebsocketService, useClass: MockProgrammingExerciseWebsocketService },
                { provide: ProgrammingExerciseGradingService, useClass: MockProgrammingExerciseGradingService },
                { provide: ProgrammingBuildRunService, useClass: MockProgrammingBuildRunService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                { provide: Router, useClass: MockRouter },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: NgbModal, useClass: MockNgbModalService },
                MockProvider(AlertService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseConfigureGradingComponent);
                debugElement = fixture.debugElement;
                comp = fixture.componentInstance as ProgrammingExerciseConfigureGradingComponent;

                gradingService = debugElement.injector.get(ProgrammingExerciseGradingService);
                route = debugElement.injector.get(ActivatedRoute);
                const router = debugElement.injector.get(Router);
                programmingExerciseWebsocketService = debugElement.injector.get(ProgrammingExerciseWebsocketService);
                programmingExerciseService = debugElement.injector.get(ProgrammingExerciseService);
                modalService = debugElement.injector.get(NgbModal);

                updateCategoriesStub = jest.spyOn(gradingService, 'updateCodeAnalysisCategories');
                resetCategoriesStub = jest.spyOn(gradingService, 'resetCategories');
                loadStatisticsStub = jest.spyOn(gradingService, 'getGradingStatistics');
                importCategoriesFromExerciseStub = jest.spyOn(gradingService, 'importCategoriesFromExercise');

                // @ts-ignore
                (router as MockRouter).setUrl('/');
                routeSubject = new Subject();
                // @ts-ignore
                (route as MockActivatedRouteWithSubjects).setSubject(routeSubject);

                testCasesChangedStub = jest.spyOn(programmingExerciseWebsocketService, 'getTestCaseState');
                getExerciseTestCaseStateStub = jest.spyOn(programmingExerciseService, 'getProgrammingExerciseTestCaseState');
                loadExerciseStub = jest.spyOn(programmingExerciseService, 'find');

                getExerciseTestCaseStateSubject = new Subject();

                testCasesChangedSubject = new Subject<boolean>();
                testCasesChangedStub.mockReturnValue(testCasesChangedSubject);
                getExerciseTestCaseStateStub.mockReturnValue(getExerciseTestCaseStateSubject);

                loadStatisticsStub.mockReturnValue(of(gradingStatistics));
                loadExerciseStub.mockReturnValue(of({ body: exercise }));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        testCases.forEach((testCase) => {
            testCase.weight = 1;
            testCase.bonusMultiplier = 1;
            testCase.bonusPoints = 0;
        });
    });

    const initGradingComponent = ({
        tab = 'test-cases',
        released = true,
        hasStudentResult = true,
        testCasesChanged = false,
        hasBuildAndTestAfterDueDate = true,
        buildAndTestAfterDueDate = dayjs(),
        showInactive = false,
    } = {}) => {
        comp.ngOnInit();
        comp.showInactive = showInactive;

        routeSubject.next({ exerciseId, tab });
        getExerciseTestCaseStateSubject.next(
            getExerciseTestCasteStateDTO(released, hasStudentResult, testCasesChanged, hasBuildAndTestAfterDueDate ? buildAndTestAfterDueDate : undefined),
        );

        (gradingService as unknown as MockProgrammingExerciseGradingService).nextTestCases(testCases);
        (gradingService as unknown as MockProgrammingExerciseGradingService).nextCategories(codeAnalysisCategories1);
    };

    it('should show the updatedTests badge when the exercise is released and has student results', () => {
        initGradingComponent();

        fixture.detectChanges();

        expect(getNoUnsavedChangesBadge()).not.toBeNull();
        expect(getNoUpdatedTestCaseBadge()).not.toBeNull();
    });

    it('should not show the updatedTests badge when the exercise is released and has no student results', () => {
        initGradingComponent({ released: true, hasStudentResult: false });

        fixture.detectChanges();

        expect(getNoUnsavedChangesBadge()).not.toBeNull();
        expect(getNoUpdatedTestCaseBadge()).toBeNull();
    });

    it('should not show the updatedTests badge when the exercise is not released and has student results (edge case)', () => {
        initGradingComponent({ released: false, hasStudentResult: true });

        fixture.detectChanges();

        expect(getNoUnsavedChangesBadge()).not.toBeNull();
        expect(getNoUpdatedTestCaseBadge()).toBeNull();
    });

    it('should show that there are updated test cases if the testCasesChanged flat is set', () => {
        initGradingComponent({ testCasesChanged: true });

        fixture.detectChanges();

        expect(getUpdatedTestCaseBadge()).not.toBeNull();
        expect(getNoUpdatedTestCaseBadge()).toBeNull();
    });

    it('should reset all categories when the reset button is clicked', () => {
        initGradingComponent({ tab: 'code-analysis' });

        fixture.detectChanges();

        comp.updateEditedField(codeAnalysisCategories1[0], EditableField.STATE)(StaticCodeAnalysisCategoryState.Feedback);
        comp.updateEditedField(codeAnalysisCategories1[1], EditableField.STATE)(StaticCodeAnalysisCategoryState.Feedback);

        comp.updateEditedField(codeAnalysisCategories1[0], EditableField.PENALTY)(3);
        comp.updateEditedField(codeAnalysisCategories1[1], EditableField.PENALTY)(4);

        comp.updateEditedField(codeAnalysisCategories1[0], EditableField.MAX_PENALTY)(15);
        comp.updateEditedField(codeAnalysisCategories1[1], EditableField.MAX_PENALTY)(15);

        const updatedCategories: StaticCodeAnalysisCategory[] = [
            { ...codeAnalysisCategories1[0], state: StaticCodeAnalysisCategoryState.Feedback, penalty: 3, maxPenalty: 15 },
            { ...codeAnalysisCategories1[1], state: StaticCodeAnalysisCategoryState.Feedback, penalty: 4, maxPenalty: 15 },
        ];
        updateCategoriesStub.mockReturnValue(of(updatedCategories));

        // Save tests.
        comp.saveCategories();

        fixture.detectChanges();

        expect(updateCategoriesStub).toHaveBeenCalledOnce();
        expect(comp.changedCategoryIds).toHaveLength(0);

        testCasesChangedSubject.next(true);

        // Reset button is now enabled because the categories were saved.
        expect(comp.hasUpdatedGradingConfig).toBeTrue();

        fixture.detectChanges();

        resetCategoriesStub.mockReturnValue(of(codeAnalysisCategories1));

        // Reset the stub to ensure that it function is called exactly once on category reset
        loadStatisticsStub.mockReset();

        const resetButton = getResetButton();
        expectElementToBeEnabled(resetButton);
        resetButton.click();

        fixture.detectChanges();

        expect(resetCategoriesStub).toHaveBeenCalledOnce();
        expect(resetCategoriesStub).toHaveBeenCalledWith(exerciseId);
        expect(loadStatisticsStub).toHaveBeenCalledOnce();
        expect(loadStatisticsStub).toHaveBeenCalledWith(exerciseId);
        expect(comp.staticCodeAnalysisCategoriesForTable).toEqual(codeAnalysisCategories1);
        expect(comp.changedCategoryIds).toHaveLength(0);
    });

    it('should import a configuration from a different exercise', () => {
        const mockReturnValue = {
            result: Promise.resolve({ id: 456 } as ProgrammingExercise),
            componentInstance: {},
        } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        initGradingComponent({ tab: 'code-analysis' });
        fixture.detectChanges();

        const button = debugElement.query(By.css('#import-configuration-button'));

        button.nativeElement.click();

        expect(mockReturnValue.componentInstance.exerciseType).toEqual(ExerciseType.PROGRAMMING);
        expect(mockReturnValue.componentInstance.programmingLanguage).toEqual(ProgrammingLanguage.JAVA);

        expect(importCategoriesFromExerciseStub).toHaveBeenCalledOnce();
        expect(importCategoriesFromExerciseStub).toHaveBeenCalledWith(exercise.id, 456);
    });

    it('should import categories and update the component', () => {
        initGradingComponent();

        const newConfiguration = new StaticCodeAnalysisCategory();
        importCategoriesFromExerciseStub.mockReturnValue(of(newConfiguration));

        comp.importCategories(5);

        expect(comp.staticCodeAnalysisCategoriesForTable).toBe(newConfiguration);
        expect(comp.staticCodeAnalysisCategoriesForCharts).toBe(newConfiguration);
        expect(comp.backupStaticCodeAnalysisCategories).toBe(newConfiguration);
    });

    it('should update sca category when an input field is updated', () => {
        initGradingComponent({ tab: 'code-analysis' });

        fixture.detectChanges();

        const table = debugElement.query(By.css(codeAnalysisTableId));

        const gradedCategories = comp.staticCodeAnalysisCategoriesForTable.filter((category) => category.state === StaticCodeAnalysisCategoryState.Graded);

        // get inputs
        const editingInputs = table.queryAll(By.css(tableEditingInput));
        expect(editingInputs).toHaveLength(gradedCategories.length * 2);

        const penaltyInput = editingInputs[0].nativeElement;
        expect(penaltyInput).not.toBeNull();
        penaltyInput.focus();

        // Set new penalty.
        penaltyInput.value = '20';
        penaltyInput.dispatchEvent(new Event('blur'));

        const maxPenaltyInput = editingInputs[1].nativeElement;
        expect(maxPenaltyInput).not.toBeNull();
        maxPenaltyInput.focus();

        // Set new max penalty.
        maxPenaltyInput.value = '100';
        maxPenaltyInput.dispatchEvent(new Event('blur'));

        fixture.detectChanges();

        expect(comp.changedCategoryIds).toEqual([gradedCategories[0].id]);

        const updatedCategory: StaticCodeAnalysisCategory = { ...gradedCategories[0], penalty: 20, maxPenalty: 100 };

        // Save weight.
        updateCategoriesStub.mockReturnValue(of([updatedCategory]));
        const saveButton = getSaveButton();
        expectElementToBeEnabled(saveButton);
        saveButton.click();

        fixture.detectChanges();

        expect(updateCategoriesStub).toHaveBeenCalledOnce();
        expect(updateCategoriesStub).toHaveBeenCalledWith(exerciseId, [StaticCodeAnalysisCategoryUpdate.from(updatedCategory)]);

        const categoryThatWasUpdated = comp.staticCodeAnalysisCategoriesForTable.find((category) => category.id === updatedCategory.id)!;
        expect(categoryThatWasUpdated.penalty).toBe(20);
        expect(categoryThatWasUpdated.maxPenalty).toBe(100);
        expect(comp.changedCategoryIds).toHaveLength(0);

        testCasesChangedSubject.next(true);
        // Trigger button is now enabled because the tests were saved.
        expect(comp.hasUpdatedGradingConfig).toBeTrue();
    });

    it('should load the grading statistics correctly', () => {
        initGradingComponent({ tab: 'code-analysis' });

        fixture.detectChanges();

        expect(loadStatisticsStub).toHaveBeenCalledTimes(3);
        expect(loadStatisticsStub).toHaveBeenCalledWith(exerciseId);

        expect(comp.maxIssuesPerCategory).toBe(5);
        expect(comp.gradingStatistics).toEqual(gradingStatistics);

        fixture.detectChanges();

        const categoryIssuesCharts = debugElement.queryAll(By.directive(CategoryIssuesChartComponent)).map((d) => d.componentInstance);
        expect(categoryIssuesCharts).toHaveLength(2);

        expect(categoryIssuesCharts[0].issuesMap).toEqual(gradingStatistics.categoryIssuesMap!['Bad Practice']);
        expect(categoryIssuesCharts[0].category).toEqual(codeAnalysisCategories1[0]);
        expect(categoryIssuesCharts[0].totalStudents).toBe(5);
        expect(categoryIssuesCharts[0].maxNumberOfIssues).toBe(5);

        expect(categoryIssuesCharts[1].issuesMap).toEqual(gradingStatistics.categoryIssuesMap!['Styling']);
        expect(categoryIssuesCharts[1].category).toEqual(codeAnalysisCategories1[1]);
        expect(categoryIssuesCharts[1].totalStudents).toBe(5);
        expect(categoryIssuesCharts[1].maxNumberOfIssues).toBe(5);
    });

    const sortAndTestTable = (table: Table) => (headerElement: DebugElement, prop: string, dir: string) => {
        headerElement.nativeElement.click();
        fixture.detectChanges();

        expect(comp.tableSorts[table]).toEqual([{ prop, dir }]);
    };

    it('should sort code-analysis table', () => {
        initGradingComponent({ tab: 'code-analysis' });

        fixture.detectChanges();

        const table = debugElement.query(By.css(codeAnalysisTableId));
        const headerColumns = table.queryAll(By.css('.datatable-header-cell-wrapper'));

        const sortAndTest = sortAndTestTable('codeAnalysis');

        const penaltyHeader = headerColumns[2];
        sortAndTest(penaltyHeader, 'penalty', 'asc');

        const maxPenaltyHeader = headerColumns[3];
        sortAndTest(maxPenaltyHeader, 'maxPenalty', 'asc');

        const detectedIssuesHeader = headerColumns[4];
        sortAndTest(detectedIssuesHeader, 'detectedIssues', 'asc');
        sortAndTest(detectedIssuesHeader, 'detectedIssues', 'desc');
    });

    it('should not require confirmation if there are no unsaved changes', () => {
        initGradingComponent();

        expect(comp.canDeactivate()).toBeTrue();
    });

    it('should require confirmation if there are unsaved changes', () => {
        initGradingComponent();
        comp.changedTestCaseIds = [1, 2, 3];

        // Ignore window confirm
        window.confirm = () => {
            return false;
        };

        expect(comp.canDeactivate()).toBeFalse();
    });

    describe('test chart interaction', () => {
        it('should filter sca table correctly', () => {
            initGradingComponent({ tab: 'code-analysis' });
            fixture.detectChanges();
            const scaCategoriesDisplayedByChart = comp.staticCodeAnalysisCategoriesForCharts;
            const expectedCategory = {
                id: 1,
                name: 'Bad Practice',
                state: StaticCodeAnalysisCategoryState.Graded,
                penalty: 1,
                maxPenalty: 10,
            };

            comp.filterByChart(1, ChartFilterType.CATEGORIES);

            expect(comp.staticCodeAnalysisCategoriesForTable).toHaveLength(1);
            expect(comp.staticCodeAnalysisCategoriesForTable).toEqual([expectedCategory]);
            expect(comp.staticCodeAnalysisCategoriesForCharts).toEqual(scaCategoriesDisplayedByChart);
        });

        it('should update category accordingly if modified while chart filtering', () => {
            initGradingComponent({ tab: 'code-analysis' });
            fixture.detectChanges();
            comp.filterByChart(1, ChartFilterType.CATEGORIES);
            fixture.detectChanges();
            const table = debugElement.query(By.css(codeAnalysisTableId));

            // get inputs
            const editingInputs = table.queryAll(By.css(tableEditingInput));
            expect(editingInputs).toHaveLength(2);

            const penaltyInput = editingInputs[0].nativeElement;
            expect(penaltyInput).not.toBeNull();
            penaltyInput.focus();

            // Set new penalty.
            penaltyInput.value = '10';
            penaltyInput.dispatchEvent(new Event('blur'));

            const maxPenaltyInput = editingInputs[1].nativeElement;
            expect(maxPenaltyInput).not.toBeNull();
            maxPenaltyInput.focus();

            // Set new max penalty.
            maxPenaltyInput.value = '50';
            maxPenaltyInput.dispatchEvent(new Event('blur'));

            fixture.detectChanges();
            const currentlyDisplayedCategory = {
                id: 1,
                name: 'Bad Practice',
                state: StaticCodeAnalysisCategoryState.Graded,
                penalty: 10,
                maxPenalty: 50,
            };

            expect(comp.staticCodeAnalysisCategoriesForTable).toHaveLength(1);
            expect(comp.staticCodeAnalysisCategoriesForTable).toEqual([currentlyDisplayedCategory]);
            expect(comp.staticCodeAnalysisCategoriesForCharts).toContainEqual(currentlyDisplayedCategory);

            // we reset the table
            comp.filterByChart(-5, ChartFilterType.CATEGORIES);
            const expectedCategories = codeAnalysisCategories1
                .filter((category) => category.state !== StaticCodeAnalysisCategoryState.Inactive)
                .map((category) => (category.id !== 1 ? category : currentlyDisplayedCategory));

            expect(comp.staticCodeAnalysisCategoriesForTable).toEqual(expectedCategories);
        });
    });
});
