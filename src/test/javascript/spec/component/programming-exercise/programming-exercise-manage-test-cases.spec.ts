import { async, ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import * as moment from 'moment';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import * as sinonChai from 'sinon-chai';
import { sortBy as _sortBy } from 'lodash';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, Subject } from 'rxjs';
import { ActivatedRoute, Params } from '@angular/router';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { CookieService } from 'ngx-cookie';
import { JhiAlertService } from 'ng-jhipster';
import * as chai from 'chai';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { ArtemisTestModule } from '../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { MockActivatedRoute, MockCookieService, MockProgrammingExerciseService, MockSyncStorage } from '../../mocks';
import { MockProgrammingExerciseTestCaseService } from '../../mocks/mock-programming-exercise-test-case.service';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisProgrammingExerciseTestCaseModule } from 'app/entities/programming-exercise/test-cases/programming-exercise-test-case.module';
import { expectElementToBeDisabled, expectElementToBeEnabled, getElement } from '../../utils/general.utils';
import { ProgrammingExerciseWebsocketService } from 'app/entities/programming-exercise/services/programming-exercise-websocket.service';
import { MockProgrammingExerciseWebsocketService } from '../../mocks/mock-programming-exercise-websocket.service';
import { ProgrammingBuildRunService } from 'app/programming-submission/programming-build-run.service';
import { MockProgrammingBuildRunService } from '../../mocks/mock-programming-build-run.service';
import { FeatureToggleService } from 'app/feature-toggle/feature-toggle.service';
import { MockFeatureToggleService } from '../../mocks/mock-feature-toggle-service';
import { EditableField, ProgrammingExerciseManageTestCasesComponent } from 'app/entities/programming-exercise/test-cases';
import { ProgrammingExerciseService, ProgrammingExerciseTestCaseService, ProgrammingExerciseTestCaseStateDTO } from 'app/entities/programming-exercise/services';

chai.use(sinonChai);
const expect = chai.expect;

// TODO: Since the update to v.16 all tests for the ngx-swimlane table need to add a 0 tick to avoid the ViewDestroyedError.
// The issue is a manual call to changeDetector.detectChanges that is triggered on a timeout.
describe('ProgrammingExerciseManageTestCases', () => {
    let comp: ProgrammingExerciseManageTestCasesComponent;
    let fixture: ComponentFixture<ProgrammingExerciseManageTestCasesComponent>;
    let debugElement: DebugElement;

    let route: ActivatedRoute;
    let testCaseService: ProgrammingExerciseTestCaseService;
    let programmingExerciseService: ProgrammingExerciseService;

    let updateTestCasesStub: SinonStub;
    let notifyTestCasesSpy: SinonSpy;
    let testCasesChangedStub: SinonStub;
    let getExerciseTestCaseStateStub: SinonStub;
    let loadExerciseStub: SinonStub;
    let programmingExerciseWebsocketService: ProgrammingExerciseWebsocketService;

    let routeSubject: Subject<Params>;
    let testCasesChangedSubject: Subject<boolean>;
    let getExerciseTestCaseStateSubject: Subject<{ body: ProgrammingExerciseTestCaseStateDTO }>;

    const testCaseTableId = '#testCaseTable';
    const tableEditingInput = '.table-editable-field__input';
    const rowClass = 'datatable-body-row';
    const saveTestCasesButton = '#save-test-cases-button';
    const resetWeightsButton = '#reset-weights-button';
    const triggerSubmissionRunButton = '#trigger-all-button > button';
    const testCasesNoUnsavedChanges = '#test-case-status-no-unsaved-changes';
    const testCasesUnsavedChanges = '#test-case-status-unsaved-changes';
    const testCasesUpdated = '#test-case-status-updated';
    const testCasesNoUpdated = '#test-case-status-no-updated';

    const exerciseId = 1;
    const exercise = {
        id: exerciseId,
    } as ProgrammingExercise;
    const testCases1 = [
        { id: 1, testName: 'testBubbleSort', active: true, weight: 1, afterDueDate: false },
        { id: 2, testName: 'testMergeSort', active: true, weight: 1, afterDueDate: true },
        { id: 3, testName: 'otherTest', active: false, weight: 1, afterDueDate: false },
    ] as ProgrammingExerciseTestCase[];

    const getExerciseTestCasteStateDTO = (
        released: boolean,
        hasStudentResult: boolean,
        testCasesChanged: boolean,
        buildAndTestStudentSubmissionsAfterDueDate: moment.Moment | null,
    ) => ({
        body: {
            released,
            hasStudentResult,
            testCasesChanged,
            buildAndTestStudentSubmissionsAfterDueDate,
        },
    });

    const getSaveButton = () => {
        return getElement(debugElement, saveTestCasesButton);
    };

    const getResetButton = () => {
        return getElement(debugElement, resetWeightsButton);
    };

    const getTriggerButton = () => {
        return getElement(debugElement, triggerSubmissionRunButton);
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
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, ArtemisProgrammingExerciseTestCaseModule],
            providers: [
                JhiAlertService,
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: ProgrammingExerciseWebsocketService, useClass: MockProgrammingExerciseWebsocketService },
                { provide: ProgrammingExerciseTestCaseService, useClass: MockProgrammingExerciseTestCaseService },
                { provide: ProgrammingBuildRunService, useClass: MockProgrammingBuildRunService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CookieService, useClass: MockCookieService },
                { provide: ActivatedRoute, useClass: MockActivatedRoute },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseManageTestCasesComponent);
                debugElement = fixture.debugElement;
                comp = fixture.componentInstance as ProgrammingExerciseManageTestCasesComponent;

                testCaseService = debugElement.injector.get(ProgrammingExerciseTestCaseService);
                route = debugElement.injector.get(ActivatedRoute);
                programmingExerciseWebsocketService = debugElement.injector.get(ProgrammingExerciseWebsocketService);
                programmingExerciseService = debugElement.injector.get(ProgrammingExerciseService);

                updateTestCasesStub = stub(testCaseService, 'updateTestCase');
                notifyTestCasesSpy = spy(testCaseService, 'notifyTestCases');

                routeSubject = new Subject();
                (route as any).setSubject(routeSubject);

                testCasesChangedStub = stub(programmingExerciseWebsocketService, 'getTestCaseState');
                getExerciseTestCaseStateStub = stub(programmingExerciseService, 'getProgrammingExerciseTestCaseState');
                loadExerciseStub = stub(programmingExerciseService, 'find');

                routeSubject = new Subject();
                // @ts-ignore
                (route as MockActivatedRoute).setSubject(routeSubject);
                getExerciseTestCaseStateSubject = new Subject();

                testCasesChangedSubject = new Subject<boolean>();
                testCasesChangedStub.returns(testCasesChangedSubject);
                getExerciseTestCaseStateStub.returns(getExerciseTestCaseStateSubject);
                loadExerciseStub.returns(of({ body: exercise }));
            });
    }));

    afterEach(() => {
        notifyTestCasesSpy.restore();
        testCasesChangedStub.restore();
        getExerciseTestCaseStateStub.restore();
    });

    it('should create a datatable with the correct amount of rows when test cases come in (hide inactive tests)', fakeAsync(() => {
        comp.ngOnInit();
        routeSubject.next({ exerciseId });
        getExerciseTestCaseStateSubject.next(getExerciseTestCasteStateDTO(true, true, false, moment()));

        (testCaseService as any).next(testCases1);

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const rows = table.queryAll(By.css(rowClass));

        expect(comp.testCases).to.deep.equal(testCases1);
        expect(rows).to.have.lengthOf(testCases1.filter(({ active }) => active).length);

        const saveWeightsButton = debugElement.query(By.css(saveTestCasesButton));
        expect(saveWeightsButton).to.exist;
        expect(saveWeightsButton.nativeElement.disabled).to.be.true;

        tick();
        fixture.destroy();
    }));

    it('should create a datatable with the correct amount of rows when test cases come in (show inactive tests)', fakeAsync(() => {
        comp.ngOnInit();
        comp.showInactive = true;
        routeSubject.next({ exerciseId });
        getExerciseTestCaseStateSubject.next(getExerciseTestCasteStateDTO(true, true, false, moment()));

        (testCaseService as any).next(testCases1);

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const rows = table.queryAll(By.css(rowClass));

        expect(comp.testCases).to.deep.equal(testCases1);
        expect(rows).to.have.lengthOf(testCases1.length);

        const saveWeightsButton = debugElement.query(By.css(saveTestCasesButton));
        expect(saveWeightsButton).to.exist;
        expect(saveWeightsButton.nativeElement.disabled).to.be.true;

        tick();
        fixture.destroy();
    }));

    it('should enter edit mode when an edit button is clicked to edit weights', fakeAsync(() => {
        comp.ngOnInit();
        comp.showInactive = true;
        routeSubject.next({ exerciseId });
        getExerciseTestCaseStateSubject.next(getExerciseTestCasteStateDTO(true, true, false, moment()));

        const orderedTests = _sortBy(testCases1, 'testName');

        (testCaseService as any).next(testCases1);

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const editIcons = table.queryAll(By.css('.table-editable-field__edit'));
        expect(editIcons).to.have.lengthOf(testCases1.length);
        editIcons[0].nativeElement.click();

        fixture.detectChanges();

        let editingInput = debugElement.query(By.css(tableEditingInput)).nativeElement;

        expect(editingInput).to.exist;
        expect(comp.editing).to.deep.equal([orderedTests[0], EditableField.WEIGHT]);

        // Set new weight.
        editingInput.value = '20';
        editingInput.dispatchEvent(new Event('blur'));

        fixture.detectChanges();

        expect(comp.changedTestCaseIds).to.deep.equal([orderedTests[0].id]);

        // Trigger button should be disabled.
        let triggerButton = getTriggerButton();
        expectElementToBeDisabled(triggerButton);

        // Save weight.
        updateTestCasesStub.returns(of([{ ...orderedTests[0], weight: 20 }]));
        const saveButton = getSaveButton();
        expectElementToBeEnabled(saveButton);
        saveButton.click();

        fixture.detectChanges();

        const testThatWasUpdated = _sortBy(comp.testCases, 'testName')[0];
        editingInput = debugElement.query(By.css(tableEditingInput));
        expect(updateTestCasesStub).to.have.been.calledOnceWithExactly(exerciseId, [{ id: testThatWasUpdated.id, afterDueDate: testThatWasUpdated.afterDueDate, weight: '20' }]);
        expect(editingInput).not.to.exist;
        expect(testThatWasUpdated.weight).to.equal(20);
        expect(comp.changedTestCaseIds).to.have.lengthOf(0);

        testCasesChangedSubject.next(true);
        // Trigger button is now enabled because the tests were saved.
        expect(comp.hasUpdatedTestCases).to.be.true;

        fixture.detectChanges();

        triggerButton = getTriggerButton();
        expectElementToBeEnabled(triggerButton);

        tick();
        fixture.destroy();
        flush();

        tick();
        fixture.destroy();
        flush();
    }));

    it('should be able to update the value of the afterDueDate boolean', async () => {
        comp.ngOnInit();
        comp.showInactive = true;
        routeSubject.next({ exerciseId });
        getExerciseTestCaseStateSubject.next(getExerciseTestCasteStateDTO(true, true, false, moment()));

        const orderedTests = _sortBy(testCases1, 'testName');

        (testCaseService as any).next(testCases1);

        fixture.detectChanges();
        await fixture.whenStable();

        const table = debugElement.query(By.css(testCaseTableId));
        const checkboxes = table.queryAll(By.css('.table-editable-field__checkbox'));
        expect(checkboxes).to.have.lengthOf(testCases1.length);
        checkboxes[0].nativeElement.click();

        await fixture.whenStable();
        fixture.detectChanges();

        expect(comp.changedTestCaseIds).to.deep.equal([orderedTests[0].id]);

        // The UI should now show that there are unsaved changes.
        expect(getUnsavedChangesBadge()).to.exist;
        expect(getNoUnsavedChangesBadge()).not.to.exist;

        // Save weight.
        updateTestCasesStub.returns(of({ ...orderedTests[0], afterDueDate: true }));
        const saveTestCases = debugElement.query(By.css(saveTestCasesButton));
        expect(saveTestCases).to.exist;
        expect(saveTestCases.nativeElement.disabled).to.be.false;
        saveTestCases.nativeElement.click();

        fixture.detectChanges();

        const testThatWasUpdated = _sortBy(comp.testCases, 'testName')[0];
        expect(updateTestCasesStub).to.have.been.calledOnceWithExactly(exerciseId, [
            { id: testThatWasUpdated.id, weight: testThatWasUpdated.weight, afterDueDate: testThatWasUpdated.afterDueDate },
        ]);

        await new Promise(resolve => setTimeout(resolve));
        fixture.destroy();
    });

    it('should not be able to update the value of the afterDueDate boolean if the programming exercise does not have a buildAndTestAfterDueDate', async () => {
        comp.ngOnInit();
        comp.showInactive = true;
        routeSubject.next({ exerciseId });
        getExerciseTestCaseStateSubject.next(getExerciseTestCasteStateDTO(true, true, false, null));

        const orderedTests = _sortBy(testCases1, 'testName');

        (testCaseService as any).next(testCases1);

        fixture.detectChanges();
        await fixture.whenStable();

        const table = debugElement.query(By.css(testCaseTableId));
        const checkboxes = table.queryAll(By.css('.table-editable-field__checkbox'));
        expect(checkboxes).to.have.lengthOf(testCases1.length);
        expect(checkboxes.every(({ nativeElement: { disabled } }) => disabled)).to.be.true;

        fixture.destroy();
    });

    it('should show the updatedTests badge when the exercise is released and has student results', fakeAsync(() => {
        comp.ngOnInit();
        routeSubject.next({ exerciseId });
        // @ts-ignore
        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases1);
        getExerciseTestCaseStateSubject.next(getExerciseTestCasteStateDTO(true, true, false, moment()));

        fixture.detectChanges();

        expect(getNoUnsavedChangesBadge()).to.exist;
        expect(getNoUpdatedTestCaseBadge()).to.exist;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not show the updatedTests badge when the exercise is released and has no student results', fakeAsync(() => {
        comp.ngOnInit();
        routeSubject.next({ exerciseId });
        // @ts-ignore
        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases1);
        getExerciseTestCaseStateSubject.next(getExerciseTestCasteStateDTO(true, false, false, moment()));

        fixture.detectChanges();

        expect(getNoUnsavedChangesBadge()).to.exist;
        expect(getNoUpdatedTestCaseBadge()).not.to.exist;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should not show the updatedTests badge when the exercise is not released and has student results (edge case)', fakeAsync(() => {
        comp.ngOnInit();
        routeSubject.next({ exerciseId });
        // @ts-ignore
        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases1);
        getExerciseTestCaseStateSubject.next(getExerciseTestCasteStateDTO(false, true, false, moment()));

        fixture.detectChanges();

        expect(getNoUnsavedChangesBadge()).to.exist;
        expect(getNoUpdatedTestCaseBadge()).not.to.exist;

        tick();
        fixture.destroy();
        flush();
    }));

    it('should show that there are updated test cases if the getExerciseTestCaseState call returns this info', fakeAsync(() => {
        comp.ngOnInit();
        routeSubject.next({ exerciseId });
        // @ts-ignore
        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases1);
        getExerciseTestCaseStateSubject.next(getExerciseTestCasteStateDTO(true, true, true, moment()));

        fixture.detectChanges();

        expect(getUpdatedTestCaseBadge()).to.exist;
        expect(getNoUpdatedTestCaseBadge()).not.to.exist;

        tick();
        fixture.destroy();
        flush();
    }));
});
