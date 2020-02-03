import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import * as moment from 'moment';
import { TranslateModule } from '@ngx-translate/core';
import { WindowRef } from 'app/core/websocket/window.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { SinonStub, stub } from 'sinon';
import { of, Subject } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { MockParticipationWebsocketService, MockSyncStorage } from '../../mocks';
import { Result } from 'app/entities/result';
import { InitializationState } from 'app/entities/participation';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Exercise } from 'app/entities/exercise';
import { ProgrammingSubmissionService, ProgrammingSubmissionState, ProgrammingSubmissionStateObj } from 'app/programming-submission/programming-submission.service';
import { ArtemisProgrammingExerciseActionsModule } from 'app/entities/programming-exercise/actions/programming-exercise-actions.module';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/entities/programming-exercise/actions';
import { triggerChanges } from '../../utils/general.utils';

chai.use(sinonChai);
const expect = chai.expect;

describe('TriggerBuildButtonSpec', () => {
    let comp: ProgrammingExerciseStudentTriggerBuildButtonComponent;
    let fixture: ComponentFixture<ProgrammingExerciseStudentTriggerBuildButtonComponent>;
    let debugElement: DebugElement;
    let submissionService: ProgrammingSubmissionService;

    let getLatestPendingSubmissionStub: SinonStub;
    let getLatestPendingSubmissionSubject: Subject<ProgrammingSubmissionStateObj>;

    let triggerBuildStub: SinonStub;
    let triggerFailedBuildStub: SinonStub;

    const exercise = { id: 20 } as Exercise;
    const student = { id: 99 };
    const gradedResult1 = { id: 10, rated: true, completionDate: moment('2019-06-06T22:15:29.203+02:00') } as Result;
    const gradedResult2 = { id: 11, rated: true, completionDate: moment('2019-06-06T22:17:29.203+02:00') } as Result;
    const ungradedResult1 = { id: 12, rated: false, completionDate: moment('2019-06-06T22:25:29.203+02:00') } as Result;
    const ungradedResult2 = { id: 13, rated: false, completionDate: moment('2019-06-06T22:32:29.203+02:00') } as Result;
    const results = [gradedResult2, ungradedResult1, gradedResult1, ungradedResult2] as Result[];
    const participation = { id: 1, exercise, results, student } as any;

    const submission = { id: 1 } as any;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisProgrammingExerciseActionsModule],
            providers: [
                JhiLanguageHelper,
                WindowRef,
                ChangeDetectorRef,
                { provide: AccountService, useClass: MockAccountService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                submissionService = debugElement.injector.get(ProgrammingSubmissionService);

                getLatestPendingSubmissionSubject = new Subject<ProgrammingSubmissionStateObj>();
                getLatestPendingSubmissionStub = stub(submissionService, 'getLatestPendingSubmissionByParticipationId').returns(getLatestPendingSubmissionSubject);

                triggerBuildStub = stub(submissionService, 'triggerBuild').returns(of());
                triggerFailedBuildStub = stub(submissionService, 'triggerFailedBuild').returns(of());
            });
    });

    afterEach(() => {
        getLatestPendingSubmissionStub.restore();
        triggerBuildStub.restore();
    });

    const getTriggerButton = () => {
        const triggerButton = debugElement.query(By.css('button'));
        return triggerButton ? triggerButton.nativeElement : null;
    };

    it('should not show the trigger button if there is no pending submission and no build is running', () => {
        comp.participation = { ...participation, results: [gradedResult1], initializationState: InitializationState.INITIALIZED };
        comp.exercise = { id: 4 };

        triggerChanges(comp, { property: 'participation', currentValue: comp.participation });
        fixture.detectChanges();

        // Button should not show if there is no failed submission.
        let triggerButton = getTriggerButton();
        expect(triggerButton).not.to.exist;

        // After a failed submission is sent, the button should be displayed.
        getLatestPendingSubmissionSubject.next({ submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: null, participationId: comp.participation.id });

        fixture.detectChanges();
        triggerButton = getTriggerButton();
        expect(triggerButton).to.exist;
    });

    it('should be enabled and trigger the build on click if it is provided with a participation including results', () => {
        comp.participation = { ...participation, results: [gradedResult1], initializationState: InitializationState.INITIALIZED };
        comp.exercise = { id: 5 };

        triggerChanges(comp, { property: 'participation', currentValue: comp.participation });
        getLatestPendingSubmissionSubject.next({ submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: null, participationId: comp.participation.id });
        fixture.detectChanges();

        let triggerButton = getTriggerButton();
        expect(triggerButton.disabled).to.be.false;

        // Click the button to start a build.
        triggerButton.click();
        expect(triggerFailedBuildStub).to.have.been.calledOnce;
        expect(triggerBuildStub).to.not.have.been.calledOnce;

        // After some time the created submission comes through the websocket, button is disabled until the build is done.
        getLatestPendingSubmissionSubject.next({ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission, participationId: comp.participation.id });
        expect(comp.isBuilding).to.be.true;
        fixture.detectChanges();
        expect(triggerButton.disabled).to.be.true;

        // Now the server signals that the build is done, the button should now be removed.
        getLatestPendingSubmissionSubject.next({ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: null, participationId: comp.participation.id });
        expect(comp.isBuilding).to.be.false;
        fixture.detectChanges();
        triggerButton = getTriggerButton();
        expect(triggerButton).not.to.exist;
    });
});
