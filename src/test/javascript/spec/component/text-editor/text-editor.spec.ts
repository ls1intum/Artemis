import * as chai from 'chai';
import { DebugElement } from '@angular/core';
import * as moment from 'moment';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { AlertService } from 'app/core/alert/alert.service';
import { ArtemisTestModule } from '../../test.module';
import { TranslateModule } from '@ngx-translate/core';
import { MockTextEditorService } from '../../mocks/mock-text-editor.service';
import { SinonStub, stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { BehaviorSubject } from 'rxjs';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterTestingModule } from '@angular/router/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../mocks/mock-sync.storage';
import { MockComponent } from 'ng-mocks';
import { TextResultComponent } from 'app/exercises/text/participate/text-result/text-result.component';
import { ComplaintInteractionsComponent } from 'app/complaints/complaint-interactions.component';
import { SubmissionResultStatusComponent } from 'app/overview/submission-result-status.component';
import { TextEditorComponent } from 'app/exercises/text/participate/text-editor.component';
import { textEditorRoute } from 'app/exercises/text/participate/text-editor.route';
import { TextExercise } from 'app/entities/text-exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ButtonComponent } from 'app/shared/components/button.component';
import { Result } from 'app/entities/result.model';
import { ComplaintsComponent } from 'app/complaints/complaints.component';
import { TextSubmission } from 'app/entities/text-submission.model';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';

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
    const participation = new StudentParticipation();
    const result = new Result();

    beforeAll(() => {
        participation.id = 42;
        participation.exercise = textExercise;
        participation.submissions = [new TextSubmission()];
        result.id = 1;
    });

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, ArtemisTeamModule, RouterTestingModule.withRoutes([textEditorRoute[0]])],
            declarations: [
                TextEditorComponent,
                MockComponent(SubmissionResultStatusComponent),
                MockComponent(ButtonComponent),
                MockComponent(TextResultComponent),
                MockComponent(ComplaintsComponent),
                MockComponent(ComplaintInteractionsComponent),
            ],
            providers: [
                AlertService,
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
