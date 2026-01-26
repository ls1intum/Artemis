import { expect, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import dayjs from 'dayjs/esm';
import { BehaviorSubject, of } from 'rxjs';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import {
    BuildTimingInfo,
    ProgrammingSubmissionService,
    ProgrammingSubmissionState,
    ProgrammingSubmissionStateObj,
} from 'app/programming/shared/services/programming-submission.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockProgrammingSubmissionService } from 'test/helpers/mocks/service/mock-programming-submission.service';
import { triggerChanges } from 'test/helpers/utils/general-test.utils';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ResultComponent } from 'app/exercise/result/result.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { MockComponent } from 'ng-mocks';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { MissingResultInformation } from 'app/exercise/result/result.utils';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('UpdatingResultComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: UpdatingResultComponent;
    let fixture: ComponentFixture<UpdatingResultComponent>;
    let participationWebsocketService: ParticipationWebsocketService;
    let programmingSubmissionService: ProgrammingSubmissionService;

    let subscribeForLatestResultOfParticipationStub: ReturnType<typeof vi.spyOn>;
    let subscribeForLatestResultOfParticipationSubject: BehaviorSubject<Result | undefined>;

    let getLatestPendingSubmissionStub: ReturnType<typeof vi.spyOn>;
    let fetchQueueReleaseDateEstimationByParticipationIdStub: ReturnType<typeof vi.spyOn>;

    let profileService: ProfileService;

    const exercise = { id: 20 } as Exercise;
    const student = { id: 99 };
    const gradedResult1 = { id: 10, rated: true, completionDate: dayjs('2019-06-06T22:15:29.203+02:00') } as Result;
    const gradedResult2 = { id: 11, rated: true, completionDate: dayjs('2019-06-06T22:17:29.203+02:00') } as Result;
    const ungradedResult1 = { id: 12, rated: false, completionDate: dayjs('2019-06-06T22:25:29.203+02:00') } as Result;
    const ungradedResult2 = { id: 13, rated: false, completionDate: dayjs('2019-06-06T22:32:29.203+02:00') } as Result;
    const results = [gradedResult2, ungradedResult1, gradedResult1, ungradedResult2] as Result[];
    const initialParticipation = { id: 1, exercise, submissions: [{ results }], student } as StudentParticipation;
    const newGradedResult = { id: 14, rated: true } as Result;
    const newUngradedResult = { id: 15, rated: false } as Result;

    const submission = { id: 1 } as Submission;
    const buildTimingInfo: BuildTimingInfo = {
        buildStartDate: dayjs().subtract(10, 'second'),
        estimatedCompletionDate: dayjs().add(10, 'second'),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockComponent(ResultComponent), UpdatingResultComponent],
            providers: [
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UpdatingResultComponent);
                comp = fixture.componentInstance;

                participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
                programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);

                profileService = TestBed.inject(ProfileService);

                subscribeForLatestResultOfParticipationSubject = new BehaviorSubject<Result | undefined>(undefined);
                subscribeForLatestResultOfParticipationStub = vi
                    .spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation')
                    .mockReturnValue(subscribeForLatestResultOfParticipationSubject);

                const programmingSubmissionStateObj = { participationId: 1, submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION };
                getLatestPendingSubmissionStub = vi
                    .spyOn(programmingSubmissionService, 'getLatestPendingSubmissionByParticipationId')
                    .mockReturnValue(of(programmingSubmissionStateObj));
                fetchQueueReleaseDateEstimationByParticipationIdStub = vi
                    .spyOn(programmingSubmissionService, 'fetchQueueReleaseDateEstimationByParticipationId')
                    .mockReturnValue(of(undefined));
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    const cleanInitializeGraded = (participation = initialParticipation) => {
        comp.participation = participation;
        comp.ngOnInit();
        triggerChanges(comp, { property: 'participation', currentValue: participation });
        fixture.detectChanges();
    };

    const cleanInitializeUngraded = (participation = initialParticipation) => {
        comp.participation = participation;
        comp.showUngradedResults = true;
        comp.ngOnInit();
        triggerChanges(comp, { property: 'participation', currentValue: participation });
        fixture.detectChanges();
    };

    it('should not try to subscribe for new results if no participation is provided', () => {
        triggerChanges(comp, { property: 'participation', currentValue: undefined, firstChange: true });
        fixture.detectChanges();

        expect(subscribeForLatestResultOfParticipationStub).not.toHaveBeenCalled();
        expect(comp.result).toBeUndefined();
    });

    it('should use the newest rated result of the provided participation and subscribe for new results', () => {
        cleanInitializeGraded();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledWith(initialParticipation.id, true, undefined);
        expect(comp.result!.id).toBe(gradedResult2.id);
    });

    it('should use the newest (un)rated result of the provided participation and subscribe for new results', () => {
        cleanInitializeUngraded();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledOnce();
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledWith(initialParticipation.id, true, undefined);
        expect(comp.result!.id).toBe(ungradedResult2.id);
    });

    it('should react to rated, but not to unrated results if showUngradedResults is false', () => {
        cleanInitializeGraded();
        const currentResult = comp.result;
        subscribeForLatestResultOfParticipationSubject.next(newUngradedResult);
        expect(comp.result!.id).toBe(currentResult!.id);
        subscribeForLatestResultOfParticipationSubject.next(newGradedResult);
        expect(comp.result!.id).toBe(newGradedResult.id);
    });

    it('should react to both rated and unrated results if showUngradedResults is true', async () => {
        cleanInitializeUngraded();
        subscribeForLatestResultOfParticipationSubject.next(newUngradedResult);
        expect(comp.result!.id).toBe(newUngradedResult.id);
        subscribeForLatestResultOfParticipationSubject.next(newGradedResult);
        expect(comp.result!.id).toBe(newGradedResult.id);
    });

    it('should update result and establish new websocket connection on participation change', () => {
        cleanInitializeGraded();
        const unsubscribeSpy = vi.spyOn(comp.resultSubscription, 'unsubscribe');
        const newParticipation = { id: 80, exercise, student, submissions: [{ results: [{ id: 1, rated: true }] }] } as Participation;
        cleanInitializeGraded(newParticipation);
        expect(unsubscribeSpy).toHaveBeenNthCalledWith(1);
        expect(comp.result!.id).toBe(newParticipation.submissions![0].results![0].id);
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenCalledTimes(2);
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenNthCalledWith(1, initialParticipation.id, true, undefined);
        expect(subscribeForLatestResultOfParticipationStub).toHaveBeenNthCalledWith(2, newParticipation.id, true, undefined);

        subscribeForLatestResultOfParticipationSubject.next(newGradedResult);
        expect(comp.result!.id).toBe(newGradedResult.id);
    });

    it('should subscribe to fetching the latest pending submission when the exerciseType is PROGRAMMING', () => {
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING } as Exercise;
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).toHaveBeenCalledOnce();
        expect(getLatestPendingSubmissionStub).toHaveBeenCalledWith(comp.participation.id, comp.exercise.id, true);
        expect(comp.isBuilding).toBe(false);
    });

    it('should set the isBuilding attribute to true if exerciseType is PROGRAMMING and there is a latest pending submission', () => {
        // LocalCI is disabled
        vi.spyOn(profileService, 'isProfileActive').mockImplementation(() => false);
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING } as Exercise;
        getLatestPendingSubmissionStub.mockReturnValue(
            of({ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission, participationId: 3, buildTimingInfo }),
        );
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).toHaveBeenCalledOnce();
        expect(getLatestPendingSubmissionStub).toHaveBeenCalledWith(comp.participation.id, comp.exercise.id, true);
        expect(comp.isBuilding).toBe(true);
        expect(comp.missingResultInfo).toBe(MissingResultInformation.NONE);
        // LocalCI is not enabled, so the buildStartDate and estimatedCompletionDate should not be set
        expect(comp.buildStartDate).toBeUndefined();
        expect(comp.estimatedCompletionDate).toBeUndefined();
    });

    it('should set the isBuilding attribute to false if exerciseType is PROGRAMMING and there is no pending submission anymore', () => {
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING } as Exercise;
        comp.isBuilding = true;
        getLatestPendingSubmissionStub.mockReturnValue(of({ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 3 }));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).toHaveBeenCalledOnce();
        expect(getLatestPendingSubmissionStub).toHaveBeenCalledWith(comp.participation.id, comp.exercise.id, true);
        expect(comp.isBuilding).toBe(false);
        expect(comp.missingResultInfo).toBe(MissingResultInformation.NONE);
    });

    it('should set missingResultInfo attribute if the exerciseType is PROGRAMMING and the latest submission failed (offline IDE)', () => {
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING, allowOfflineIde: true } as ProgrammingExercise;
        comp.isBuilding = true;
        getLatestPendingSubmissionStub.mockReturnValue(of({ submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: undefined, participationId: 3 }));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).toHaveBeenCalledOnce();
        expect(getLatestPendingSubmissionStub).toHaveBeenCalledWith(comp.participation.id, comp.exercise.id, true);
        expect(comp.isBuilding).toBe(false);
        expect(comp.missingResultInfo).toBe(MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_OFFLINE_IDE);
    });

    it('should set missingResultInfo attribute if the exerciseType is PROGRAMMING and the latest submission failed (online IDE)', () => {
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING, allowOfflineIde: false } as ProgrammingExercise;
        getLatestPendingSubmissionStub.mockReturnValue(of({ submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: undefined, participationId: 3 }));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).toHaveBeenCalledOnce();
        expect(getLatestPendingSubmissionStub).toHaveBeenCalledWith(comp.participation.id, comp.exercise.id, true);
        expect(comp.missingResultInfo).toBe(MissingResultInformation.FAILED_PROGRAMMING_SUBMISSION_ONLINE_IDE);
    });

    it('should not set the isBuilding attribute to true if the exerciseType is not PROGRAMMING', () => {
        getLatestPendingSubmissionStub.mockReturnValue(of({ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission, participationId: 3 }));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).not.toHaveBeenCalled();
        expect(comp.isBuilding).toBeUndefined();
        expect(comp.missingResultInfo).toBe(MissingResultInformation.NONE);
    });

    it('should update the building status if the submission was before the due date', () => {
        submission.submissionDate = dayjs();
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING, dueDate: submission.submissionDate.add(1, 'hour') } as Exercise;
        getLatestPendingSubmissionStub.mockReturnValue(of({ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission, participationId: 3 }));
        cleanInitializeGraded();
        expect(comp.isBuilding).toBe(true);
        expect(comp.missingResultInfo).toBe(MissingResultInformation.NONE);
    });

    it('should not update the building status if the submission was after the due date', () => {
        submission.submissionDate = dayjs();
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING, dueDate: submission.submissionDate.subtract(1, 'minute') } as Exercise;
        getLatestPendingSubmissionStub.mockReturnValue(of({ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission, participationId: 3 }));
        cleanInitializeGraded();
        expect(comp.isBuilding).toBeUndefined();
        expect(comp.missingResultInfo).toBe(MissingResultInformation.NONE);
    });

    it('should set the isQueue and isBuilding attribute to true with correct timing', () => {
        // LocalCI is enabled
        vi.spyOn(profileService, 'isProfileActive').mockImplementation((profile) => profile === PROFILE_LOCALCI);
        comp.exercise = { id: 99, type: ExerciseType.PROGRAMMING } as Exercise;
        const pendingSubmissionSubject = new BehaviorSubject({
            submissionState: ProgrammingSubmissionState.IS_QUEUED,
            submission,
            participationId: 3,
        } as ProgrammingSubmissionStateObj);
        getLatestPendingSubmissionStub.mockReturnValue(pendingSubmissionSubject);
        const queueReleaseDate = dayjs().add(3, 'second');
        fetchQueueReleaseDateEstimationByParticipationIdStub.mockReturnValue(of(queueReleaseDate));
        cleanInitializeGraded();
        expect(getLatestPendingSubmissionStub).toHaveBeenCalledOnce();
        expect(getLatestPendingSubmissionStub).toHaveBeenCalledWith(comp.participation.id, comp.exercise.id, true);

        expect(comp.isBuilding).toBeFalsy();
        expect(comp.isQueued).toBeTruthy();
        expect(comp.missingResultInfo).toBe(MissingResultInformation.NONE);
        expect(comp.estimatedCompletionDate).toBe(queueReleaseDate);

        // Now the submission is building
        pendingSubmissionSubject.next({ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission, participationId: 3, buildTimingInfo });
        expect(comp.isBuilding).toBeTruthy();
        expect(comp.isQueued).toBeFalsy();
        expect(comp.buildStartDate).toBe(buildTimingInfo.buildStartDate);
        expect(comp.estimatedCompletionDate).toBe(buildTimingInfo.estimatedCompletionDate);
    });
});
