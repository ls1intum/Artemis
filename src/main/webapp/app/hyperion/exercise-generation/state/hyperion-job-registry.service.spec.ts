import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ConnectionState, WebsocketService } from 'app/foundation/service/websocket.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationEvent, HyperionGenerationStatus } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import {
    HYPERION_JOB_APPEARANCE_DEBOUNCE_MS,
    HYPERION_JOB_POLL_INTERVAL_MS,
    HyperionJobRegistryService,
} from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';

const LOGIN = 'ab12cde';

function user(login: string, imageUrl?: string): User {
    return { login, imageUrl } as User;
}

function event(type: HyperionGenerationEvent['type'], completionStatus?: HyperionGenerationEvent['completionStatus'], timestamp = '2026-07-10T20:00:00Z'): HyperionGenerationEvent {
    return { type, completionStatus, timestamp, message: `${type} happened` };
}

function status(partial: Partial<HyperionGenerationStatus>): HyperionGenerationStatus {
    return {
        jobId: 'j1',
        running: false,
        events: [],
        fileChanges: [],
        revertAvailable: false,
        ownedByCaller: true,
        cancellable: false,
        accountingState: 'COMPLETE',
        ...partial,
    } as HyperionGenerationStatus;
}

describe('HyperionJobRegistryService', () => {
    let identity: ReturnType<typeof signal<User | undefined>>;
    let connectionState: BehaviorSubject<ConnectionState>;
    let getStatus: Mock<(exerciseId: number) => Observable<HyperionGenerationStatus | null>>;
    let streams: Map<string, Subject<unknown>>;

    function configure(): void {
        identity = signal<User | undefined>(undefined);
        connectionState = new BehaviorSubject<ConnectionState>(new ConnectionState(false, false));
        getStatus = vi.fn(() => of(status({ running: true, events: [event('STARTED')] })) as Observable<HyperionGenerationStatus | null>);
        streams = new Map();

        const generationServiceMock = {
            getStatus: (exerciseId: number) => getStatus(exerciseId),
            subscribeToStream: (jobId: string) => {
                const stream = streams.get(jobId) ?? new Subject<unknown>();
                streams.set(jobId, stream);
                return stream.asObservable();
            },
        };

        TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useValue: { userIdentity: identity } },
                { provide: WebsocketService, useValue: { connectionState: connectionState.asObservable() } },
                { provide: HyperionExerciseGenerationService, useValue: generationServiceMock },
            ],
        });
    }

    /** Creates the registry and lets the identity effect run for the given login. */
    function createService(login: string | undefined = LOGIN): HyperionJobRegistryService {
        const service = TestBed.inject(HyperionJobRegistryService);
        identity.set(login ? user(login) : undefined);
        TestBed.tick();
        return service;
    }

    beforeEach(() => {
        vi.useFakeTimers();
        localStorage.clear();
        configure();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
        localStorage.clear();
    });

    it('keeps tracked runs across a reload, namespaced per login', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        expect(service.entries()).toHaveLength(1);

        // Simulate a browser reload: a brand-new injector reading the same localStorage.
        TestBed.resetTestingModule();
        configure();
        const reloaded = createService();
        expect(reloaded.entries()).toHaveLength(1);
        expect(reloaded.entries()[0].jobId).toBe('j1');
        expect(reloaded.entries()[0].exerciseTitle).toBe('Sorting');

        // A different user must not see them.
        TestBed.resetTestingModule();
        configure();
        expect(createService('xy98zab').entries()).toHaveLength(0);
    });

    it('takes the terminal state from the reconciled status, not from the websocket', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        expect(service.entries()[0].status).toBe('queued');

        getStatus.mockReturnValue(of(status({ running: false, events: [event('STARTED'), event('DONE', 'SUCCESS')] })));
        service.refresh();

        expect(service.entries()[0].status).toBe('saved');
        expect(service.activeCount()).toBe(0);
        expect(service.unseenCount()).toBe(1);
        vi.advanceTimersByTime(HYPERION_JOB_APPEARANCE_DEBOUNCE_MS);
        expect(service.indicatorState()).toBe('success');
    });

    it('marks a run whose exercise has moved on to another job as unknown', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });

        getStatus.mockReturnValue(of(status({ jobId: 'j2', running: true, events: [event('STARTED')] })));
        service.refresh();

        expect(service.entries()[0].status).toBe('unknown');
    });

    it('records when a run ended from the server event, so a late reconciliation still reports the real duration', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });

        getStatus.mockReturnValue(of(status({ running: false, events: [event('STARTED'), event('DONE', 'SUCCESS', '2026-07-10T20:21:00Z')] })));
        service.refresh();

        expect(service.entries()[0].endedAt).toBe('2026-07-10T20:21:00Z');
    });

    it('records no ending at all for a run whose ending it never saw', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });

        // The exercise has moved on to another job, so how and when this run stopped is simply not known.
        getStatus.mockReturnValue(of(status({ jobId: 'j2', running: true, events: [event('STARTED')] })));
        service.refresh();

        expect(service.entries()[0].endedAt).toBeUndefined();
    });

    it('reports a failed run as attention once the appearance debounce elapsed', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        getStatus.mockReturnValue(of(status({ running: false, events: [event('ERROR')] })));
        service.refresh();

        expect(service.indicatorState()).toBe('idle');
        vi.advanceTimersByTime(HYPERION_JOB_APPEARANCE_DEBOUNCE_MS);
        expect(service.entries()[0].status).toBe('failed');
        expect(service.indicatorState()).toBe('attention');
    });

    it('clears the unseen badge when a run is opened', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        getStatus.mockReturnValue(of(status({ running: false, events: [event('DONE', 'SUCCESS')] })));
        service.refresh();
        expect(service.unseenCount()).toBe(1);

        service.markSeen('j1');

        expect(service.unseenCount()).toBe(0);
        expect(service.entries()).toHaveLength(1);
        vi.advanceTimersByTime(HYPERION_JOB_APPEARANCE_DEBOUNCE_MS);
        expect(service.indicatorState()).toBe('idle');
    });

    it('clears the unseen badge and drops the entry when a run is dismissed for good', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        getStatus.mockReturnValue(of(status({ running: false, events: [event('ERROR')] })));
        service.refresh();

        service.dismiss('j1');

        expect(service.unseenCount()).toBe(0);
        expect(service.entries()).toHaveLength(0);

        // A dismissed run never comes back, not even when it is tracked again.
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        expect(service.entries()).toHaveLength(0);
    });

    it('polls while a run is active and stops once nothing is', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        expect(getStatus).not.toHaveBeenCalled();

        vi.advanceTimersByTime(HYPERION_JOB_POLL_INTERVAL_MS);
        expect(getStatus).toHaveBeenCalledTimes(1);
        vi.advanceTimersByTime(HYPERION_JOB_POLL_INTERVAL_MS);
        expect(getStatus).toHaveBeenCalledTimes(2);

        getStatus.mockReturnValue(of(status({ running: false, events: [event('DONE', 'SUCCESS')] })));
        vi.advanceTimersByTime(HYPERION_JOB_POLL_INTERVAL_MS);
        expect(getStatus).toHaveBeenCalledTimes(3);
        expect(service.activeCount()).toBe(0);

        vi.advanceTimersByTime(4 * HYPERION_JOB_POLL_INTERVAL_MS);
        expect(getStatus).toHaveBeenCalledTimes(3);
    });

    it('re-syncs when the websocket connection comes back, so a missed terminal event still resolves', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        getStatus.mockReturnValue(of(status({ running: false, events: [event('DONE', 'NEEDS_REVIEW')] })));

        connectionState.next(new ConnectionState(true, false));

        expect(getStatus).toHaveBeenCalledExactlyOnceWith(42);
        expect(service.entries()[0].status).toBe('needsReview');
    });

    it('does not reload storage when the same user identity object is replaced', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        service.markSeen('j1');
        const callsAfterTracking = getStatus.mock.calls.length;

        // A refreshed identity object for the same login: same user, new reference.
        identity.set(user(LOGIN, 'new-image.png'));
        TestBed.tick();

        expect(service.entries()).toHaveLength(1);
        expect(getStatus.mock.calls).toHaveLength(callsAfterTracking);
    });

    it('drops everything and stops touching the editor-only endpoint on logout', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });

        identity.set(undefined);
        TestBed.tick();

        expect(service.entries()).toHaveLength(0);
        service.refresh();
        expect(getStatus).not.toHaveBeenCalled();
        vi.advanceTimersByTime(4 * HYPERION_JOB_POLL_INTERVAL_MS);
        expect(getStatus).not.toHaveBeenCalled();
    });

    it('surfaces a failed reconciliation instead of swallowing it', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        getStatus.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        service.refresh();

        expect(service.loadFailed()).toBe(true);

        getStatus.mockReturnValue(of(status({ running: true, events: [event('STARTED')] })));
        service.refresh();
        expect(service.loadFailed()).toBe(false);
    });

    it('treats a websocket event as a hint that schedules one authoritative refresh', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        getStatus.mockReturnValue(of(status({ running: false, events: [event('DONE', 'PARTIAL')] })));

        streams.get('j1')!.next(event('PROGRESS'));
        streams.get('j1')!.next(event('PROGRESS'));
        expect(getStatus).not.toHaveBeenCalled();

        vi.advanceTimersByTime(1_000);

        expect(getStatus).toHaveBeenCalledExactlyOnceWith(42);
        expect(service.entries()[0].status).toBe('partial');
    });

    it('prunes runs older than a day', () => {
        const service = createService();
        const yesterday = new Date(Date.now() - 25 * 60 * 60 * 1000).toISOString();
        service.track({ jobId: 'old', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE', startedAt: yesterday });

        expect(service.entries()).toHaveLength(0);
    });
});
