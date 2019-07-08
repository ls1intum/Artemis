import { ComponentFixture, TestBed, async, inject, flush, fakeAsync, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import * as sinonChai from 'sinon-chai';
import { sortBy as _sortBy } from 'lodash';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, Subject } from 'rxjs';
import { ActivatedRoute, Params } from '@angular/router';
import { SinonStub, SinonSpy, spy, stub } from 'sinon';
import { CookieService } from 'ngx-cookie';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import * as chai from 'chai';
import { ProgrammingExerciseManageTestCasesComponent, ProgrammingExerciseTestCaseService } from 'app/entities/programming-exercise';
import { ArTEMiSTestModule } from '../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { MockActivatedRoute, MockCookieService, MockSyncStorage } from '../../mocks';
import { MockProgrammingExerciseTestCaseService } from '../../mocks/mock-programming-exercise-test-case.service';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseManageTestCases', () => {
    let comp: ProgrammingExerciseManageTestCasesComponent;
    let fixture: ComponentFixture<ProgrammingExerciseManageTestCasesComponent>;
    let debugElement: DebugElement;

    let route: ActivatedRoute;
    let testCaseService: ProgrammingExerciseTestCaseService;

    let updateWeightStub: SinonStub;
    let notifyTestCasesSpy: SinonSpy;

    let routeSubject: Subject<Params>;

    const testCaseTableId = '#testCaseTable';
    const rowClass = 'datatable-body-row';

    const exerciseId = 1;
    const testCases1 = [
        { testName: 'testBubbleSort', active: true, weight: 1 },
        { testName: 'testMergeSort', active: true, weight: 1 },
        { testName: 'otherTest', active: false, weight: 1 },
    ] as ProgrammingExerciseTestCase[];
    const testCases2 = [
        { testName: 'testBubbleSort', active: false, weight: 2 },
        { testName: 'testMergeSort', active: true, weight: 2 },
        { testName: 'otherTest', active: true, weight: 2 },
    ] as ProgrammingExerciseTestCase[];

    beforeEach(async(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, NgxDatatableModule, NgbModule, FormsModule],
            declarations: [ProgrammingExerciseManageTestCasesComponent],
            providers: [
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

                updateWeightStub = stub(testCaseService, 'updateWeight');
                notifyTestCasesSpy = spy(testCaseService, 'notifyTestCases');

                routeSubject = new Subject();
                (route as MockActivatedRoute).setSubject(routeSubject);
            });
    }));

    afterEach(() => {
        notifyTestCasesSpy.restore();

        routeSubject.complete();
        routeSubject = new Subject();
        (route as MockActivatedRoute).setSubject(routeSubject);
    });

    it('should create a datatable with the correct amount of rows when test cases come in (hide inactive tests)', fakeAsync(() => {
        comp.ngOnInit();
        routeSubject.next({ exerciseId });

        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases1);
        tick();

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const rows = table.queryAll(By.css(rowClass));

        expect(comp.testCases).to.deep.equal(testCases1);
        expect(rows).to.have.lengthOf(testCases1.filter(({ active }) => active).length);

        fixture.destroy();
        flush();
    }));

    it('should create a datatable with the correct amount of rows when test cases come in (show inactive tests)', fakeAsync(() => {
        comp.ngOnInit();
        comp.showInactive = true;
        routeSubject.next({ exerciseId });

        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases1);
        tick();

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const rows = table.queryAll(By.css(rowClass));

        expect(comp.testCases).to.deep.equal(testCases1);
        expect(rows).to.have.lengthOf(testCases1.length);

        fixture.destroy();
        flush();
    }));

    it('should enter edit mode when an edit button is clicked to edit weights', fakeAsync(() => {
        comp.ngOnInit();
        comp.showInactive = true;
        routeSubject.next({ exerciseId });

        let orderedTests = _sortBy(testCases1, 'testName');

        (testCaseService as MockProgrammingExerciseTestCaseService).next(testCases1);
        tick();

        fixture.detectChanges();

        const table = debugElement.query(By.css(testCaseTableId));
        const editIcons = table.queryAll(By.css('.edit-weight__edit-icon'));
        expect(editIcons).to.have.lengthOf(testCases1.length);
        editIcons[0].nativeElement.click();

        fixture.detectChanges();

        expect(comp.editingInput).to.exist;
        expect(comp.editing).to.deep.equal(orderedTests[0]);

        updateWeightStub.returns(of({ ...orderedTests[0], weight: 20 }));
        // Set new weight.
        comp.editingInput.nativeElement.value = '20';
        comp.editingInput.nativeElement.dispatchEvent(new Event('blur'));

        fixture.detectChanges();
        tick();

        const testThatWasUpdated = _sortBy(comp.testCases, 'testName')[0];
        expect(updateWeightStub).to.have.been.calledOnceWithExactly(exerciseId, testThatWasUpdated.id, '20');
        expect(comp.editingInput).not.to.exist;
        expect(testThatWasUpdated.weight).to.equal(20);

        fixture.destroy();
        flush();
    }));
});
