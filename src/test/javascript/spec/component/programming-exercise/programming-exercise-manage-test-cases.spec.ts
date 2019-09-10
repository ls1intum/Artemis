import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { MockComponent } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import * as sinonChai from 'sinon-chai';
import { sortBy as _sortBy } from 'lodash';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, Subject } from 'rxjs';
import { ActivatedRoute, Params } from '@angular/router';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { CookieService } from 'ngx-cookie';
import { JhiAlertService } from 'ng-jhipster';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import * as chai from 'chai';
import { EditableField, ProgrammingExerciseManageTestCasesComponent, ProgrammingExerciseTestCaseService } from 'app/entities/programming-exercise';
import { ArtemisTestModule } from '../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { MockActivatedRoute, MockCookieService, MockSyncStorage } from '../../mocks';
import { MockProgrammingExerciseTestCaseService } from '../../mocks/mock-programming-exercise-test-case.service';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';
import { ArtemisSharedModule, JhiAlertComponent } from 'app/shared';
import { ArtemisTableModule } from 'app/components/table/table.module';
import { ArtemisProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ArtemisCoreModule } from 'app/core';
import { ArtemisProgrammingExerciseTestCaseModule } from 'app/entities/programming-exercise/test-cases/programming-exercise-test-case.module';
import { elementIsDisabled, expectElementToBeDisabled, expectElementToBeEnabled, getElement } from '../../utils/general.utils';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseManageTestCases', () => {
    let comp: ProgrammingExerciseManageTestCasesComponent;
    let fixture: ComponentFixture<ProgrammingExerciseManageTestCasesComponent>;
    let debugElement: DebugElement;

    let route: ActivatedRoute;
    let testCaseService: ProgrammingExerciseTestCaseService;

    let updateTestCasesStub: SinonStub;
    let notifyTestCasesSpy: SinonSpy;

    let routeSubject: Subject<Params>;

    const testCaseTableId = '#testCaseTable';
    const tableEditingInput = '.table-editable-field__input';
    const rowClass = 'datatable-body-row';
    const saveTestCasesButton = '#save-test-cases-button';
    const resetWeightsButton = '#reset-weights-button';
    const triggerSubmissionRunButton = '#trigger-all-button';

    const exerciseId = 1;
    const testCases1 = [
        { id: 1, testName: 'testBubbleSort', active: true, weight: 1, afterDueDate: false },
        { id: 2, testName: 'testMergeSort', active: true, weight: 1, afterDueDate: true },
        { id: 3, testName: 'otherTest', active: false, weight: 1, afterDueDate: false },
    ] as ProgrammingExerciseTestCase[];
    const testCases2 = [
        { id: 4, testName: 'testBubbleSort', active: false, weight: 2 },
        { id: 5, testName: 'testMergeSort', active: true, weight: 2 },
        { id: 6, testName: 'otherTest', active: true, weight: 2 },
    ] as ProgrammingExerciseTestCase[];

    const getSaveButton = () => {
        return getElement(debugElement, saveTestCasesButton);
    };

    const getResetButton = () => {
        return getElement(debugElement, resetWeightsButton);
    };

    const getTriggerButton = () => {
        return getElement(debugElement, triggerSubmissionRunButton);
    };

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, ArtemisProgrammingExerciseTestCaseModule],
            providers: [
                JhiAlertService,
                { provide: ProgrammingExerciseTestCaseService, useClass: MockProgrammingExerciseTestCaseService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: CookieService, useClass: MockCookieService },
                { provide: ActivatedRoute, useClass: MockActivatedRoute },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseManageTestCasesComponent);
                debugElement = fixture.debugElement;
                comp = fixture.componentInstance as ProgrammingExerciseManageTestCasesComponent;

                testCaseService = debugElement.injector.get(ProgrammingExerciseTestCaseService);
                route = debugElement.injector.get(ActivatedRoute);

                updateTestCasesStub = stub(testCaseService, 'updateTestCase');
                notifyTestCasesSpy = spy(testCaseService, 'notifyTestCases');

                routeSubject = new Subject();
                // @ts-ignore
                (route as MockActivatedRoute).setSubject(routeSubject);
            });
    }));

    afterEach(() => {
        notifyTestCasesSpy.restore();

        routeSubject.complete();
        routeSubject = new Subject();
        // @ts-ignore
        (route as MockActivatedRoute).setSubject(routeSubject);
    });

    it('should create a datatable with the correct amount of rows when test cases come in (hide inactive tests)', () => {
        comp.ngOnInit();
        routeSubject.next({ exerciseId });

        // @ts-ignore
        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases1);

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const rows = table.queryAll(By.css(rowClass));

        expect(comp.testCases).to.deep.equal(testCases1);
        expect(rows).to.have.lengthOf(testCases1.filter(({ active }) => active).length);

        const saveWeightsButton = debugElement.query(By.css(saveTestCasesButton));
        expect(saveWeightsButton).to.exist;
        expect(saveWeightsButton.nativeElement.disabled).to.be.true;
    });

    it('should create a datatable with the correct amount of rows when test cases come in (show inactive tests)', () => {
        comp.ngOnInit();
        comp.showInactive = true;
        routeSubject.next({ exerciseId });

        // @ts-ignore
        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases1);

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const rows = table.queryAll(By.css(rowClass));

        expect(comp.testCases).to.deep.equal(testCases1);
        expect(rows).to.have.lengthOf(testCases1.length);

        const saveWeightsButton = debugElement.query(By.css(saveTestCasesButton));
        expect(saveWeightsButton).to.exist;
        expect(saveWeightsButton.nativeElement.disabled).to.be.true;
    });

    it('should enter edit mode when an edit button is clicked to edit weights', () => {
        comp.ngOnInit();
        comp.showInactive = true;
        routeSubject.next({ exerciseId });

        let orderedTests = _sortBy(testCases1, 'testName');

        // @ts-ignore
        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases1);

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

        // Trigger button is now enabled because the tests were saved.
        expect(comp.hasUpdatedTestCases).to.be.true;
        triggerButton = getTriggerButton();
        expectElementToBeEnabled(triggerButton);
    });

    it('should be able to update the value of the afterDueDate boolean', async () => {
        comp.ngOnInit();
        comp.showInactive = true;
        routeSubject.next({ exerciseId });

        const orderedTests = _sortBy(testCases1, 'testName');

        // @ts-ignore
        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases1);

        fixture.detectChanges();
        await fixture.whenStable();

        const table = debugElement.query(By.css(testCaseTableId));
        const checkboxes = table.queryAll(By.css('.table-editable-field__checkbox'));
        expect(checkboxes).to.have.lengthOf(testCases1.length);
        checkboxes[0].nativeElement.click();

        await fixture.whenStable();
        fixture.detectChanges();

        expect(comp.changedTestCaseIds).to.deep.equal([orderedTests[0].id]);

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
    });
});
