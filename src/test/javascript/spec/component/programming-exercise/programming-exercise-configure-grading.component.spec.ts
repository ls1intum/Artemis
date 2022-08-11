import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, Subject } from 'rxjs';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockProgrammingExerciseGradingService } from '../../helpers/mocks/service/mock-programming-exercise-grading.service';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming-exercise-test-case.model';
import { expectElementToBeEnabled, getElement } from '../../helpers/utils/general.utils';
import { ProgrammingExerciseWebsocketService } from 'app/exercises/programming/manage/services/programming-exercise-websocket.service';
import { MockProgrammingExerciseWebsocketService } from '../../helpers/mocks/service/mock-programming-exercise-websocket.service';
import { ProgrammingBuildRunService } from 'app/exercises/programming/participate/programming-build-run.service';
import { MockProgrammingBuildRunService } from '../../helpers/mocks/service/mock-programming-build-run.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import {
    ChartFilterType,
    EditableField,
    ProgrammingExerciseConfigureGradingComponent,
} from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading.component';
import { ProgrammingExerciseService, ProgrammingExerciseTestCaseStateDTO } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import {
    ProgrammingExerciseGradingService,
    ProgrammingExerciseTestCaseUpdate,
    StaticCodeAnalysisCategoryUpdate,
} from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { ProgrammingExerciseGradingStatistics } from 'app/entities/programming-exercise-test-case-statistics.model';
import { CategoryIssuesChartComponent } from 'app/exercises/programming/manage/grading/charts/category-issues-chart.component';
import { TestCasePassedBuildsChartComponent } from 'app/exercises/programming/manage/grading/charts/test-case-passed-builds-chart.component';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { ProgrammingExerciseConfigureGradingStatusComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading-status.component';
import { ProgrammingExerciseConfigureGradingActionsComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading-actions.component';
import { ProgrammingExerciseGradingTableActionsComponent } from 'app/exercises/programming/manage/grading/programming-exercise-grading-table-actions.component';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { TableEditableFieldComponent } from 'app/shared/table/table-editable-field.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbAlert, NgbPopover, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgModel } from '@angular/forms';
import { TestCaseDistributionChartComponent } from 'app/exercises/programming/manage/grading/charts/test-case-distribution-chart.component';
import { ScaCategoryDistributionChartComponent } from 'app/exercises/programming/manage/grading/charts/sca-category-distribution-chart.component';
import { ProgrammingExerciseReEvaluateButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-re-evaluate-button.component';
import { ProgrammingExerciseTriggerAllButtonComponent } from 'app/exercises/programming/shared/actions/programming-exercise-trigger-all-button.component';
import { ProgrammingExerciseGradingSubmissionPolicyConfigurationActionsComponent } from 'app/exercises/programming/manage/grading/programming-exercise-grading-submission-policy-configuration-actions.component';
import { SubmissionPolicyUpdateComponent } from 'app/exercises/shared/submission-policy/submission-policy-update.component';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';

describe('ProgrammingExerciseConfigureGradingComponent', () => {
    let comp: ProgrammingExerciseConfigureGradingComponent;
    let fixture: ComponentFixture<ProgrammingExerciseConfigureGradingComponent>;
    let debugElement: DebugElement;

    let route: ActivatedRoute;
    let gradingService: ProgrammingExerciseGradingService;
    let programmingExerciseService: ProgrammingExerciseService;

    let updateTestCasesStub: jest.SpyInstance;
    let updateCategoriesStub: jest.SpyInstance;
    let resetTestCasesStub: jest.SpyInstance;
    let resetCategoriesStub: jest.SpyInstance;
    let testCasesChangedStub: jest.SpyInstance;
    let getExerciseTestCaseStateStub: jest.SpyInstance;
    let loadExerciseStub: jest.SpyInstance;
    let loadStatisticsStub: jest.SpyInstance;
    let programmingExerciseWebsocketService: ProgrammingExerciseWebsocketService;

    let routeSubject: Subject<Params>;
    let testCasesChangedSubject: Subject<boolean>;
    let getExerciseTestCaseStateSubject: Subject<{ body: ProgrammingExerciseTestCaseStateDTO }>;

    const testCaseTableId = '#testCaseTable';
    const tableEditingInput = '.table-editable-field__input';
    const rowClass = 'datatable-body-row';
    const saveTableButton = '#save-table-button';
    const resetTableButton = '#reset-table-button';
    const testCasesNoUnsavedChanges = '#test-case-status-no-unsaved-changes';
    const testCasesUnsavedChanges = '#test-case-status-unsaved-changes';
    const testCasesUpdated = '#test-case-status-updated';
    const testCasesNoUpdated = '#test-case-status-no-updated';
    const codeAnalysisTableId = '#codeAnalysisTable';

    const exerciseId = 1;
    const exercise = {
        id: exerciseId,
        staticCodeAnalysisEnabled: true,
    } as ProgrammingExercise;
    const testCases1 = [
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

    const sortedTestCases = (testCases: ProgrammingExerciseTestCase[]) => {
        return testCases.sort((a, b) => {
            if (a.testName! < b.testName!) {
                return -1;
            } else if (a.testName! > b.testName!) {
                return 1;
            } else {
                return 0;
            }
        });
    };

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

    const getUnsavedChangesBadge = () => {
        return getElement(debugElement, testCasesUnsavedChanges);
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
            imports: [ArtemisTestModule, NgxDatatableModule, MockModule(TranslateTestingModule)],
            declarations: [
                ProgrammingExerciseConfigureGradingComponent,
                ProgrammingExerciseConfigureGradingStatusComponent,
                ProgrammingExerciseGradingTableActionsComponent,
                TableEditableFieldComponent,
                MockComponent(ProgrammingExerciseConfigureGradingActionsComponent),
                MockComponent(ProgrammingExerciseGradingSubmissionPolicyConfigurationActionsComponent),
                MockComponent(SubmissionPolicyUpdateComponent),
                MockComponent(ProgrammingExerciseReEvaluateButtonComponent),
                MockComponent(ProgrammingExerciseTriggerAllButtonComponent),
                MockComponent(TestCasePassedBuildsChartComponent),
                MockComponent(TestCaseDistributionChartComponent),
                MockComponent(CategoryIssuesChartComponent),
                MockComponent(ScaCategoryDistributionChartComponent),
                MockComponent(NgbAlert),
                MockPipe(RemoveKeysPipe),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgModel),
                MockDirective(NgbPopover),
                MockDirective(NgbTooltip),
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

                updateTestCasesStub = jest.spyOn(gradingService, 'updateTestCase');
                updateCategoriesStub = jest.spyOn(gradingService, 'updateCodeAnalysisCategories');
                resetTestCasesStub = jest.spyOn(gradingService, 'resetTestCases');
                resetCategoriesStub = jest.spyOn(gradingService, 'resetCategories');
                loadStatisticsStub = jest.spyOn(gradingService, 'getGradingStatistics');

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

        (gradingService as unknown as MockProgrammingExerciseGradingService).nextTestCases(testCases1);
        (gradingService as unknown as MockProgrammingExerciseGradingService).nextCategories(codeAnalysisCategories1);
    };

    it('should create a datatable with the correct amount of rows when test cases come in (hide inactive tests)', () => {
        initGradingComponent();

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const rows = table.queryAll(By.css(rowClass));

        expect(comp.testCases).toEqual(testCases1);
        expect(rows).toHaveLength(testCases1.filter(({ active }) => active).length);

        const saveButton = debugElement.query(By.css(saveTableButton));
        expect(saveButton).not.toBeNull();
        expect(saveButton.nativeElement.disabled).toBeTrue();
    });

    it('should create a datatable with the correct amount of rows when test cases come in (show inactive tests)', () => {
        initGradingComponent({ showInactive: true });

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const rows = table.queryAll(By.css(rowClass));

        expect(comp.testCases).toEqual(testCases1);
        expect(rows).toHaveLength(testCases1.length);

        const saveButton = debugElement.query(By.css(saveTableButton));
        expect(saveButton).not.toBeNull();
        expect(saveButton.nativeElement.disabled).toBeTrue();
    });

    it('should update test case when an input field is updated', () => {
        initGradingComponent({ showInactive: true });

        fixture.detectChanges();

        const orderedTests = sortedTestCases(testCases1);

        const table = debugElement.query(By.css(testCaseTableId));

        // get first weight input
        const editingInputs = table.queryAll(By.css(tableEditingInput));
        expect(editingInputs).toHaveLength(testCases1.length * 3);

        const weightInput = editingInputs[0].nativeElement;
        expect(weightInput).not.toBeNull();
        weightInput.focus();

        // Set new weight.
        weightInput.value = '20';
        weightInput.dispatchEvent(new Event('blur'));

        const multiplierInput = editingInputs[1].nativeElement;
        expect(multiplierInput).not.toBeNull();
        multiplierInput.focus();

        // Set new multiplier.
        multiplierInput.value = '2';
        multiplierInput.dispatchEvent(new Event('blur'));

        const bonusInput = editingInputs[2].nativeElement;
        expect(bonusInput).not.toBeNull();
        bonusInput.focus();

        // Set new bonus.
        bonusInput.value = '1';
        bonusInput.dispatchEvent(new Event('blur'));

        fixture.detectChanges();

        expect(comp.changedTestCaseIds).toEqual([orderedTests[0].id]);

        // Save weight.
        updateTestCasesStub.mockReturnValue(of([{ ...orderedTests[0], weight: 20, bonusMultiplier: 2, bonusPoints: 1 }]));
        const saveButton = getSaveButton();
        expectElementToBeEnabled(saveButton);
        saveButton.click();

        fixture.detectChanges();

        let testThatWasUpdated = sortedTestCases(comp.testCases)[0];
        expect(updateTestCasesStub).toHaveBeenCalledOnce();
        expect(updateTestCasesStub).toHaveBeenCalledWith(exerciseId, [new ProgrammingExerciseTestCaseUpdate(testThatWasUpdated.id, 20, 1, 2, testThatWasUpdated.visibility)]);
        expect(testThatWasUpdated.weight).toBe(20);
        expect(testThatWasUpdated.bonusMultiplier).toBe(2);
        expect(testThatWasUpdated.bonusPoints).toBe(1);
        expect(comp.changedTestCaseIds).toHaveLength(0);

        testCasesChangedSubject.next(true);
        // Trigger button is now enabled because the tests were saved.
        expect(comp.hasUpdatedGradingConfig).toBeTrue();

        fixture.detectChanges();

        multiplierInput.focus();
        multiplierInput.value = ''; // test default value
        bonusInput.dispatchEvent(new Event('input'));

        bonusInput.focus();
        bonusInput.value = 'a'; // test NaN value
        bonusInput.dispatchEvent(new Event('input'));

        fixture.detectChanges();

        multiplierInput.dispatchEvent(new Event('blur'));
        bonusInput.dispatchEvent(new Event('blur'));

        fixture.detectChanges();

        expect(comp.changedTestCaseIds).toEqual([orderedTests[0].id]);

        expect(multiplierInput.value).toBe('1');
        expect(bonusInput.value).toBe('1');

        // Save weight.
        updateTestCasesStub.mockReset();
        updateTestCasesStub.mockReturnValue(of([{ ...orderedTests[0], weight: 20, bonusMultiplier: 1, bonusPoints: 1 }]));
        saveButton.click();

        fixture.detectChanges();

        testThatWasUpdated = sortedTestCases(comp.testCases)[0];
        expect(updateTestCasesStub).toHaveBeenCalledOnce();
        expect(updateTestCasesStub).toHaveBeenCalledWith(exerciseId, [new ProgrammingExerciseTestCaseUpdate(testThatWasUpdated.id, 20, 1, 1, testThatWasUpdated.visibility)]);
        expect(testThatWasUpdated.weight).toBe(20);
        expect(testThatWasUpdated.bonusMultiplier).toBe(1);
        expect(testThatWasUpdated.bonusPoints).toBe(1);
        expect(comp.changedTestCaseIds).toHaveLength(0);
    });

    const setAllWeightsToZero = () => {
        const table = debugElement.query(By.css(testCaseTableId));

        // get all input fields
        const editingInputs = table.queryAll(By.css(tableEditingInput));
        expect(editingInputs).toHaveLength(testCases1.length * 3);
        // Set only the weight input fields to 0 of all test cases
        for (let i = 0; i < editingInputs.length; i += 3) {
            const weightInput = editingInputs[i].nativeElement;
            expect(weightInput).not.toBeNull();
            weightInput.focus();

            // Set new weight.
            weightInput.value = '0';
            weightInput.dispatchEvent(new Event('blur'));
        }
    };

    const checkBehaviourForZeroWeight = (assessmentType: AssessmentType) => {
        initGradingComponent({ showInactive: true });
        comp.programmingExercise.assessmentType = assessmentType;

        fixture.detectChanges();

        const orderedTests = sortedTestCases(testCases1);
        setAllWeightsToZero();

        fixture.detectChanges();
        expect(comp.changedTestCaseIds).toEqual(orderedTests.map((test) => test.id));

        // Mock which should be return from update service call
        const updateTestCases = orderedTests.map((test) => {
            return { ...test, weight: 0, bonusMultiplier: 2, bonusPoints: 1 };
        });
        // Save weight.
        updateTestCasesStub.mockReturnValue(of(updateTestCases));

        // Initialize spy for error alert
        const alertService = TestBed.inject(AlertService);
        const alertServiceSpy = jest.spyOn(alertService, 'error');

        const saveButton = getSaveButton();
        expectElementToBeEnabled(saveButton);
        saveButton.click();

        if (assessmentType === AssessmentType.AUTOMATIC) {
            expect(alertServiceSpy).toHaveBeenCalledOnce();
            expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.configureGrading.testCases.weightSumError');
        } else {
            expect(alertServiceSpy).not.toHaveBeenCalled();
        }
    };

    it('should show an error alert when test case weights are less or equal zero for exercises with automatic feedback', () => {
        checkBehaviourForZeroWeight(AssessmentType.AUTOMATIC);
    });

    it('should NOT show an error alert when test case weights are zero for exercises with semiautomatic feedback', () => {
        checkBehaviourForZeroWeight(AssessmentType.SEMI_AUTOMATIC);
    });

    it('should NOT show an error alert when test case weights are zero for exercises with manual feedback', () => {
        checkBehaviourForZeroWeight(AssessmentType.MANUAL);
    });

    it('should be able to update the value of the visibility', () => {
        initGradingComponent({ showInactive: true });

        fixture.detectChanges();

        const orderedTests = sortedTestCases(testCases1);

        const table = debugElement.query(By.css(testCaseTableId));
        const dropdowns = table.queryAll(By.all()).filter((elem) => elem.name === 'select');
        expect(dropdowns).toHaveLength(testCases1.length);
        dropdowns[0].nativeElement.value = Visibility.AfterDueDate;
        dropdowns[0].nativeElement.dispatchEvent(new Event('change'));

        fixture.detectChanges();

        expect(comp.changedTestCaseIds).toEqual([orderedTests[0].id]);

        // The UI should now show that there are unsaved changes.
        expect(getUnsavedChangesBadge()).not.toBeNull();
        expect(getNoUnsavedChangesBadge()).toBeNull();

        // Save weight.
        updateTestCasesStub.mockReturnValue(of({ ...orderedTests[0], afterDueDate: true }));
        const saveTestCases = debugElement.query(By.css(saveTableButton));
        expect(saveTestCases).not.toBeNull();
        expect(saveTestCases.nativeElement.disabled).toBeFalse();
        saveTestCases.nativeElement.click();

        fixture.detectChanges();

        const testThatWasUpdated = sortedTestCases(comp.testCases)[0];
        expect(updateTestCasesStub).toHaveBeenCalledOnce();
        expect(updateTestCasesStub).toHaveBeenCalledWith(exerciseId, [ProgrammingExerciseTestCaseUpdate.from(testThatWasUpdated)]);
    });

    it('should also be able to select after due date as visibility option if the programming exercise does not have a buildAndTestAfterDueDate', () => {
        initGradingComponent({ hasBuildAndTestAfterDueDate: false, showInactive: true });

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const options = table.queryAll(By.all()).filter((elem) => elem.name === 'option');
        // three options for each test case should still be available
        expect(options).toHaveLength(testCases1.length * 3);
    });

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

    it('should reset all test cases when the reset button is clicked', () => {
        initGradingComponent();

        fixture.detectChanges();

        comp.updateEditedField(testCases1[0], EditableField.WEIGHT)(3);
        comp.updateEditedField(testCases1[1], EditableField.WEIGHT)(4);

        comp.updateEditedField(testCases1[1], EditableField.BONUS_MULTIPLIER)(2);
        comp.updateEditedField(testCases1[2], EditableField.BONUS_MULTIPLIER)(3);

        comp.updateEditedField(testCases1[0], EditableField.BONUS_POINTS)(4);
        comp.updateEditedField(testCases1[2], EditableField.BONUS_POINTS)(10);

        const updatedTestCases: ProgrammingExerciseTestCase[] = [
            { ...testCases1[0], weight: 3, bonusPoints: 4 },
            { ...testCases1[1], weight: 4, bonusMultiplier: 2 },
            { ...testCases1[2], bonusMultiplier: 3, bonusPoints: 10 },
        ];
        updateTestCasesStub.mockReturnValue(of(updatedTestCases));

        // Save tests.
        comp.saveTestCases();

        fixture.detectChanges();

        expect(updateTestCasesStub).toHaveBeenCalledOnce();

        expect(comp.changedTestCaseIds).toHaveLength(0);
        testCasesChangedSubject.next(true);

        // Reset button is now enabled because the tests were saved.
        expect(comp.hasUpdatedGradingConfig).toBeTrue();

        fixture.detectChanges();

        resetTestCasesStub.mockReturnValue(of(testCases1));

        const resetButton = getResetButton();
        expectElementToBeEnabled(resetButton);
        resetButton.click();

        fixture.detectChanges();

        expect(resetTestCasesStub).toHaveBeenCalledOnce();
        expect(resetTestCasesStub).toHaveBeenCalledWith(exerciseId);
        expect(comp.testCases).toEqual(testCases1);
        expect(comp.changedTestCaseIds).toHaveLength(0);
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

        comp.selectTab('test-cases');

        fixture.detectChanges();

        const passedBuildCharts = debugElement.queryAll(By.directive(TestCasePassedBuildsChartComponent)).map((d) => d.componentInstance);
        expect(passedBuildCharts).toHaveLength(3);

        // this test case is disabled and has no stats
        expect(passedBuildCharts[0].testCaseStats).toBeUndefined();
        expect(passedBuildCharts[0].totalParticipations).toBe(5);

        expect(passedBuildCharts[1].testCaseStats).toEqual(gradingStatistics.testCaseStatsMap!.testBubbleSort);
        expect(passedBuildCharts[1].totalParticipations).toBe(5);

        expect(passedBuildCharts[2].testCaseStats).toEqual(gradingStatistics.testCaseStatsMap!.testMergeSort);
        expect(passedBuildCharts[2].totalParticipations).toBe(5);
    });

    const sortAndTestTable = (table: string) => (headerElement: DebugElement, prop: string, dir: string) => {
        headerElement.nativeElement.click();
        fixture.detectChanges();

        expect(comp.tableSorts[table]).toEqual([{ prop, dir }]);
    };

    it('should sort test-case table', () => {
        initGradingComponent();

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const headerColumns = table.queryAll(By.css('.datatable-header-cell-wrapper'));

        const sortAndTest = sortAndTestTable('testCases');

        const weightHeader = headerColumns[1];
        sortAndTest(weightHeader, 'weight', 'asc');
        sortAndTest(weightHeader, 'weight', 'desc');

        const passedPercentHeader = headerColumns[6];
        sortAndTest(passedPercentHeader, 'passedPercent', 'asc');
        sortAndTest(passedPercentHeader, 'passedPercent', 'desc');
    });

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

    describe('test chart interaction', () => {
        it('should filter test case table correctly', () => {
            initGradingComponent();
            fixture.detectChanges();
            const testCasesDisplayedByCharts = comp.filteredTestCasesForCharts;
            const expectedTestCase = {
                id: 1,
                testName: 'testBubbleSort',
                active: true,
                weight: 1,
                bonusMultiplier: 1,
                bonusPoints: 0,
                visibility: Visibility.Always,
            };

            comp.filterByChart(1, ChartFilterType.TEST_CASES);

            expect(comp.filteredTestCasesForTable).toHaveLength(1);
            expect(comp.filteredTestCasesForTable).toEqual([expectedTestCase]);
            expect(comp.filteredTestCasesForCharts).toEqual(testCasesDisplayedByCharts);
        });

        it('should update test case accordingly if modified while chart filtering', () => {
            initGradingComponent();
            fixture.detectChanges();
            comp.filterByChart(1, ChartFilterType.TEST_CASES);
            fixture.detectChanges();
            const table = debugElement.query(By.css(testCaseTableId));
            const editingInputs = table.queryAll(By.css(tableEditingInput));
            expect(editingInputs).toHaveLength(3);

            const weightInput = editingInputs[0].nativeElement;
            expect(weightInput).not.toBeNull();
            weightInput.focus();

            // Set new weight.
            weightInput.value = '40';
            weightInput.dispatchEvent(new Event('blur'));

            const multiplierInput = editingInputs[1].nativeElement;
            expect(multiplierInput).not.toBeNull();
            multiplierInput.focus();

            // Set new multiplier.
            multiplierInput.value = '4';
            multiplierInput.dispatchEvent(new Event('blur'));

            const bonusInput = editingInputs[2].nativeElement;
            expect(bonusInput).not.toBeNull();
            bonusInput.focus();

            // Set new bonus.
            bonusInput.value = '7';
            bonusInput.dispatchEvent(new Event('blur'));
            fixture.detectChanges();

            const visibleTestCase = {
                id: 1,
                testName: 'testBubbleSort',
                active: true,
                weight: 40,
                bonusMultiplier: 4,
                bonusPoints: 7,
                visibility: Visibility.Always,
            };

            expect(comp.filteredTestCasesForTable).toHaveLength(1);
            expect(comp.filteredTestCasesForTable).toEqual([visibleTestCase]);
            expect(comp.filteredTestCasesForCharts).toContainEqual(visibleTestCase);

            // we reset the table
            comp.filterByChart(-5, ChartFilterType.TEST_CASES);
            const expectedTestCases = testCases1.filter((testCase) => testCase.active === true).map((testCase) => (testCase.id !== 1 ? testCase : visibleTestCase));

            expect(comp.filteredTestCasesForTable).toEqual(expectedTestCases);
        });

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
