import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { TumAetUiConfirmationService } from '@tumaet/ui-angular';
import { AlertService } from 'app/foundation/service/alert.service';
import { ConnectionState, WebsocketService } from 'app/foundation/service/websocket.service';
import { HyperionCodeGenerationApi } from 'app/openapi/api/hyperion-code-generation-api';
import { HyperionEvent, HyperionWebsocketService } from 'app/hyperion/services/hyperion-websocket.service';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { LegacyCodeGenerationComponent } from './legacy-code-generation.component';

describe('LegacyCodeGenerationComponent', () => {
    let fixture: ComponentFixture<LegacyCodeGenerationComponent>;
    let comp: LegacyCodeGenerationComponent;
    let streams: Map<string, Subject<HyperionEvent>>;
    let connection: BehaviorSubject<ConnectionState>;
    let api: { generateCode: ReturnType<typeof vi.fn>; getActiveCodeGenerationJob: ReturnType<typeof vi.fn> };
    let pull: ReturnType<typeof vi.fn>;
    let confirmations: TumAetUiConfirmationService;

    beforeEach(async () => {
        vi.useFakeTimers();
        sessionStorage.clear();
        streams = new Map();
        connection = new BehaviorSubject(new ConnectionState(false, false));
        api = { generateCode: vi.fn(() => of({ jobId: 'job-1' })), getActiveCodeGenerationJob: vi.fn(() => of(undefined)) };
        pull = vi.fn(() => of(undefined));
        await TestBed.configureTestingModule({
            imports: [LegacyCodeGenerationComponent],
            providers: [
                { provide: HyperionCodeGenerationApi, useValue: api },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: WebsocketService, useValue: { connectionState: connection.asObservable() } },
                { provide: AlertService, useValue: { warning: vi.fn(), addAlert: vi.fn() } },
                { provide: CodeEditorRepositoryService, useValue: { pull } },
                { provide: ExerciseReviewCommentService, useValue: { threads: signal([]), getSelectedFeedbackThreadIdsForRepository: vi.fn(() => []) } },
                {
                    provide: HyperionWebsocketService,
                    useValue: {
                        subscribeToJob: (id: string) => {
                            const stream = streams.get(id) ?? new Subject<HyperionEvent>();
                            streams.set(id, stream);
                            return stream;
                        },
                        unsubscribeFromJob: vi.fn(),
                    },
                },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(LegacyCodeGenerationComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', { id: 42 });
        fixture.componentRef.setInput('selectedRepository', RepositoryType.TEMPLATE);
        fixture.detectChanges();
        confirmations = fixture.debugElement.injector.get(TumAetUiConfirmationService);
        vi.spyOn(confirmations, 'confirm').mockImplementation((request) => request.accept());
    });

    afterEach(() => {
        fixture.destroy();
        vi.useRealTimers();
        vi.restoreAllMocks();
        sessionStorage.clear();
    });

    it('uses the legacy endpoint and shows the actual terminal state, not whole-exercise verification', () => {
        const refreshed = vi.fn();
        comp.refreshRequested.subscribe(refreshed);
        comp.generateCode();
        expect(api.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.TEMPLATE });
        expect(comp.isGeneratingCode()).toBe(true);
        streams.get('job-1')!.next({ type: 'DONE', success: true, completionStatus: 'PARTIAL', attempts: 2 });
        expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.TEMPLATE)?.state).toBe('warning');
        expect(comp.isGeneratingCode()).toBe(false);
        expect(refreshed).toHaveBeenCalledTimes(1);
    });

    it('forwards the selected feedback IDs through the existing legacy request contract', () => {
        vi.spyOn(TestBed.inject(ExerciseReviewCommentService), 'getSelectedFeedbackThreadIdsForRepository').mockReturnValue([7]);
        comp.generateCode();
        expect(api.generateCode).toHaveBeenCalledWith(42, { repositoryType: RepositoryType.TEMPLATE, selectedFeedbackThreadIds: [7] });
    });

    it('debounces file activity pulls and does not pull another open repository', () => {
        comp.generateCode();
        streams.get('job-1')!.next({ type: 'FILE_UPDATED', path: 'Stack.java' });
        streams.get('job-1')!.next({ type: 'NEW_FILE', path: 'Item.java' });
        vi.advanceTimersByTime(250);
        expect(pull).toHaveBeenCalledTimes(1);
        fixture.componentRef.setInput('selectedRepository', RepositoryType.TESTS);
        fixture.detectChanges();
        streams.get('job-1')!.next({ type: 'FILE_UPDATED', path: 'Stack.java' });
        vi.advanceTimersByTime(250);
        expect(pull).toHaveBeenCalledTimes(1);
    });

    it('waits for slot release before starting the next selected repository', () => {
        comp.setCodeGenerationRepositoryEnabled(RepositoryType.SOLUTION, true);
        comp.generateCode();
        api.getActiveCodeGenerationJob.mockReturnValueOnce(of({ jobId: 'job-1', repositoryType: RepositoryType.SOLUTION })).mockReturnValueOnce(of(undefined));
        api.generateCode.mockReturnValueOnce(of({ jobId: 'job-2' }));
        streams.get('job-1')!.next({ type: 'DONE', success: true, attempts: 1 });
        expect(api.generateCode).toHaveBeenCalledTimes(1);
        vi.advanceTimersByTime(1000);
        expect(api.generateCode).toHaveBeenCalledTimes(2);
        expect(api.generateCode).toHaveBeenLastCalledWith(42, { repositoryType: RepositoryType.TEMPLATE });
        streams.get('job-2')!.next({ type: 'ERROR', message: 'Build failed' });
        expect(comp.isGeneratingCode()).toBe(false);
    });

    it('restores an active server run without submitting another generation', () => {
        fixture.destroy();
        api.getActiveCodeGenerationJob.mockReturnValue(of({ jobId: 'existing', repositoryType: RepositoryType.TESTS }));
        fixture = TestBed.createComponent(LegacyCodeGenerationComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', { id: 42 });
        fixture.componentRef.setInput('selectedRepository', RepositoryType.TESTS);
        fixture.detectChanges();
        expect(comp.isGeneratingCode()).toBe(true);
        expect(streams.get('existing')?.observed).toBe(true);
        expect(api.generateCode).not.toHaveBeenCalled();
    });

    it('reauthorizes the active job on reconnect without submitting another generation', () => {
        comp.generateCode();
        api.getActiveCodeGenerationJob.mockReturnValue(of({ jobId: 'job-1', repositoryType: RepositoryType.TEMPLATE }));
        connection.next(new ConnectionState(true, false));
        expect(api.getActiveCodeGenerationJob).toHaveBeenCalledTimes(2);
        expect(api.generateCode).toHaveBeenCalledTimes(1);
        expect(comp.isGeneratingCode()).toBe(true);
    });

    it.each(['absent', 'failed'])('defers reconnect restoration while the start is pending and status is %s', (status) => {
        const start = new Subject<{ jobId: string }>();
        api.generateCode.mockReturnValueOnce(start).mockReturnValueOnce(of({ jobId: 'job-2' }));
        comp.setCodeGenerationRepositoryEnabled(RepositoryType.SOLUTION, true);
        comp.generateCode();
        const readsBeforeReconnect = api.getActiveCodeGenerationJob.mock.calls.length;
        api.getActiveCodeGenerationJob.mockReturnValue(status === 'absent' ? of(undefined) : throwError(() => new Error('Unavailable')));
        connection.next(new ConnectionState(true, false));
        expect(api.getActiveCodeGenerationJob).toHaveBeenCalledTimes(readsBeforeReconnect);
        expect(api.generateCode).toHaveBeenCalledTimes(1);
        expect(comp.isGeneratingCode()).toBe(true);

        api.getActiveCodeGenerationJob.mockReturnValue(of({ jobId: 'accepted', repositoryType: RepositoryType.SOLUTION }));
        start.next({ jobId: 'accepted' });
        start.complete();
        expect(streams.get('accepted')?.observed).toBe(true);
        expect(api.generateCode).toHaveBeenCalledTimes(1);
        expect(comp.isGeneratingCode()).toBe(true);

        api.getActiveCodeGenerationJob.mockReturnValue(of(undefined));
        streams.get('accepted')!.next({ type: 'DONE', success: true, attempts: 1 });
        expect(api.generateCode).toHaveBeenCalledTimes(2);
        expect(api.generateCode).toHaveBeenLastCalledWith(42, { repositoryType: RepositoryType.TEMPLATE });
    });

    it('does not claim a terminal outcome when reconnect finds no active job and no replay', () => {
        comp.generateCode();
        connection.next(new ConnectionState(true, false));
        expect(api.generateCode).toHaveBeenCalledTimes(1);
        expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.TEMPLATE)?.state).toBe('unknown');
        expect(comp.isGeneratingCode()).toBe(false);
    });

    it('keeps automatic generation pending until the dynamic editor blocker clears', () => {
        const previousState = window.history.state;
        try {
            window.history.replaceState({ autoStartCodeGenerationAllRepositories: true }, '');
            fixture.componentRef.setInput('blockedReason', 'unsavedChanges');
            fixture.detectChanges();
            expect(api.generateCode).not.toHaveBeenCalled();
            expect(window.history.state.autoStartCodeGenerationAllRepositories).toBe(true);
            fixture.componentRef.setInput('blockedReason', undefined);
            fixture.detectChanges();
            expect(api.generateCode).toHaveBeenCalledExactlyOnceWith(42, { repositoryType: RepositoryType.SOLUTION, initialAutoGeneration: true });
            expect(window.history.state.autoStartCodeGenerationAllRepositories).toBe(false);
            fixture.detectChanges();
            expect(api.generateCode).toHaveBeenCalledTimes(1);
        } finally {
            window.history.replaceState(previousState, '');
        }
    });

    it('retains a restored queue while blocked and rechecks slot release before resuming', () => {
        comp.setCodeGenerationRepositoryEnabled(RepositoryType.SOLUTION, true);
        comp.generateCode();
        fixture.destroy();
        api.generateCode.mockClear();
        api.generateCode.mockReturnValueOnce(of({ jobId: 'job-2' }));
        api.getActiveCodeGenerationJob
            .mockReturnValueOnce(of(undefined))
            .mockReturnValueOnce(of({ jobId: 'previous-cleanup', repositoryType: RepositoryType.SOLUTION }))
            .mockReturnValueOnce(of(undefined));
        fixture = TestBed.createComponent(LegacyCodeGenerationComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', { id: 42 });
        fixture.componentRef.setInput('selectedRepository', RepositoryType.TEMPLATE);
        fixture.componentRef.setInput('blockedReason', 'unsavedChanges');
        fixture.detectChanges();
        expect(api.generateCode).not.toHaveBeenCalled();
        expect(comp.isGeneratingCode()).toBe(true);
        expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.TEMPLATE)?.state).toBe('queued');
        fixture.componentRef.setInput('blockedReason', undefined);
        fixture.detectChanges();
        expect(api.generateCode).not.toHaveBeenCalled();
        vi.advanceTimersByTime(1000);
        expect(api.generateCode).toHaveBeenCalledExactlyOnceWith(42, { repositoryType: RepositoryType.TEMPLATE });
        fixture.detectChanges();
        expect(api.generateCode).toHaveBeenCalledTimes(1);
    });

    it('holds the next repository without dropping it when the editor becomes blocked', () => {
        comp.setCodeGenerationRepositoryEnabled(RepositoryType.SOLUTION, true);
        comp.generateCode();
        fixture.componentRef.setInput('blockedReason', 'otherAiOperation');
        fixture.detectChanges();
        api.generateCode.mockReturnValueOnce(of({ jobId: 'job-2' }));
        streams.get('job-1')!.next({ type: 'DONE', success: true, attempts: 1 });
        expect(api.generateCode).toHaveBeenCalledTimes(1);
        expect(comp.isGeneratingCode()).toBe(true);
        expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.TEMPLATE)?.state).toBe('queued');
        vi.advanceTimersByTime(1000);
        expect(api.generateCode).toHaveBeenCalledTimes(1);
        fixture.componentRef.setInput('blockedReason', undefined);
        fixture.detectChanges();
        expect(api.generateCode).toHaveBeenCalledTimes(2);
        expect(api.generateCode).toHaveBeenLastCalledWith(42, { repositoryType: RepositoryType.TEMPLATE });
        fixture.detectChanges();
        vi.advanceTimersByTime(1000);
        expect(api.generateCode).toHaveBeenCalledTimes(2);
    });

    it('does not start when blocked and rechecks eligibility after confirmation', () => {
        fixture.componentRef.setInput('blockedReason', 'blocked');
        fixture.detectChanges();
        comp.generateCode();
        expect(api.generateCode).not.toHaveBeenCalled();
        fixture.componentRef.setInput('blockedReason', undefined);
        fixture.detectChanges();
        vi.mocked(confirmations.confirm).mockImplementation((request) => {
            fixture.componentRef.setInput('blockedReason', 'blocked');
            request.accept();
        });
        comp.generateCode();
        expect(api.generateCode).not.toHaveBeenCalled();
    });

    it('cancels local observation on navigation without claiming to cancel the server job', () => {
        comp.generateCode();
        expect(streams.get('job-1')?.observed).toBe(true);
        fixture.componentRef.setInput('exercise', { id: 43 });
        fixture.detectChanges();
        expect(streams.get('job-1')?.observed).toBe(false);
        expect(api.getActiveCodeGenerationJob).toHaveBeenLastCalledWith(43);
        expect(comp.isGeneratingCode()).toBe(false);
    });

    it('stops the queue on a start failure without submitting another repository', () => {
        comp.setCodeGenerationRepositoryEnabled(RepositoryType.SOLUTION, true);
        api.generateCode.mockReturnValue(throwError(() => new Error('unavailable')));
        comp.generateCode();
        expect(api.generateCode).toHaveBeenCalledTimes(1);
        expect(comp.isGeneratingCode()).toBe(false);
        expect(comp.codeGenerationStatuses().find((status) => status.repositoryType === RepositoryType.TEMPLATE)?.state).toBe('skipped');
    });
});
