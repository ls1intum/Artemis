/**
 * Vitest tests for TeamSubmissionSyncComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TeamSubmissionSyncComponent } from 'app/exercise/team-submission-sync/team-submission-sync.component';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockProvider } from 'ng-mocks';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Subject, of } from 'rxjs';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { SubmissionSyncPayload } from 'app/exercise/shared/entities/submission/submission-sync-payload.model';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { SubmissionPatchPayload } from 'app/exercise/shared/entities/submission/submission-patch-payload.model';
import { SubmissionPatch } from 'app/exercise/shared/entities/submission/submission-patch.model';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { ConnectionState } from 'app/foundation/service/websocket.service';
import { ApollonEditor } from '@tumaet/apollon';

/**
 * The reconnect sync loads the editor with a dynamic import (so that an individual text exercise never downloads it) and sends
 * after it resolved: let the import and the continuation after it finish before asserting on what was sent.
 */
async function flushDynamicImport(): Promise<void> {
    await vi.dynamicImportSettled();
    await new Promise<void>((resolve) => setTimeout(resolve));
}

describe('Team Submission Sync Component', () => {
    let fixture: ComponentFixture<TeamSubmissionSyncComponent>;
    let component: TeamSubmissionSyncComponent;
    let websocketService: WebsocketService;
    let textSubmissionWithParticipation: Submission;
    let submissionSyncPayload: SubmissionSyncPayload;
    let currentUser: User;
    let otherUser: User;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TeamSubmissionSyncComponent],
            providers: [
                MockProvider(AlertService),
                MockProvider(SessionStorageService),
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: HttpClient, useClass: MockHttpService },
            ],
        })
            .overrideTemplate(TeamSubmissionSyncComponent, '')
            .compileComponents();

        currentUser = new User();
        currentUser.login = 'ge12ebc';
        otherUser = new User();
        otherUser.login = 'other';
        vi.spyOn(TestBed.inject(AccountService), 'identity').mockResolvedValue(currentUser);

        fixture = TestBed.createComponent(TeamSubmissionSyncComponent);
        component = fixture.componentInstance;
        websocketService = TestBed.inject(WebsocketService);

        fixture.componentRef.setInput('exerciseType', ExerciseType.TEXT);
        const participation = new StudentParticipation(ParticipationType.STUDENT);
        participation.id = 3;
        fixture.componentRef.setInput('participation', participation);

        textSubmissionWithParticipation = new TextSubmission();
        textSubmissionWithParticipation.participation = new StudentParticipation();
        fixture.componentRef.setInput('submissionObservable', of(textSubmissionWithParticipation));

        // Ensure the identity promise in the constructor resolves.
        await Promise.resolve();

        submissionSyncPayload = new SubmissionSyncPayload();
        const submission = new TextSubmission();
        submission.id = 12;
        submissionSyncPayload.submission = submission;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('ngOnInit should work correctly', async () => {
        const expectedWebsocketTopic = '/topic/participations/3/team/text-submissions';
        submissionSyncPayload.sender = otherUser;
        const submissionSyncObservable = of(submissionSyncPayload);

        const receiveSubmissionEmitSpy = vi.spyOn(component.receiveSubmission, 'emit');
        const websocketSubscribeSpy = vi.spyOn(websocketService, 'subscribe').mockReturnValue(submissionSyncObservable);
        const websocketSendSpy = vi.spyOn(websocketService, 'send');

        // The first change detection runs ngOnInit. Awaiting in a zoneless test lets the fixture run it on its own, so do it explicitly once.
        fixture.detectChanges();
        await flushDynamicImport();

        expect(component.websocketTopic).toBe(expectedWebsocketTopic);
        expect(websocketSubscribeSpy).toHaveBeenCalledTimes(1);
        expect(websocketSubscribeSpy).toHaveBeenCalledWith(expectedWebsocketTopic);

        // checks for setupReceiver
        expect(receiveSubmissionEmitSpy).toHaveBeenCalledTimes(1);
        expect(receiveSubmissionEmitSpy).toHaveBeenCalledWith(submissionSyncPayload?.submission);

        // checks for setupSender
        expect(textSubmissionWithParticipation).toBeDefined();
        expect(textSubmissionWithParticipation?.participation?.exercise).toBeUndefined();
        expect(textSubmissionWithParticipation?.participation?.submissions).toHaveLength(0);
        // a text exercise has no patch endpoint on the server and nothing to re-announce: only the submission itself is sent
        expect(websocketSendSpy).toHaveBeenCalledTimes(1);
        expect(websocketSendSpy).toHaveBeenNthCalledWith(1, '/app/participations/3/team/text-submissions/update', textSubmissionWithParticipation);
    });

    it('should handle submission patch payloads.', () => {
        const mockEmitter = new Subject<SubmissionPatchPayload>();
        const receiver = vi.fn();
        vi.spyOn(websocketService, 'subscribe').mockReturnValue(mockEmitter.asObservable());

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
        const sendSpy = vi.spyOn(websocketService, 'send');
        vi.spyOn(websocketService, 'subscribe').mockReturnValue(of());
        const mockEmitter = new Subject<SubmissionPatch>();

        fixture.componentRef.setInput('submissionPatchObservable', mockEmitter);
        component.ngOnInit();

        const expectedTopic = '/app/participations/3/team/text-submissions/patch';
        const patch: SubmissionPatch = { patch: JSON.stringify([{ op: 'replace', path: '/text', value: 'new text' }]) };
        mockEmitter.next(patch);
        expect(sendSpy).toHaveBeenCalledWith(expectedTopic, patch);
    });

    it('emits `reconnected` on every STOMP (re)connect of a text exercise without sending a patch or loading Apollon', async () => {
        // A text exercise has no patch endpoint on the server and nothing to re-announce, so its page never downloads the diagram editor.
        const mock = websocketService as unknown as MockWebsocketService;
        const loadSpy = vi.spyOn(component as unknown as { loadApollon: () => Promise<unknown> }, 'loadApollon');
        const reconnectedSpy = vi.fn();
        component.reconnected.subscribe(reconnectedSpy);

        // Drop the submissionObservable so the only thing that could send is the reconnect path
        fixture.componentRef.setInput('submissionObservable', undefined);
        const sendSpy = vi.spyOn(websocketService, 'send');
        vi.spyOn(websocketService, 'subscribe').mockReturnValue(of());

        // The first change detection runs ngOnInit. Awaiting in a zoneless test lets the fixture run it on its own, so do it explicitly once.
        fixture.detectChanges();
        await flushDynamicImport();

        // BehaviorSubject in MockWebsocketService starts as connected=true: one reconnect emission
        expect(reconnectedSpy).toHaveBeenCalledTimes(1);

        // Disconnect: no fresh reconnect signal
        mock.setConnectionState(new ConnectionState(false, true));
        await flushDynamicImport();
        expect(reconnectedSpy).toHaveBeenCalledTimes(1);

        // Reconnect: the signal fires again
        mock.setConnectionState(new ConnectionState(true, true));
        await flushDynamicImport();
        expect(reconnectedSpy).toHaveBeenCalledTimes(2);

        // A duplicate connected=true must not re-fire (distinctUntilChanged on the `connected` field)
        mock.setConnectionState(new ConnectionState(true, true));
        await flushDynamicImport();
        expect(reconnectedSpy).toHaveBeenCalledTimes(2);

        expect(sendSpy).not.toHaveBeenCalled();
        expect(loadSpy).not.toHaveBeenCalled();
    });

    it('additionally re-broadcasts the awareness sync for modeling exercises on every STOMP (re)connect', async () => {
        // The shared (re)connect lifecycle is covered by the test above; this asserts only the modeling delta:
        // modeling sends a second patch carrying the initial awareness sync, on the first connect and on every reconnect.
        const mock = websocketService as unknown as MockWebsocketService;
        fixture.componentRef.setInput('exerciseType', ExerciseType.MODELING);
        const expectedTopic = '/app/participations/3/team/modeling-submissions/patch';
        const generateInitialSyncSpy = vi.spyOn(ApollonEditor, 'generateInitialSyncMessage').mockReturnValue('initial-sync-stub');
        const generateInitialAwarenessSyncSpy = vi.spyOn(ApollonEditor, 'generateInitialAwarenessSyncMessage').mockReturnValue('initial-awareness-stub');
        const reconnectedSpy = vi.fn();
        component.reconnected.subscribe(reconnectedSpy);
        fixture.componentRef.setInput('submissionObservable', undefined);
        const sendSpy = vi.spyOn(websocketService, 'send');
        vi.spyOn(websocketService, 'subscribe').mockReturnValue(of());

        // The first change detection runs ngOnInit. Awaiting in a zoneless test lets the fixture run it on its own, so do it explicitly once.
        fixture.detectChanges();
        await flushDynamicImport();

        expect(sendSpy.mock.calls.map((call) => [call[0], (call[1] as SubmissionPatch).patch])).toEqual([
            [expectedTopic, 'initial-sync-stub'],
            [expectedTopic, 'initial-awareness-stub'],
        ]);

        mock.setConnectionState(new ConnectionState(false, true));
        mock.setConnectionState(new ConnectionState(true, true));
        await flushDynamicImport();
        expect(sendSpy).toHaveBeenCalledTimes(4);
        expect((sendSpy.mock.calls[3][1] as SubmissionPatch).patch).toBe('initial-awareness-stub');
        expect(reconnectedSpy).toHaveBeenCalledTimes(2);

        generateInitialSyncSpy.mockRestore();
        generateInitialAwarenessSyncSpy.mockRestore();
    });

    it('reports an error and sends nothing when the editor cannot be downloaded', async () => {
        fixture.componentRef.setInput('exerciseType', ExerciseType.MODELING);
        vi.spyOn(component as unknown as { loadApollon: () => Promise<unknown> }, 'loadApollon').mockRejectedValue(new Error('offline'));
        const alertSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
        const reconnectedSpy = vi.fn();
        component.reconnected.subscribe(reconnectedSpy);
        fixture.componentRef.setInput('submissionObservable', undefined);
        const sendSpy = vi.spyOn(websocketService, 'send');
        vi.spyOn(websocketService, 'subscribe').mockReturnValue(of());

        fixture.detectChanges();
        await flushDynamicImport();

        expect(alertSpy).toHaveBeenCalledExactlyOnceWith('offline');
        expect(sendSpy).not.toHaveBeenCalled();
        expect(reconnectedSpy).not.toHaveBeenCalled();
    });

    it('does not send or emit when the component is destroyed while the editor downloads', async () => {
        fixture.componentRef.setInput('exerciseType', ExerciseType.MODELING);
        let finishDownload!: (module: typeof import('@tumaet/apollon')) => void;
        const download = new Promise<typeof import('@tumaet/apollon')>((resolve) => (finishDownload = resolve));
        vi.spyOn(component as unknown as { loadApollon: () => Promise<unknown> }, 'loadApollon').mockReturnValue(download);
        const generateInitialSyncSpy = vi.spyOn(ApollonEditor, 'generateInitialSyncMessage').mockReturnValue('initial-sync-stub');
        const reconnectedSpy = vi.fn();
        component.reconnected.subscribe(reconnectedSpy);
        fixture.componentRef.setInput('submissionObservable', undefined);
        const sendSpy = vi.spyOn(websocketService, 'send');
        vi.spyOn(websocketService, 'subscribe').mockReturnValue(of());

        fixture.detectChanges();
        // The student leaves the exercise while the download is still running.
        component.ngOnDestroy();
        finishDownload({ ApollonEditor } as unknown as typeof import('@tumaet/apollon'));
        await flushDynamicImport();

        expect(generateInitialSyncSpy).not.toHaveBeenCalled();
        expect(sendSpy).not.toHaveBeenCalled();
        expect(reconnectedSpy).not.toHaveBeenCalled();
    });

    it('should stop reacting to connection-state changes after ngOnDestroy', async () => {
        const mock = websocketService as unknown as MockWebsocketService;
        vi.spyOn(ApollonEditor, 'generateInitialSyncMessage').mockReturnValue('initial-sync-stub');
        const reconnectedSpy = vi.fn();
        component.reconnected.subscribe(reconnectedSpy);
        fixture.componentRef.setInput('submissionObservable', undefined);
        vi.spyOn(websocketService, 'subscribe').mockReturnValue(of());

        // The first change detection runs ngOnInit. Awaiting in a zoneless test lets the fixture run it on its own, so do it explicitly once.
        fixture.detectChanges();
        await flushDynamicImport();
        expect(reconnectedSpy).toHaveBeenCalledTimes(1);

        component.ngOnDestroy();
        mock.setConnectionState(new ConnectionState(false, true));
        mock.setConnectionState(new ConnectionState(true, true));
        await flushDynamicImport();
        expect(reconnectedSpy).toHaveBeenCalledTimes(1);
    });
});
