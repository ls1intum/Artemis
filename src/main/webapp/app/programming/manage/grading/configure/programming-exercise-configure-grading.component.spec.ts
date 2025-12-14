import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { NgbModal, NgbModalRef, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExerciseGradingStatistics } from 'app/programming/shared/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTestCase, Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/programming/shared/entities/static-code-analysis-category.model';
import { CategoryIssuesChartComponent } from 'app/programming/manage/grading/charts/category-issues-chart.component';
import { ScaCategoryDistributionChartComponent } from 'app/programming/manage/grading/charts/sca-category-distribution-chart.component';
import { TestCaseDistributionChartComponent } from 'app/programming/manage/grading/charts/test-case-distribution-chart.component';
import { TestCasePassedBuildsChartComponent } from 'app/programming/manage/grading/charts/test-case-passed-builds-chart.component';
import { ProgrammingExerciseConfigureGradingActionsComponent } from 'app/programming/manage/grading/configure-actions/programming-exercise-configure-grading-actions.component';
import { ProgrammingExerciseConfigureGradingStatusComponent } from 'app/programming/manage/grading/configure-status/programming-exercise-configure-grading-status.component';
import {
    ChartFilterType,
    EditableField,
    ProgrammingExerciseConfigureGradingComponent,
} from 'app/programming/manage/grading/configure/programming-exercise-configure-grading.component';
import { ProgrammingExerciseGradingSubmissionPolicyConfigurationActionsComponent } from 'app/programming/manage/grading/configure-submission-policy/programming-exercise-grading-submission-policy-configuration-actions.component';
import { ProgrammingExerciseGradingTableActionsComponent } from 'app/programming/manage/grading/table-actions/programming-exercise-grading-table-actions.component';
import { ProgrammingExerciseGradingService, StaticCodeAnalysisCategoryUpdate } from 'app/programming/manage/services/programming-exercise-grading.service';
import { ProgrammingExerciseWebsocketService } from 'app/programming/manage/services/programming-exercise-websocket.service';
import { ProgrammingExerciseService, ProgrammingExerciseTestCaseStateDTO } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingBuildRunService } from 'app/programming/shared/services/programming-build-run.service';
import { ProgrammingExerciseReEvaluateButtonComponent } from 'app/programming/shared/actions/re-evalulate-button/programming-exercise-re-evaluate-button.component';
import { ProgrammingExerciseTriggerAllButtonComponent } from 'app/programming/shared/actions/trigger-all-button/programming-exercise-trigger-all-button.component';
import { SubmissionPolicyUpdateComponent } from 'app/exercise/submission-policy/submission-policy-update.component';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { TableEditableFieldComponent } from 'app/shared/table/editable-field/table-editable-field.component';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { Subject, of } from 'rxjs';
import { MockActivatedRouteWithSubjects } from 'test/helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockProgrammingBuildRunService } from 'test/helpers/mocks/service/mock-programming-build-run.service';
import { MockProgrammingExerciseGradingService } from 'test/helpers/mocks/service/mock-programming-exercise-grading.service';
import { MockProgrammingExerciseWebsocketService } from 'test/helpers/mocks/service/mock-programming-exercise-websocket.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { expectElementToBeEnabled, getElement } from 'test/helpers/utils/general-test.utils';
import { ProgrammingExerciseGradingTasksTableComponent } from 'app/programming/manage/grading/tasks/programming-exercise-grading-tasks-table/programming-exercise-grading-tasks-table.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('ProgrammingExerciseConfigureGradingComponent', () => {
    let comp: ProgrammingExerciseConfigureGradingComponent;
    let fixture: ComponentFixture<ProgrammingExerciseConfigureGradingComponent>;
    let debugElement: DebugElement;

    let route: ActivatedRoute;
    let gradingService: ProgrammingExerciseGradingService;
    let programmingExerciseService: ProgrammingExerciseService;
    let modalService: NgbModal;

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

    const resetTableButton = '#reset-table-button';
    const testCasesNoUnsavedChanges = '#test-case-status-no-unsaved-changes';
    const testCasesUpdated = '#test-case-status-updated';
    const testCasesNoUpdated = '#test-case-status-no-updated';

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
            imports: [NgxDatatableModule, MockModule(NgbTooltipModule), FaIconComponent],
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
                MockDirective(TranslateDirective),
            ],
            providers: [
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: ProgrammingExerciseWebsocketService, useClass: MockProgrammingExerciseWebsocketService },
                { provide: ProgrammingExerciseGradingService, useClass: MockProgrammingExerciseGradingService },
                { provide: ProgrammingBuildRunService, useClass: MockProgrammingBuildRunService },
                LocalStorageService,
                SessionStorageService,
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                { provide: Router, useClass: MockRouter },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseConfigureGradingComponent);
        debugElement = fixture.debugElement;
        comp = fixture.componentInstance as ProgrammingExerciseConfigureGradingComponent;

        gradingService = fixture.debugElement.injector.get(ProgrammingExerciseGradingService);
        route = fixture.debugElement.injector.get(ActivatedRoute);
        const router = fixture.debugElement.injector.get(Router);
        programmingExerciseWebsocketService = fixture.debugElement.injector.get(ProgrammingExerciseWebsocketService);
        programmingExerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);
        modalService = fixture.debugElement.injector.get(NgbModal);

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

        fixture.changeDetectorRef.detectChanges();

        expect(getNoUnsavedChangesBadge()).not.toBeNull();
        expect(getNoUpdatedTestCaseBadge()).not.toBeNull();
    });

    it('should not show the updatedTests badge when the exercise is released and has no student results', () => {
        initGradingComponent({ released: true, hasStudentResult: false });

        fixture.changeDetectorRef.detectChanges();

        expect(getNoUnsavedChangesBadge()).not.toBeNull();
        expect(getNoUpdatedTestCaseBadge()).toBeNull();
    });

    it('should not show the updatedTests badge when the exercise is not released and has student results (edge case)', () => {
        initGradingComponent({ released: false, hasStudentResult: true });

        fixture.changeDetectorRef.detectChanges();

        expect(getNoUnsavedChangesBadge()).not.toBeNull();
        expect(getNoUpdatedTestCaseBadge()).toBeNull();
    });

    it('should show that there are updated test cases if the testCasesChanged flat is set', () => {
        initGradingComponent({ testCasesChanged: true });

        fixture.changeDetectorRef.detectChanges();

        expect(getUpdatedTestCaseBadge()).not.toBeNull();
        expect(getNoUpdatedTestCaseBadge()).toBeNull();
    });

    it('should reset all categories when the reset button is clicked', () => {
        initGradingComponent({ tab: 'code-analysis' });
        // Reset default sorts to avoid ngx-datatable compareFn issues in tests
        comp.tableSorts = { testCases: [], codeAnalysis: [] };
        fixture.changeDetectorRef.detectChanges();

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

        fixture.changeDetectorRef.detectChanges();

        expect(updateCategoriesStub).toHaveBeenCalledOnce();
        expect(comp.changedCategoryIds).toHaveLength(0);

        testCasesChangedSubject.next(true);

        // Reset button is now enabled because the categories were saved.
        expect(comp.hasUpdatedGradingConfig).toBeTrue();

        fixture.changeDetectorRef.detectChanges();

        resetCategoriesStub.mockReturnValue(of(codeAnalysisCategories1));

        // Reset the stub to ensure that it function is called exactly once on category reset
        loadStatisticsStub.mockReset();

        const resetButton = getResetButton();
        expectElementToBeEnabled(resetButton);
        resetButton.click();

        fixture.changeDetectorRef.detectChanges();

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
        // Reset default sorts to avoid ngx-datatable compareFn issues in tests
        comp.tableSorts = { testCases: [], codeAnalysis: [] };
        fixture.changeDetectorRef.detectChanges();

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

        const gradedCategories = comp.staticCodeAnalysisCategoriesForTable.filter((category) => category.state === StaticCodeAnalysisCategoryState.Graded);
        expect(gradedCategories).not.toHaveLength(0);

        comp.updateEditedCategoryField(gradedCategories[0], EditableField.PENALTY)(20);
        comp.updateEditedCategoryField(gradedCategories[0], EditableField.MAX_PENALTY)(100);

        expect(comp.changedCategoryIds).toEqual([gradedCategories[0].id]);

        const updatedCategory: StaticCodeAnalysisCategory = { ...gradedCategories[0], penalty: 20, maxPenalty: 100 };

        // Save weight.
        updateCategoriesStub.mockReturnValue(of([updatedCategory]));
        comp.saveCategories();

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
        // Reset default sorts to avoid ngx-datatable compareFn issues in tests
        comp.tableSorts = { testCases: [], codeAnalysis: [] };
        fixture.changeDetectorRef.detectChanges();
        fixture.changeDetectorRef.detectChanges();

        expect(loadStatisticsStub).toHaveBeenCalledTimes(3);
        expect(loadStatisticsStub).toHaveBeenCalledWith(exerciseId);

        expect(comp.maxIssuesPerCategory).toBe(5);
        expect(comp.gradingStatistics).toEqual(gradingStatistics);

        expect(comp.staticCodeAnalysisCategoriesForCharts).toEqual(codeAnalysisCategories1);
    });

    it('should sort code-analysis table', () => {
        initGradingComponent({ tab: 'code-analysis' });

        comp.onSort('codeAnalysis', { sorts: [{ prop: 'penalty', dir: 'asc' }] });
        expect(comp.tableSorts.codeAnalysis).toEqual([{ prop: 'penalty', dir: 'asc' }]);

        comp.onSort('codeAnalysis', { sorts: [{ prop: 'maxPenalty', dir: 'asc' }] });
        expect(comp.tableSorts.codeAnalysis).toEqual([{ prop: 'maxPenalty', dir: 'asc' }]);

        comp.onSort('codeAnalysis', { sorts: [{ prop: 'detectedIssues', dir: 'asc' }] });
        comp.onSort('codeAnalysis', { sorts: [{ prop: 'detectedIssues', dir: 'desc' }] });
        expect(comp.tableSorts.codeAnalysis).toEqual([{ prop: 'detectedIssues', dir: 'desc' }]);
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
            // Reset default sorts to avoid ngx-datatable compareFn issues in tests
            comp.tableSorts = { testCases: [], codeAnalysis: [] };
            fixture.changeDetectorRef.detectChanges();
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
            // Reset default sorts to avoid ngx-datatable compareFn issues in tests
            comp.tableSorts = { testCases: [], codeAnalysis: [] };
            fixture.changeDetectorRef.detectChanges();
            fixture.changeDetectorRef.detectChanges();
            comp.filterByChart(1, ChartFilterType.CATEGORIES);
            fixture.changeDetectorRef.detectChanges();
            const gradedCategory = comp.staticCodeAnalysisCategoriesForTable.find((category) => category.id === 1)!;
            comp.updateEditedCategoryField(gradedCategory, EditableField.PENALTY)(10);
            comp.updateEditedCategoryField(gradedCategory, EditableField.MAX_PENALTY)(50);

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
