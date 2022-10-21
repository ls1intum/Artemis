import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TeamSubmissionSyncComponent } from 'app/exercises/shared/team-submission-sync/team-submission-sync.component';
import { MockProvider } from 'ng-mocks';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { AlertService } from 'app/core/util/alert.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { SessionStorageService } from 'ngx-webstorage';
import { MockHttpService } from '../../../helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';
import { Submission } from 'app/entities/submission.model';
import { Observable, of } from 'rxjs';
import { TextSubmission } from 'app/entities/text-submission.model';
import { SubmissionSyncPayload } from 'app/entities/submission-sync-payload.model';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';

describe('Team Submission Sync Component', () => {
    let fixture: ComponentFixture<TeamSubmissionSyncComponent>;
    let component: TeamSubmissionSyncComponent;
    let websocketService: JhiWebsocketService;
    let textSubmissionWithParticipation: Submission;
    let submissionObservableWithParticipation: Observable<Submission>;
    let submissionSyncPayload: SubmissionSyncPayload;
    let currentUser: User;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(AlertService),
                MockProvider(SessionStorageService),
                MockProvider(JhiWebsocketService),
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: HttpClient, useClass: MockHttpService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TeamSubmissionSyncComponent);
                component = fixture.componentInstance;

                websocketService = TestBed.inject(JhiWebsocketService);

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
        expect(websocketSendSpy).toHaveBeenCalledOnce();
        expect(websocketSendSpy).toHaveBeenCalledWith(expectedWebsocketTopic + '/update', textSubmissionWithParticipation);
    });
});
