import * as chai from 'chai';
import { TextEditorComponent, textEditorRoute } from 'app/text-editor';
import { DebugElement } from '@angular/core';
import * as moment from 'moment';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TextExercise } from 'app/entities/text-exercise';
import { StudentParticipation } from 'app/entities/participation';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Result, ResultComponent } from 'app/entities/result';
import { JhiAlertService } from 'ng-jhipster';
import { ArtemisTestModule } from '../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { MockTextEditorService } from '../../mocks/mock-text-editor.service';
import { SinonStub, stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { TextEditorService } from 'app/text-editor/text-editor.service';
import { BehaviorSubject } from 'rxjs';
import { ArtemisSharedModule } from 'app/shared';
import { RouterTestingModule } from '@angular/router/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../mocks';
import { MockComponent } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components';
import { TextResultComponent } from 'app/text-editor/text-result/text-result.component';
import { ComplaintsComponent } from 'app/complaints';
import { ComplaintInteractionsComponent } from 'app/complaints/complaint-interactions.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('TableEditableFieldComponent', () => {
    let comp: TextEditorComponent;
    let fixture: ComponentFixture<TextEditorComponent>;
    let debugElement: DebugElement;
    let textService: TextEditorService;

    let getTextForParticipationStub: SinonStub;

    const route = { snapshot: { paramMap: convertToParamMap({ participationId: 42 }) } } as ActivatedRoute;
    const textExercise = { id: 1 } as TextExercise;
    const participation = { id: 42, exercise: textExercise } as StudentParticipation;
    const result = { id: 1 } as Result;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, RouterTestingModule.withRoutes([textEditorRoute[0]])],
            declarations: [
                TextEditorComponent,
                MockComponent(ResultComponent),
                MockComponent(ButtonComponent),
                MockComponent(TextResultComponent),
                MockComponent(ComplaintsComponent),
                MockComponent(ComplaintInteractionsComponent),
            ],
            providers: [
                JhiAlertService,
                { provide: ActivatedRoute, useValue: route },
                { provide: TextEditorService, useClass: MockTextEditorService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextEditorComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                textService = debugElement.injector.get(TextEditorService);
                getTextForParticipationStub = stub(textService, 'get');
            });
    });

    afterEach(() => {
        getTextForParticipationStub.restore();
    });

    it('should not allow to submit after the deadline if there is no due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.returns(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDeadline).to.be.false;
        expect(comp.isAlwaysActive).to.be.true;
    }));

    it('should not allow to submit after the deadline if the initialization date is before the due date', fakeAsync(() => {
        participation.initializationDate = moment();
        textExercise.dueDate = moment().add(1, 'days');
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.returns(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDeadline).to.be.false;
    }));

    it('should allow to submit after the deadline if the initilization date is after the due date', fakeAsync(() => {
        participation.initializationDate = moment().add(1, 'days');
        textExercise.dueDate = moment();
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.returns(participationSubject);
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAllowedToSubmitAfterDeadline).to.be.true;
    }));

    it('should not be always active if there is a result and no due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.returns(participationSubject);
        comp.result = result;
        comp.textExercise = textExercise;

        fixture.detectChanges();
        tick();

        expect(comp.isAlwaysActive).to.be.false;
    }));

    it('should be always active if there is no result and the initialization date is after the due date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.returns(participationSubject);
        comp.textExercise = textExercise;
        comp.textExercise.dueDate = moment();
        participation.initializationDate = moment().add(1, 'days');

        fixture.detectChanges();
        tick();

        expect(comp.isAlwaysActive).to.be.true;
    }));

    it('should get inactive as soon as the due date passes the current date', fakeAsync(() => {
        const participationSubject = new BehaviorSubject<StudentParticipation>(participation);
        getTextForParticipationStub.returns(participationSubject);
        textExercise.dueDate = moment().add(1, 'days');
        participation.initializationDate = moment();

        fixture.detectChanges();
        tick();

        expect(comp.isActive).to.be.true;

        comp.textExercise.dueDate = moment().subtract(1, 'days');

        fixture.detectChanges();
        tick();

        expect(comp.isActive).to.be.false;
    }));
});
