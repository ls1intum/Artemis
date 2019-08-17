import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MomentModule } from 'ngx-moment';
import * as moment from 'moment';
import { TranslateModule } from '@ngx-translate/core';
import { AccountService, JhiLanguageHelper, WindowRef } from 'app/core';
import { ChangeDetectorRef, DebugElement, SimpleChange, SimpleChanges } from '@angular/core';
import { SinonStub, stub } from 'sinon';
import { BehaviorSubject, of, Subject } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ArTEMiSTestModule } from '../../test.module';
import { MockParticipationWebsocketService, MockSyncStorage } from '../../mocks';
import { Result } from 'app/entities/result';
import { ArTEMiSSharedModule } from 'app/shared';
import { InitializationState, ParticipationWebsocketService } from 'app/entities/participation';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Exercise } from 'app/entities/exercise';
import { ProgrammingSubmissionState, ProgrammingSubmissionStateObj, ProgrammingSubmissionWebsocketService } from 'app/submission/programming-submission-websocket.service';
import { MockSubmissionWebsocketService } from '../../mocks/mock-submission-websocket.service';
import { ProgrammingExerciseParticipationService, ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/entities/programming-exercise';
import { MockProgrammingExerciseParticipationService } from '../../mocks/mock-programming-exercise-participation.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('TriggerBuildButtonSpec', () => {
    let comp: ProgrammingExerciseStudentTriggerBuildButtonComponent;
    let fixture: ComponentFixture<ProgrammingExerciseStudentTriggerBuildButtonComponent>;
    let debugElement: DebugElement;
    let participationWebsocketService: ParticipationWebsocketService;
    let submissionWebsocketService: ProgrammingSubmissionWebsocketService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;

    let checkIfParticipationHasResult: SinonStub;

    let subscribeForLatestResultOfParticipationStub: SinonStub;
    let subscribeForLatestResultOfParticipationSubject: BehaviorSubject<Result | null>;

    let getLatestPendingSubmissionStub: SinonStub;
    let getLatestPendingSubmissionSubject: Subject<ProgrammingSubmissionStateObj>;

    let triggerBuildStub: SinonStub;

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
            imports: [TranslateModule.forRoot(), ArTEMiSTestModule, ArTEMiSSharedModule, MomentModule],
            declarations: [ProgrammingExerciseStudentTriggerBuildButtonComponent],
            providers: [
                JhiLanguageHelper,
                WindowRef,
                ChangeDetectorRef,
                { provide: AccountService, useClass: MockAccountService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ProgrammingSubmissionWebsocketService, useClass: MockSubmissionWebsocketService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingExerciseStudentTriggerBuildButtonComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                participationWebsocketService = debugElement.injector.get(ParticipationWebsocketService);
                submissionWebsocketService = debugElement.injector.get(ProgrammingSubmissionWebsocketService);
                programmingExerciseParticipationService = debugElement.injector.get(ProgrammingExerciseParticipationService);

                subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | null>(null);
                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation').returns(
                    subscribeForLatestResultOfParticipationSubject,
                );

                getLatestPendingSubmissionSubject = new Subject<ProgrammingSubmissionStateObj>();
                getLatestPendingSubmissionStub = stub(submissionWebsocketService, 'getLatestPendingSubmission').returns(getLatestPendingSubmissionSubject);

                checkIfParticipationHasResult = stub(programmingExerciseParticipationService, 'checkIfParticipationHasResult');
                triggerBuildStub = stub(submissionWebsocketService, 'triggerBuild').returns(of());
            });
    });

    afterEach(() => {
        subscribeForLatestResultOfParticipationStub.restore();
        subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | null>(null);
        subscribeForLatestResultOfParticipationStub.returns(subscribeForLatestResultOfParticipationSubject);
    });

    const getTriggerButton = () => {
        const triggerButton = debugElement.query(By.css('button'));
        return triggerButton ? triggerButton.nativeElement : null;
    };

    it('should not show the trigger button if there is no pending submission and no build is running', () => {
        comp.participation = { ...participation, results: [gradedResult1], initializationState: InitializationState.INITIALIZED };
        const changes: SimpleChanges = {
            participation: new SimpleChange(undefined, comp.participation, true),
        };
        comp.ngOnChanges(changes);

        fixture.detectChanges();

        // Button should not show if there is no failed submission.
        let triggerButton = getTriggerButton();
        expect(triggerButton).not.to.exist;

        // After a failed submission is sent, the button should be displayed.
        getLatestPendingSubmissionSubject.next([ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, null]);

        fixture.detectChanges();
        triggerButton = getTriggerButton();
        expect(triggerButton).to.exist;
    });

    it('should be enabled and trigger the build on click if it is provided with a participation including results', () => {
        comp.participation = { ...participation, results: [gradedResult1], initializationState: InitializationState.INITIALIZED };
        const changes: SimpleChanges = {
            participation: new SimpleChange(undefined, comp.participation, true),
        };
        comp.ngOnChanges(changes);

        getLatestPendingSubmissionSubject.next([ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, null]);

        fixture.detectChanges();

        let triggerButton = getTriggerButton();
        expect(triggerButton.disabled).to.be.false;

        // Click the button to start a build.
        triggerButton.click();
        expect(triggerBuildStub).to.have.been.calledOnce;

        // After some time the created submission comes through the websocket, button is disabled until the build is done.
        getLatestPendingSubmissionSubject.next([ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission]);
        expect(comp.isBuilding).to.be.true;
        fixture.detectChanges();
        expect(triggerButton.disabled).to.be.true;

        // Now the server signals that the build is done, the button should now be removed.
        getLatestPendingSubmissionSubject.next([ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, null]);
        expect(comp.isBuilding).to.be.false;
        fixture.detectChanges();
        triggerButton = getTriggerButton();
        expect(triggerButton).not.to.exist;
    });

    it('should be disabled if the participation has no result as this means that probably no commit was made yet', () => {
        checkIfParticipationHasResult.returns(of(false));
        comp.participation = { ...participation, results: [], initializationState: InitializationState.INITIALIZED };
        const changes: SimpleChanges = {
            participation: new SimpleChange(undefined, comp.participation, true),
        };
        comp.ngOnChanges(changes);

        getLatestPendingSubmissionSubject.next([ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, null]);

        fixture.detectChanges();

        // There is no result within the participation object, button should not show.
        expect(checkIfParticipationHasResult).to.have.been.calledOnceWithExactly(comp.participation.id);
        expect(comp.isBuilding).to.be.false;
        let triggerButton = getTriggerButton();
        expect(triggerButton).not.to.exist;

        // If the participation receives a result, the trigger button should now show.
        subscribeForLatestResultOfParticipationSubject.next(gradedResult2);
        expect(comp.isBuilding).to.be.false;
        fixture.detectChanges();
        triggerButton = getTriggerButton();
        expect(triggerButton).to.exist;
        expect(triggerButton.disabled).to.be.false;
    });

    it('should be become enabled if a result is not attached to the participation but could be found on the server', () => {
        checkIfParticipationHasResult.returns(of(true));
        comp.participation = { ...participation, results: [], initializationState: InitializationState.INITIALIZED };
        const changes: SimpleChanges = {
            participation: new SimpleChange(undefined, comp.participation, true),
        };
        comp.ngOnChanges(changes);

        getLatestPendingSubmissionSubject.next([ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, null]);

        fixture.detectChanges();
        expect(checkIfParticipationHasResult).to.have.been.calledOnceWithExactly(comp.participation.id);

        const triggerButton = getTriggerButton();
        expect(triggerButton.disabled).to.be.false;
    });
});
