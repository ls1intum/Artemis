import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TeamSubmissionSyncComponent } from 'app/exercise/team-submission-sync/team-submission-sync.component';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockProvider } from 'ng-mocks';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Observable, Subject, of } from 'rxjs';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { SubmissionSyncPayload } from 'app/exercise/shared/entities/submission/submission-sync-payload.model';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { SubmissionPatchPayload } from 'app/exercise/shared/entities/submission/submission-patch-payload.model';
import { SubmissionPatch } from 'app/exercise/shared/entities/submission/submission-patch.model';

describe('Team Submission Sync Component', () => {
    let fixture: ComponentFixture<TeamSubmissionSyncComponent>;
    let component: TeamSubmissionSyncComponent;
    let websocketService: WebsocketService;
    let textSubmissionWithParticipation: Submission;
    let submissionObservableWithParticipation: Observable<Submission>;
    let submissionSyncPayload: SubmissionSyncPayload;
    let currentUser: User;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(AlertService),
                MockProvider(SessionStorageService),
                MockProvider(WebsocketService),
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: HttpClient, useClass: MockHttpService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TeamSubmissionSyncComponent);
                component = fixture.componentInstance;

                websocketService = TestBed.inject(WebsocketService);

                component.exerciseType = ExerciseType.TEXT;
                const participation = new StudentParticipation(ParticipationType.STUDENT);
                participation.id = 3;
                component.participation = participation;
                textSubmissionWithParticipation = new TextSubmission();
                textSubmissionWithParticipation.participation = new StudentParticipation();
                submissionObservableWithParticipation = of(textSubmissionWithParticipation);
                component.submissionObservable = submissionObservableWithParticipation;
                currentUser = new User();
                currentUser.login = 'ge12ebc';
                component.currentUser = currentUser;

                submissionSyncPayload = new SubmissionSyncPayload();
                submissionSyncPayload.sender = currentUser;
                const submission = new TextSubmission();
                submission.id = 12;
                submissionSyncPayload.submission = submission;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('ngOnInit should work correctly', () => {
        const expectedWebsocketTopic = '/topic/participations/3/team/text-submissions';
        const submissionSyncObservable = of(submissionSyncPayload);

        const receiveSubmissionEventEmitter = jest.spyOn(component.receiveSubmission, 'emit');
        const websocketSubscribeSpy = jest.spyOn(websocketService, 'subscribe');
        const websocketReceiveMock = jest.spyOn(websocketService, 'receive').mockReturnValue(submissionSyncObservable);
        const websocketSendSpy = jest.spyOn(websocketService, 'send');

        component.ngOnInit();

        expect(component.websocketTopic).toBe(expectedWebsocketTopic);
        expect(websocketSubscribeSpy).toHaveBeenCalledOnce();
        expect(websocketSubscribeSpy).toHaveBeenCalledWith(expectedWebsocketTopic);

        // checks for setupReceiver
        expect(websocketReceiveMock).toHaveBeenCalledOnce();
        expect(websocketReceiveMock).toHaveBeenCalledWith(expectedWebsocketTopic);
        expect(receiveSubmissionEventEmitter).toHaveBeenCalledOnce();
        expect(receiveSubmissionEventEmitter).toHaveBeenCalledWith(submissionSyncPayload?.submission);

        // checks for setupSender
        expect(textSubmissionWithParticipation).toBeDefined();
        expect(textSubmissionWithParticipation?.participation?.exercise).toBeUndefined();
        expect(textSubmissionWithParticipation?.participation?.submissions).toBeEmpty();
        expect(websocketSendSpy).toHaveBeenCalledTimes(2);
        expect(websocketSendSpy).toHaveBeenNthCalledWith(1, expectedWebsocketTopic + '/update', textSubmissionWithParticipation);
        expect(websocketSendSpy.mock.calls[1][0]).toBe(expectedWebsocketTopic + '/patch');
        expect(websocketSendSpy.mock.calls[1][1]).toBeInstanceOf(SubmissionPatch);
    });

    it('should handle submission patch payloads.', () => {
        const mockEmitter = new Subject<SubmissionPatchPayload>();
        const receiver = jest.fn();
        jest.spyOn(websocketService, 'receive').mockReturnValue(mockEmitter);

        component.ngOnInit();
        component.receiveSubmissionPatch.subscribe(receiver);

        const patchString = JSON.stringify([{ op: 'replace', path: '/text', value: 'new text' }]);
        mockEmitter.next({
            submissionPatch: { patch: patchString },
            sender: currentUser.login!,
        });

        expect(receiver).toHaveBeenCalledWith({ patch: patchString });
    });

    it('should properly send submission patches.', () => {
        const sendSpy = jest.spyOn(websocketService, 'send');
        jest.spyOn(websocketService, 'receive').mockReturnValue(of());
        const mockEmitter = new Subject<SubmissionPatch>();

        component.submissionPatchObservable = mockEmitter;
        component.ngOnInit();

        const expectedTopic = '/topic/participations/3/team/text-submissions/patch';
        const patch: SubmissionPatch = { patch: JSON.stringify([{ op: 'replace', path: '/text', value: 'new text' }]) };
        mockEmitter.next(patch);
        expect(sendSpy).toHaveBeenCalledWith(expectedTopic, patch);
    });
});
