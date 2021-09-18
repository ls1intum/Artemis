import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import dayjs from 'dayjs';
import { TranslateModule } from '@ngx-translate/core';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { SinonStub, spy, stub } from 'sinon';
import { BehaviorSubject, of } from 'rxjs';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ProgrammingSubmissionService, ProgrammingSubmissionState } from 'app/exercises/programming/participate/programming-submission.service';
import { MockProgrammingSubmissionService } from '../../helpers/mocks/service/mock-programming-submission.service';
import { triggerChanges } from '../../helpers/utils/general.utils';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { Result } from 'app/entities/result.model';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MissingResultInfo } from 'app/exercises/shared/result/result.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('UpdatingResultComponent', () => {
    let comp: UpdatingResultComponent;
    let fixture: ComponentFixture<UpdatingResultComponent>;
    let debugElement: DebugElement;
    let participationWebsocketService: ParticipationWebsocketService;
    let programmingSubmissionService: ProgrammingSubmissionService;

    let subscribeForLatestResultOfParticipationStub: SinonStub;
    let subscribeForLatestResultOfParticipationSubject: BehaviorSubject<Result | undefined>;

    let getLatestPendingSubmissionStub: SinonStub;

    const exercise = { id: 20 } as Exercise;
    const student = { id: 99 };
    const gradedResult1 = { id: 10, rated: true, completionDate: dayjs('2019-06-06T22:15:29.203+02:00') } as Result;
    const gradedResult2 = { id: 11, rated: true, completionDate: dayjs('2019-06-06T22:17:29.203+02:00') } as Result;
    const ungradedResult1 = { id: 12, rated: false, completionDate: dayjs('2019-06-06T22:25:29.203+02:00') } as Result;
    const ungradedResult2 = { id: 13, rated: false, completionDate: dayjs('2019-06-06T22:32:29.203+02:00') } as Result;
    const results = [gradedResult2, ungradedResult1, gradedResult1, ungradedResult2] as Result[];
    const initialParticipation = { id: 1, exercise, results, student } as any;
    const newGradedResult = { id: 14, rated: true } as Result;
    const newUngradedResult = { id: 15, rated: false } as Result;

    const submission = { id: 1 } as any;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule],
            declarations: [UpdatingResultComponent, ResultComponent],
            providers: [
                JhiLanguageHelper,
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

                subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | undefined>(undefined);
                subscribeForLatestResultOfParticipationStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation').returns(
                    subscribeForLatestResultOfParticipationSubject,
                );

                const programmingSubmissionStateObj = { participationId: 1, submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION };
                getLatestPendingSubmissionStub = stub(programmingSubmissionService, 'getLatestPendingSubmissionByParticipationId').returns(of(programmingSubmissionStateObj));
            });
    });

    afterEach(() => {
        subscribeForLatestResultOfParticipationStub.restore();
        subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | undefined>(undefined);
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
        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledOnceWithExactly(initialParticipation.id, true, undefined);
        expect(comp.result!.id).to.equal(gradedResult2.id);
    });

    it('should use the newest (un)rated result of the provided participation and subscribe for new results', () => {
        cleanInitializeUngraded();
        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledOnceWithExactly(initialParticipation.id, true, undefined);
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
        expect(comp.result!.id).to.equal(newUngradedResult.id);
        subscribeForLatestResultOfParticipationSubject.next(newGradedResult);
        expect(comp.result!.id).to.equal(newGradedResult.id);
    });

    it('should update result and establish new websocket connection on participation change', () => {
        cleanInitializeGraded();
        const unsubscribeSpy = spy(comp.resultSubscription, 'unsubscribe');
        const newParticipation = { id: 80, exercise, student, results: [{ id: 1, rated: true }] } as any;
        cleanInitializeGraded(newParticipation);
        expect(unsubscribeSpy).to.have.been.calledOnceWithExactly();
        expect(comp.result!.id).to.equal(newParticipation.results[0].id);
        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledTwice;
        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledWithExactly(initialParticipation.id, true, undefined);
        expect(subscribeForLatestResultOfParticipationStub).to.have.been.calledWithExactly(newParticipation.id, true, undefined);

        subscribeForLatestResultOfParticipationSubject.next(newGradedResult);
        expect(comp.result!.id).to.equal(newGradedResult.id);
    });

    it('should subscribe to fetching the latest pending submission when the exerciseType is PROGRAMMING', () => {
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING } as Exercise;
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).to.have.been.calledOnceWithExactly(comp.participation.id, comp.exercise.id, true);
        expect(comp.isBuilding).to.be.false;
    });

    it('should set the isBuilding attribute to true if exerciseType is PROGRAMMING and there is a latest pending submission', () => {
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING } as Exercise;
        getLatestPendingSubmissionStub.returns(of({ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission, participationId: 3 }));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).to.have.been.calledOnceWithExactly(comp.participation.id, comp.exercise.id, true);
        expect(comp.isBuilding).to.be.true;
        expect(comp.missingResultInfo).to.equal(MissingResultInfo.NONE);
    });

    it('should set the isBuilding attribute to false if exerciseType is PROGRAMMING and there is no pending submission anymore', () => {
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING } as Exercise;
        comp.isBuilding = true;
        getLatestPendingSubmissionStub.returns(of({ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 3 }));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).to.have.been.calledOnceWithExactly(comp.participation.id, comp.exercise.id, true);
        expect(comp.isBuilding).to.be.false;
        expect(comp.missingResultInfo).to.equal(MissingResultInfo.NONE);
    });

    it('should set missingResultInfo attribute if the exerciseType is PROGRAMMING and the latest submission failed (offline IDE)', () => {
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING, allowOfflineIde: true } as ProgrammingExercise;
        comp.isBuilding = true;
        getLatestPendingSubmissionStub.returns(of({ submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: undefined, participationId: 3 }));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).to.have.been.calledOnceWithExactly(comp.participation.id, comp.exercise.id, true);
        expect(comp.isBuilding).to.be.false;
        expect(comp.missingResultInfo).to.equal(MissingResultInfo.FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE);
    });

    it('should set missingResultInfo attribute if the exerciseType is PROGRAMMING and the latest submission failed (online IDE)', () => {
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING, allowOfflineIde: false } as ProgrammingExercise;
        getLatestPendingSubmissionStub.returns(of({ submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: undefined, participationId: 3 }));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).to.have.been.calledOnceWithExactly(comp.participation.id, comp.exercise.id, true);
        expect(comp.missingResultInfo).to.equal(MissingResultInfo.FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE);
    });

    it('should not set the isBuilding attribute to true if the exerciseType is not PROGRAMMING', () => {
        getLatestPendingSubmissionStub.returns(of({ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission, participationId: 3 }));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).not.to.have.been.called;
        expect(comp.isBuilding).to.equal(undefined);
        expect(comp.missingResultInfo).to.equal(MissingResultInfo.NONE);
    });
});
