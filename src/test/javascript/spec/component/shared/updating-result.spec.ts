import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MomentModule } from 'ngx-moment';
import * as moment from 'moment';
import { TranslateModule } from '@ngx-translate/core';
import { WindowRef } from 'app/core/websocket/window.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { SinonStub, spy, stub } from 'sinon';
import { BehaviorSubject, of } from 'rxjs';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { CodeEditorFileService } from 'app/code-editor';
import { ArtemisTestModule } from '../../test.module';
import { MockParticipationWebsocketService, MockSyncStorage } from '../../mocks';
import { Result, ResultComponent, UpdatingResultComponent } from 'app/entities/result';
import { ArtemisSharedModule } from 'app/shared';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Exercise, ExerciseType } from 'app/entities/exercise';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/programming-submission/programming-submission.service';
import { MockProgrammingSubmissionService } from '../../mocks/mock-programming-submission.service';
import { triggerChanges } from '../../utils/general.utils';

chai.use(sinonChai);
const expect = chai.expect;

describe('UpdatingResultComponent', () => {
    let comp: UpdatingResultComponent;
    let fixture: ComponentFixture<UpdatingResultComponent>;
    let debugElement: DebugElement;
    let participationWebsocketService: ParticipationWebsocketService;
    let programmingSubmissionService: ProgrammingSubmissionService;

    let subscribeForLatestResultOfParticipationStub: SinonStub;
    let subscribeForLatestResultOfParticipationSubject: BehaviorSubject<Result | null>;

    let getLatestPendingSubmissionStub: SinonStub;

    const exercise = { id: 20 } as Exercise;
    const student = { id: 99 };
    const gradedResult1 = { id: 10, rated: true, completionDate: moment('2019-06-06T22:15:29.203+02:00') } as Result;
    const gradedResult2 = { id: 11, rated: true, completionDate: moment('2019-06-06T22:17:29.203+02:00') } as Result;
    const ungradedResult1 = { id: 12, rated: false, completionDate: moment('2019-06-06T22:25:29.203+02:00') } as Result;
    const ungradedResult2 = { id: 13, rated: false, completionDate: moment('2019-06-06T22:32:29.203+02:00') } as Result;
    const results = [gradedResult2, ungradedResult1, gradedResult1, ungradedResult2] as Result[];
    const initialParticipation = { id: 1, exercise, results, student } as any;
    const newGradedResult = { id: 14, rated: true } as Result;
    const newUngradedResult = { id: 15, rated: false } as Result;

    const submission = { id: 1 } as any;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, MomentModule],
            declarations: [UpdatingResultComponent, ResultComponent],
            providers: [
                JhiLanguageHelper,
                WindowRef,
                CodeEditorFileService,
                ChangeDetectorRef,
                { provide: AccountService, useClass: MockAccountService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UpdatingResultComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                participationWebsocketService = debugElement.injector.get(ParticipationWebsocketService);
                programmingSubmissionService = debugElement.injector.get(ProgrammingSubmissionService);

                subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | null>(null);
                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation').returns(
                    subscribeForLatestResultOfParticipationSubject,
                );

                getLatestPendingSubmissionStub = stub(programmingSubmissionService, 'getLatestPendingSubmissionByParticipationId').returns(
                    of([ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, null]),
                );
            });
    });

    afterEach(() => {
        subscribeForLatestResultOfParticipationStub.restore();
        subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | null>(null);
        subscribeForLatestResultOfParticipationStub.returns(subscribeForLatestResultOfParticipationSubject);
    });

    const cleanInitializeGraded = (participation = initialParticipation) => {
        comp.participation = participation;
        triggerChanges(comp, { property: 'participation', currentValue: participation });
        fixture.detectChanges();
    };

    const cleanInitializeUngraded = (participation = initialParticipation) => {
        comp.participation = participation;
        comp.showUngradedResults = true;
        triggerChanges(comp, { property: 'participation', currentValue: participation });
        fixture.detectChanges();
    };

    it('should not try to subscribe for new results if no participation is provided', () => {
        triggerChanges(comp, { property: 'participation', currentValue: undefined, firstChange: true });
        fixture.detectChanges();

        expect(subscribeForLatestResultOfParticipationStub).to.not.have.been.called;
        expect(comp.result).to.equal(undefined);
    });

    it('should use the newest rated result of the provided participation and subscribe for new results', () => {
        cleanInitializeGraded();
        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledOnceWithExactly(initialParticipation.id);
        expect(comp.result!.id).to.equal(gradedResult2.id);
    });

    it('should use the newest (un)rated result of the provided participation and subscribe for new results', () => {
        cleanInitializeUngraded();
        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledOnceWithExactly(initialParticipation.id);
        expect(comp.result!.id).to.equal(ungradedResult2.id);
    });

    it('should react to rated, but not to unrated results if showUngradedResults is false', () => {
        cleanInitializeGraded();
        const currentResult = comp.result;
        subscribeForLatestResultOfParticipationSubject.next(newUngradedResult);
        expect(comp.result!.id).to.equal(currentResult!.id);
        subscribeForLatestResultOfParticipationSubject.next(newGradedResult);
        expect(comp.result!.id).to.equal(newGradedResult.id);
    });

    it('should react to both rated and unrated results if showUngradedResults is true', async () => {
        cleanInitializeUngraded();
        subscribeForLatestResultOfParticipationSubject.next(newUngradedResult);
        expect(comp.result.id).to.equal(newUngradedResult.id);
        subscribeForLatestResultOfParticipationSubject.next(newGradedResult);
        expect(comp.result.id).to.equal(newGradedResult.id);
    });

    it('should update result and establish new websocket connection on participation change', () => {
        cleanInitializeGraded();
        const unsubscribeSpy = spy(comp.resultSubscription, 'unsubscribe');
        const newParticipation = { id: 80, exercise, student, results: [{ id: 1, rated: true }] } as any;
        cleanInitializeGraded(newParticipation);
        expect(unsubscribeSpy).to.have.been.calledOnceWithExactly();
        expect(comp.result!.id).to.equal(newParticipation.results[0].id);
        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledTwice;
        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledWithExactly(initialParticipation.id);
        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledWithExactly(newParticipation.id);

        subscribeForLatestResultOfParticipationSubject.next(newGradedResult);
        expect(comp.result!.id).to.equal(newGradedResult.id);
    });

    it('should subscribe to fetching the latest pending submission when the exerciseType is PROGRAMMING', () => {
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING } as Exercise;
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).to.have.been.calledOnceWithExactly(comp.participation.id, comp.exercise.id);
        expect(comp.isBuilding).to.be.false;
    });

    it('should set the isBuilding attribute to true if exerciseType is PROGRAMMING and there is a latest pending submission', () => {
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING } as Exercise;
        getLatestPendingSubmissionStub.returns(of({ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission, participationId: 3 }));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).to.have.been.calledOnceWithExactly(comp.participation.id, comp.exercise.id);
        expect(comp.isBuilding).to.be.true;
    });

    it('should set the isBuilding attribute to false if exerciseType is PROGRAMMING and there is no pending submission anymore', () => {
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING } as Exercise;
        comp.isBuilding = true;
        getLatestPendingSubmissionStub.returns(of([ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, null]));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).to.have.been.calledOnceWithExactly(comp.participation.id, comp.exercise.id);
        expect(comp.isBuilding).to.equal(false);
    });

    it('should not set the isBuilding attribute to true if the exerciseType is not PROGRAMMING', () => {
        getLatestPendingSubmissionStub.returns(of([ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission]));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).not.to.have.been.called;
        expect(comp.isBuilding).to.equal(undefined);
    });
});
