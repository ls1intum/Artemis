import { async, ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import * as moment from 'moment';
import { Moment } from 'moment';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import * as sinonChai from 'sinon-chai';
import { sortBy as _sortBy } from 'lodash';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, Subject } from 'rxjs';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { CookieService } from 'ngx-cookie-service';
import { JhiAlertService } from 'ng-jhipster';
import * as chai from 'chai';
import { ArtemisTestModule } from '../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockProgrammingExerciseGradingService } from '../../helpers/mocks/service/mock-programming-exercise-grading.service';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming-exercise-test-case.model';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseGradingModule } from 'app/exercises/programming/manage/grading/programming-exercise-grading.module';
import { expectElementToBeEnabled, getElement } from '../../helpers/utils/general.utils';
import { ProgrammingExerciseWebsocketService } from 'app/exercises/programming/manage/services/programming-exercise-websocket.service';
import { MockProgrammingExerciseWebsocketService } from '../../helpers/mocks/service/mock-programming-exercise-websocket.service';
import { ProgrammingBuildRunService } from 'app/exercises/programming/participate/programming-build-run.service';
import { MockProgrammingBuildRunService } from '../../helpers/mocks/service/mock-programming-build-run.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../helpers/mocks/service/mock-feature-toggle.service';
import { EditableField, ProgrammingExerciseConfigureGradingComponent } from 'app/exercises/programming/manage/grading/programming-exercise-configure-grading.component';
import { ProgrammingExerciseService, ProgrammingExerciseTestCaseStateDTO } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import {
    ProgrammingExerciseGradingService,
    ProgrammingExerciseTestCaseUpdate,
    StaticCodeAnalysisCategoryUpdate,
} from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { MockActivatedRouteWithSubjects } from '../../helpers/mocks/activated-route/mock-activated-route-with-subjects';
import { MockCookieService } from '../../helpers/mocks/service/mock-cookie.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { ProgrammingExerciseGradingStatistics } from 'app/entities/programming-exercise-test-case-statistics.model';
import { CategoryIssuesChartComponent } from 'app/exercises/programming/manage/grading/charts/category-issues-chart.component';
import { TestCasePassedBuildsChartComponent } from 'app/exercises/programming/manage/grading/charts/test-case-passed-builds-chart.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseConfigureGradingComponent', () => {
    let comp: ProgrammingExerciseConfigureGradingComponent;
    let fixture: ComponentFixture<ProgrammingExerciseConfigureGradingComponent>;
    let debugElement: DebugElement;

    let route: ActivatedRoute;
    let gradingService: ProgrammingExerciseGradingService;
    let programmingExerciseService: ProgrammingExerciseService;

    let updateTestCasesStub: SinonStub;
    let updateCategoriesStub: SinonStub;
    let resetTestCasesStub: SinonStub;
    let resetCategoriesStub: SinonStub;
    let notifyTestCasesSpy: SinonSpy;
    let testCasesChangedStub: SinonStub;
    let getExerciseTestCaseStateStub: SinonStub;
    let loadExerciseStub: SinonStub;
    let loadStatisticsStub: SinonStub;
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

    const getExerciseTestCasteStateDTO = (released: boolean, hasStudentResult: boolean, testCasesChanged: boolean, buildAndTestStudentSubmissionsAfterDueDate?: Moment) => ({
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

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, ArtemisProgrammingExerciseGradingModule],
            providers: [
                JhiAlertService,
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: ProgrammingExerciseWebsocketService, useClass: MockProgrammingExerciseWebsocketService },
                { provide: ProgrammingExerciseGradingService, useClass: MockProgrammingExerciseGradingService },
                { provide: ProgrammingBuildRunService, useClass: MockProgrammingBuildRunService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CookieService, useClass: MockCookieService },
                { provide: ActivatedRoute, useClass: MockActivatedRouteWithSubjects },
                { provide: Router, useClass: MockRouter },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
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

                updateTestCasesStub = stub(gradingService, 'updateTestCase');
                updateCategoriesStub = stub(gradingService, 'updateCodeAnalysisCategories');
                notifyTestCasesSpy = spy(gradingService, 'notifyTestCases');
                resetTestCasesStub = stub(gradingService, 'resetTestCases');
                resetCategoriesStub = stub(gradingService, 'resetCategories');
                loadStatisticsStub = stub(gradingService, 'getGradingStatistics');

                // @ts-ignore
                (router as MockRouter).setUrl('/');
                routeSubject = new Subject();
                // @ts-ignore
                (route as MockActivatedRouteWithSubjects).setSubject(routeSubject);

                testCasesChangedStub = stub(programmingExerciseWebsocketService, 'getTestCaseState');
                getExerciseTestCaseStateStub = stub(programmingExerciseService, 'getProgrammingExerciseTestCaseState');
                loadExerciseStub = stub(programmingExerciseService, 'find');

                getExerciseTestCaseStateSubject = new Subject();

                testCasesChangedSubject = new Subject<boolean>();
                testCasesChangedStub.returns(testCasesChangedSubject);
                getExerciseTestCaseStateStub.returns(getExerciseTestCaseStateSubject);

                loadStatisticsStub.returns(of(gradingStatistics));
                loadExerciseStub.returns(of({ body: exercise }));
            });
    }));

    afterEach(() => {
        notifyTestCasesSpy.restore();
        testCasesChangedStub.restore();
        getExerciseTestCaseStateStub.restore();
        loadStatisticsStub.restore();
    });

    const initGradingComponent = ({
        tab = 'test-cases',
        released = true,
        hasStudentResult = true,
        testCasesChanged = false,
        hasBuildAndTestAfterDueDate = true,
        buildAndTestAfterDueDate = moment(),
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

    it('should create a datatable with the correct amount of rows when test cases come in (hide inactive tests)', fakeAsync(() => {
        initGradingComponent();

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const rows = table.queryAll(By.css(rowClass));

        expect(comp.testCases).to.deep.equal(testCases1);
        expect(rows).to.have.lengthOf(testCases1.filter(({ active }) => active).length);

        const saveButton = debugElement.query(By.css(saveTableButton));
        expect(saveButton).to.exist;
        expect(saveButton.nativeElement.disabled).to.be.true;

        tick();
        fixture.destroy();
    }));

    it('should create a datatable with the correct amount of rows when test cases come in (show inactive tests)', fakeAsync(() => {
        initGradingComponent({ showInactive: true });

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const rows = table.queryAll(By.css(rowClass));

        expect(comp.testCases).to.deep.equal(testCases1);
        expect(rows).to.have.lengthOf(testCases1.length);

        const saveButton = debugElement.query(By.css(saveTableButton));
        expect(saveButton).to.exist;
        expect(saveButton.nativeElement.disabled).to.be.true;

        tick();
        fixture.destroy();
    }));

    it('should update test case when an input field is updated', fakeAsync(() => {
        initGradingComponent({ showInactive: true });

        fixture.detectChanges();

        const orderedTests = _sortBy(testCases1, 'testName');

        const table = debugElement.query(By.css(testCaseTableId));

        // get first weight input
        const editingInputs = table.queryAll(By.css(tableEditingInput));
        expect(editingInputs).to.have.lengthOf(testCases1.length * 3);

        const weightInput = editingInputs[0].nativeElement;
        expect(weightInput).to.exist;
        weightInput.focus();

        // Set new weight.
        weightInput.value = '20';
        weightInput.dispatchEvent(new Event('blur'));

        const multiplierInput = editingInputs[1].nativeElement;
        expect(multiplierInput).to.exist;
        multiplierInput.focus();

        // Set new multiplier.
        multiplierInput.value = '2';
        multiplierInput.dispatchEvent(new Event('blur'));

        const bonusInput = editingInputs[2].nativeElement;
        expect(bonusInput).to.exist;
        bonusInput.focus();

        // Set new bonus.
        bonusInput.value = '1';
        bonusInput.dispatchEvent(new Event('blur'));

        fixture.detectChanges();

        expect(comp.changedTestCaseIds).to.deep.equal([orderedTests[0].id]);

        // Save weight.
        updateTestCasesStub.returns(of([{ ...orderedTests[0], weight: 20, bonusMultiplier: 2, bonusPoints: 1 }]));
        const saveButton = getSaveButton();
        expectElementToBeEnabled(saveButton);
        saveButton.click();

        fixture.detectChanges();

        let testThatWasUpdated = _sortBy(comp.testCases, 'testName')[0];
        expect(updateTestCasesStub).to.have.been.calledOnceWithExactly(exerciseId, [
            new ProgrammingExerciseTestCaseUpdate(testThatWasUpdated.id, 20, 1, 2, testThatWasUpdated.visibility),
        ]);
        expect(testThatWasUpdated.weight).to.equal(20);
        expect(testThatWasUpdated.bonusMultiplier).to.equal(2);
        expect(testThatWasUpdated.bonusPoints).to.equal(1);
        expect(comp.changedTestCaseIds).to.have.lengthOf(0);

        testCasesChangedSubject.next(true);
        // Trigger button is now enabled because the tests were saved.
        expect(comp.hasUpdatedGradingConfig).to.be.true;

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

        expect(comp.changedTestCaseIds).to.deep.equal([orderedTests[0].id]);

        tick();
        expect(multiplierInput.value).to.equal('1');
        expect(bonusInput.value).to.equal('1');

        // Save weight.
        updateTestCasesStub.reset();
        updateTestCasesStub.returns(of([{ ...orderedTests[0], weight: 20, bonusMultiplier: 1, bonusPoints: 1 }]));
        saveButton.click();

        fixture.detectChanges();

        testThatWasUpdated = _sortBy(comp.testCases, 'testName')[0];
        expect(updateTestCasesStub).to.have.been.calledOnceWithExactly(exerciseId, [
            new ProgrammingExerciseTestCaseUpdate(testThatWasUpdated.id, 20, 1, 1, testThatWasUpdated.visibility),
        ]);
        expect(testThatWasUpdated.weight).to.equal(20);
        expect(testThatWasUpdated.bonusMultiplier).to.equal(1);
        expect(testThatWasUpdated.bonusPoints).to.equal(1);
        expect(comp.changedTestCaseIds).to.have.lengthOf(0);

        fixture.detectChanges();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should show error alert when test case weights are less or equal zero', () => {
        initGradingComponent({ showInactive: true });

        fixture.detectChanges();

        const orderedTests = _sortBy(testCases1, 'testName');

        const table = debugElement.query(By.css(testCaseTableId));

        // get all input fields
        const editingInputs = table.queryAll(By.css(tableEditingInput));
        expect(editingInputs).to.have.lengthOf(testCases1.length * 3);
        // Set only the weight input fields to 0 of all test cases
        for (let i = 0; i < editingInputs.length; i += 3) {
            const weightInput = editingInputs[i].nativeElement;
            expect(weightInput).to.exist;
            weightInput.focus();

            // Set new weight.
            weightInput.value = '0';
            weightInput.dispatchEvent(new Event('blur'));
        }

        fixture.detectChanges();
        expect(comp.changedTestCaseIds).to.deep.equal(orderedTests.map((test) => test.id));

        // Mock which should be return from update service call
        const updateTestCases = orderedTests.map((test) => {
            return { ...test, weight: 0, bonusMultiplier: 2, bonusPoints: 1 };
        });
        // Save weight.
        updateTestCasesStub.returns(of(updateTestCases));

        // Initialize spy for error alert
        const alertService = TestBed.inject(JhiAlertService);
        const alertServiceSpy = spy(alertService, 'error');

        const saveButton = getSaveButton();
        expectElementToBeEnabled(saveButton);
        saveButton.click();

        expect(alertServiceSpy).to.be.calledOnce;
    });

    it('should be able to update the value of the visibility', async () => {
        initGradingComponent({ showInactive: true });

        fixture.detectChanges();
        await fixture.whenStable();

        const orderedTests = _sortBy(testCases1, 'testName');

        const table = debugElement.query(By.css(testCaseTableId));
        const dropdowns = table.queryAll(By.all()).filter((elem) => elem.name === 'select');
        expect(dropdowns).to.have.lengthOf(testCases1.length);
        dropdowns[0].nativeElement.value = Visibility.AfterDueDate;
        dropdowns[0].nativeElement.dispatchEvent(new Event('change'));

        await fixture.whenStable();
        fixture.detectChanges();

        expect(comp.changedTestCaseIds).to.deep.equal([orderedTests[0].id]);

        // The UI should now show that there are unsaved changes.
        expect(getUnsavedChangesBadge()).to.exist;
        expect(getNoUnsavedChangesBadge()).not.to.exist;

        // Save weight.
        updateTestCasesStub.returns(of({ ...orderedTests[0], afterDueDate: true }));
        const saveTestCases = debugElement.query(By.css(saveTableButton));
        expect(saveTestCases).to.exist;
        expect(saveTestCases.nativeElement.disabled).to.be.false;
        saveTestCases.nativeElement.click();

        fixture.detectChanges();

        const testThatWasUpdated = _sortBy(comp.testCases, 'testName')[0];
        expect(updateTestCasesStub).to.have.been.calledOnceWithExactly(exerciseId, [ProgrammingExerciseTestCaseUpdate.from(testThatWasUpdated)]);

        await new Promise((resolve) => setTimeout(resolve));
        fixture.destroy();
    });

    it('should also be able to select after due date as visibility option if the programming exercise does not have a buildAndTestAfterDueDate', async () => {
        initGradingComponent({ hasBuildAndTestAfterDueDate: false, showInactive: true });

        fixture.detectChanges();
        await fixture.whenStable();

        const table = debugElement.query(By.css(testCaseTableId));
        const options = table.queryAll(By.all()).filter((elem) => elem.name === 'option');
        // three options for each test case should still be available
        expect(options).to.have.lengthOf(testCases1.length * 3);

        fixture.destroy();
    });

    it('should show the updatedTests badge when the exercise is released and has student results', fakeAsync(() => {
        initGradingComponent();

        fixture.detectChanges();

        expect(getNoUnsavedChangesBadge()).to.exist;
        expect(getNoUpdatedTestCaseBadge()).to.exist;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not show the updatedTests badge when the exercise is released and has no student results', fakeAsync(() => {
        initGradingComponent({ released: true, hasStudentResult: false });

        fixture.detectChanges();

        expect(getNoUnsavedChangesBadge()).to.exist;
        expect(getNoUpdatedTestCaseBadge()).not.to.exist;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not show the updatedTests badge when the exercise is not released and has student results (edge case)', fakeAsync(() => {
        initGradingComponent({ released: false, hasStudentResult: true });

        fixture.detectChanges();

        expect(getNoUnsavedChangesBadge()).to.exist;
        expect(getNoUpdatedTestCaseBadge()).not.to.exist;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should show that there are updated test cases if the testCasesChanged flat is set', fakeAsync(() => {
        initGradingComponent({ testCasesChanged: true });

        fixture.detectChanges();

        expect(getUpdatedTestCaseBadge()).to.exist;
        expect(getNoUpdatedTestCaseBadge()).not.to.exist;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should reset all test cases when the reset button is clicked', fakeAsync(() => {
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
        updateTestCasesStub.returns(of(updatedTestCases));

        // Save tests.
        comp.saveTestCases();

        fixture.detectChanges();

        expect(updateTestCasesStub).to.have.been.calledOnce;

        expect(comp.changedTestCaseIds).to.have.lengthOf(0);
        testCasesChangedSubject.next(true);

        // Reset button is now enabled because the tests were saved.
        expect(comp.hasUpdatedGradingConfig).to.be.true;

        fixture.detectChanges();

        resetTestCasesStub.returns(of(testCases1));

        const resetButton = getResetButton();
        expectElementToBeEnabled(resetButton);
        resetButton.click();

        fixture.detectChanges();

        expect(resetTestCasesStub).to.have.been.calledOnceWithExactly(exerciseId);
        expect(comp.testCases).to.deep.equal(testCases1);
        expect(comp.changedTestCaseIds).to.have.lengthOf(0);

        tick();
        fixture.destroy();
        flush();
    }));

    it('should reset all categories when the reset button is clicked', fakeAsync(() => {
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
        updateCategoriesStub.returns(of(updatedCategories));

        // Save tests.
        comp.saveCategories();

        fixture.detectChanges();

        expect(updateCategoriesStub).to.have.been.calledOnce;
        expect(comp.changedCategoryIds).to.have.lengthOf(0);

        testCasesChangedSubject.next(true);

        // Reset button is now enabled because the categories were saved.
        expect(comp.hasUpdatedGradingConfig).to.be.true;

        fixture.detectChanges();

        resetCategoriesStub.returns(of(codeAnalysisCategories1));

        // Reset the stub to ensure that it function is called exactly once on category reset
        loadStatisticsStub.reset();

        const resetButton = getResetButton();
        expectElementToBeEnabled(resetButton);
        resetButton.click();

        fixture.detectChanges();

        expect(resetCategoriesStub).to.have.been.calledOnceWithExactly(exerciseId);
        expect(loadStatisticsStub).to.have.been.calledOnceWithExactly(exerciseId);
        expect(comp.staticCodeAnalysisCategories).to.deep.equal(codeAnalysisCategories1);
        expect(comp.changedCategoryIds).to.have.lengthOf(0);

        tick();
        fixture.destroy();
        flush();
    }));

    it('should update sca category when an input field is updated', fakeAsync(() => {
        initGradingComponent({ tab: 'code-analysis' });

        fixture.detectChanges();

        const table = debugElement.query(By.css(codeAnalysisTableId));

        const gradedCategories = comp.staticCodeAnalysisCategories.filter((category) => category.state === StaticCodeAnalysisCategoryState.Graded);

        // get inputs
        const editingInputs = table.queryAll(By.css(tableEditingInput));
        expect(editingInputs).to.have.lengthOf(gradedCategories.length * 2);

        const penaltyInput = editingInputs[0].nativeElement;
        expect(penaltyInput).to.exist;
        penaltyInput.focus();

        // Set new penalty.
        penaltyInput.value = '20';
        penaltyInput.dispatchEvent(new Event('blur'));

        const maxPenaltyInput = editingInputs[1].nativeElement;
        expect(maxPenaltyInput).to.exist;
        maxPenaltyInput.focus();

        // Set new max penalty.
        maxPenaltyInput.value = '100';
        maxPenaltyInput.dispatchEvent(new Event('blur'));

        fixture.detectChanges();

        expect(comp.changedCategoryIds).to.deep.equal([gradedCategories[0].id]);

        const updatedCategory: StaticCodeAnalysisCategory = { ...gradedCategories[0], penalty: 20, maxPenalty: 100 };

        // Save weight.
        updateCategoriesStub.returns(of([updatedCategory]));
        const saveButton = getSaveButton();
        expectElementToBeEnabled(saveButton);
        saveButton.click();

        fixture.detectChanges();

        expect(updateCategoriesStub).to.have.been.calledOnceWithExactly(exerciseId, [StaticCodeAnalysisCategoryUpdate.from(updatedCategory)]);

        const categoryThatWasUpdated = comp.staticCodeAnalysisCategories.find((category) => category.id === updatedCategory.id)!;
        expect(categoryThatWasUpdated.penalty).to.equal(20);
        expect(categoryThatWasUpdated.maxPenalty).to.equal(100);
        expect(comp.changedCategoryIds).to.have.lengthOf(0);

        testCasesChangedSubject.next(true);
        // Trigger button is now enabled because the tests were saved.
        expect(comp.hasUpdatedGradingConfig).to.be.true;

        fixture.detectChanges();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should load and calculate grading statistics correctly', fakeAsync(() => {
        initGradingComponent({ tab: 'code-analysis' });

        fixture.detectChanges();

        expect(loadStatisticsStub).to.have.been.calledWithExactly(exerciseId);

        expect(comp.maxIssuesPerCategory).to.equal(5);
        expect(comp.gradingStatistics).to.deep.equal(gradingStatistics);

        fixture.detectChanges();
        tick();

        const issueCharts = debugElement.queryAll(By.directive(CategoryIssuesChartComponent)).map((d) => d.componentInstance);

        expect(issueCharts[0].columns).to.have.lengthOf(11);
        expect(issueCharts[0].columns[0].tooltip).to.equal('2 students have 1 issue.');
        expect(issueCharts[0].columns[1].tooltip).to.equal('2 students have 2 issues.');
        expect(issueCharts[0].columns[2].tooltip).to.equal('1 student has 3 issues.');

        expect(issueCharts[1].columns).to.have.lengthOf(11);
        expect(issueCharts[1].columns[1].tooltip).to.equal('1 student has 2 issues.');
        expect(issueCharts[1].columns[4].tooltip).to.equal('1 student has 5 issues.');

        comp.selectTab('test-cases');

        fixture.detectChanges();
        tick();

        const percentageCharts = debugElement.queryAll(By.directive(TestCasePassedBuildsChartComponent)).map((d) => d.componentInstance);

        expect(percentageCharts[0].tooltip).to.equal('0% passed, 0% failed, 100% not executed of 5 students.');
        expect(percentageCharts[1].tooltip).to.equal('40% passed, 60% failed of 5 students.');
        expect(percentageCharts[2].tooltip).to.equal('20% passed, 80% failed of 5 students.');

        tick();
        fixture.destroy();
        flush();
    }));

    const sortAndTestTable = (table: string) => (headerElement: DebugElement, prop: string, dir: string) => {
        headerElement.nativeElement.click();
        fixture.detectChanges();

        const sortIcon = getElement(headerElement, 'fa-icon').attributes['ng-reflect-icon'].value;

        expect(comp.tableSorts[table]).to.deep.equal([{ prop, dir }]);
        expect(sortIcon).to.equal(dir === 'asc' ? 'sort-up' : 'sort-down');
    };

    it('should sort test-case table', fakeAsync(() => {
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

        tick();
        fixture.destroy();
        flush();
    }));

    it('should sort code-analysis table', fakeAsync(() => {
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

        tick();
        fixture.destroy();
        flush();
    }));
});
