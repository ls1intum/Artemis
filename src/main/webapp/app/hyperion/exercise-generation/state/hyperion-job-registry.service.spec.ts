import { AuthoringRunPage } from 'app/openapi/model/authoring-run-page';
import { TestBed } from '@angular/core/testing';
import { effect, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, Subject, of, throwError } from 'rxjs';
import { Mock, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { ConnectionState, WebsocketService } from 'app/foundation/service/websocket.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionGenerationEvent, HyperionGenerationStatus } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { HYPERION_JOB_POLL_INTERVAL_MS, HyperionJobRegistryService, isTerminalHyperionJobStatus } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';

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
    let getRuns: Mock<(beforeId?: number) => Observable<AuthoringRunPage>>;
    let getRunStatus: Mock<(exerciseId: number, runId: string) => Observable<HyperionGenerationStatus | null>>;
    let streams: Map<string, Subject<unknown>>;
    let getRunAccess: Mock<(jobIds: string[]) => Observable<string[]>>;

    function configure(): void {
        identity = signal<User | undefined>(undefined);
        connectionState = new BehaviorSubject<ConnectionState>(new ConnectionState(false, false));
        getStatus = vi.fn(() => of(status({ running: true, events: [event('STARTED')] })) as Observable<HyperionGenerationStatus | null>);
        streams = new Map();
        getRuns = vi.fn(() => of({ runs: [] }));
        getRunStatus = vi.fn((exerciseId) => getStatus(exerciseId));
        getRunAccess = vi.fn((jobIds) => of(jobIds));

        const generationServiceMock = {
            getRuns: (beforeId?: number) => getRuns(beforeId),
            getRunAccess: (jobIds: string[]) => getRunAccess(jobIds),
            getRunStatus: (exerciseId: number, runId: string) => getRunStatus(exerciseId, runId),
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

    it('bounds requests and subscriptions without losing active jobs', () => {
        const service = createService();
        const pending = new Map<number, Subject<HyperionGenerationStatus | null>>();
        getStatus.mockImplementation((exerciseId) => {
            const request = new Subject<HyperionGenerationStatus | null>();
            pending.set(exerciseId, request);
            return request;
        });
        for (let id = 1; id <= 30; id++) {
            service.track({ jobId: `j${id}`, exerciseId: id, courseId: 7, exerciseTitle: 'Sorting', mode: 'ADAPT' });
        }
        expect(service.entries()).toHaveLength(30);
        expect([...streams.values()].filter((stream) => stream.observed)).toHaveLength(20);
        service.refresh();
        service.refresh();
        expect(getStatus).toHaveBeenCalledTimes(4);
        const first = [...pending.keys()][0];
        pending.get(first)!.next(status({ jobId: `j${first}`, running: true }));
        expect(getStatus).toHaveBeenCalledTimes(5);
        for (const [id, request] of pending) {
            request.next(status({ jobId: `j${id}`, running: true }));
        }
        expect(getStatus).toHaveBeenCalledTimes(30);
        expect(service.entries().filter((entry) => !isTerminalHyperionJobStatus(entry.status)).length).toBe(30);
    });

    it('bounds a hung sweep and cancels queued requests on logout', () => {
        const service = createService();
        getStatus.mockReturnValue(new Subject<HyperionGenerationStatus | null>());
        for (let id = 1; id <= 10; id++) {
            service.track({ jobId: `j${id}`, exerciseId: id, courseId: 7, exerciseTitle: 'Sorting', mode: 'ADAPT' });
        }
        service.refresh();
        expect(getStatus).toHaveBeenCalledTimes(4);
        vi.advanceTimersByTime(10_000);
        expect(getStatus).toHaveBeenCalledTimes(8);
        expect(service.loadFailed()).toBe(true);
        identity.set(undefined);
        TestBed.tick();
        vi.advanceTimersByTime(30_000);
        expect(getStatus).toHaveBeenCalledTimes(8);
        expect(service.entries()).toHaveLength(0);
    });

    it('lets queued requests proceed after an individual failure', () => {
        const service = createService();
        const pending = new Subject<HyperionGenerationStatus | null>();
        getStatus.mockReturnValue(pending);
        for (let id = 1; id <= 6; id++) {
            service.track({ jobId: `j${id}`, exerciseId: id, courseId: 7, exerciseTitle: 'Sorting', mode: 'ADAPT' });
        }
        service.refresh();
        expect(getStatus).toHaveBeenCalledTimes(4);
        pending.error(new HttpErrorResponse({ status: 503 }));
        expect(getStatus).toHaveBeenCalledTimes(6);
        expect(service.loadFailed()).toBe(true);
        expect(service.entries().filter((entry) => !isTerminalHyperionJobStatus(entry.status)).length).toBe(6);
    });

    it.each([{ status: 'obsolete' }, { mode: undefined }, { mode: 'obsolete' }, { seen: undefined }, { exerciseId: -1 }])(
        'rejects malformed persisted entries %j before starting requests',
        (invalid) => {
            localStorage.setItem(
                `artemis.hyperion.jobRegistry.${LOGIN}`,
                JSON.stringify({
                    version: 1,
                    entries: [
                        {
                            jobId: 'old',
                            exerciseId: 42,
                            courseId: 7,
                            exerciseTitle: 'Sorting',
                            mode: 'GENERATE',
                            startedAt: new Date().toISOString(),
                            status: 'running',
                            seen: false,
                            ...invalid,
                        },
                    ],
                    dismissed: [],
                }),
            );
            const service = createService();
            expect(service.entries()).toHaveLength(0);
            expect(getStatus).not.toHaveBeenCalled();
            expect(streams.size).toBe(0);
        },
    );

    it('rediscovers runs after reload without restoring exercise metadata from browser storage', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        expect(service.entries()).toHaveLength(1);

        // Simulate a browser reload: a brand-new injector reading the same localStorage.
        TestBed.resetTestingModule();
        configure();
        expect(JSON.parse(localStorage.getItem(`artemis.hyperion.jobRegistry.${LOGIN}`)!)).toEqual({ version: 2, seenJobIds: [] });
        getRuns.mockReturnValue(
            of({
                runs: [{ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', kind: 'CREATE', status: 'SAVED', running: false, startedAt: '2025-01-01T00:00:00Z' }],
            }),
        );
        const reloaded = createService();
        expect(reloaded.entries()).toHaveLength(1);
        expect(reloaded.entries()[0].jobId).toBe('j1');
        expect(reloaded.entries()[0].exerciseTitle).toBe('Sorting');

        // A different user must not see them.
        TestBed.resetTestingModule();
        configure();
        expect(createService('xy98zab').entries()).toHaveLength(0);
    });

    it.each(['status', 'error'])('discards an in-flight %s from the previous login without stopping the new run', (response) => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        const pending = new Subject<HyperionGenerationStatus | null>();
        getStatus.mockReturnValueOnce(pending);
        service.refresh();

        identity.set(user('other-editor'));
        TestBed.tick();
        service.track({ jobId: 'j2', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        if (response === 'status') {
            pending.next(status({ jobId: 'j1', running: false, events: [event('DONE', 'SUCCESS')] }));
        } else {
            pending.error(new HttpErrorResponse({ status: 500 }));
        }
        expect(service.entries()[0].jobId).toBe('j2');
        expect(service.entries().filter((entry) => !isTerminalHyperionJobStatus(entry.status)).length).toBe(1);
        expect(service.loadFailed()).toBe(false);

        getStatus.mockReturnValue(of(status({ jobId: 'j2', running: false, events: [event('DONE', 'SUCCESS')] })));
        service.refresh();
        expect(service.entries()[0].status).toBe('saved');
    });

    it.each(['previousJob', 'absent'])('keeps a newly tracked run active when an older request returns %s', (response) => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        const pending = new Subject<HyperionGenerationStatus | null>();
        getStatus.mockReturnValueOnce(pending);
        service.refresh();
        service.track({ jobId: 'j2', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        pending.next(response === 'absent' ? null : status({ jobId: 'j1', running: false, events: [event('DONE', 'SUCCESS')] }));

        expect(service.entries().find((entry) => entry.jobId === 'j1')?.status).toBe(response === 'absent' ? 'unknown' : 'saved');
        expect(service.entries().find((entry) => entry.jobId === 'j2')?.status).toBe('queued');
        expect(service.entries().filter((entry) => !isTerminalHyperionJobStatus(entry.status)).length).toBe(1);
        getStatus.mockReturnValue(of(status({ jobId: 'j2', running: false, events: [event('DONE', 'SUCCESS')] })));
        vi.advanceTimersByTime(HYPERION_JOB_POLL_INTERVAL_MS);
        expect(service.entries().find((entry) => entry.jobId === 'j2')?.status).toBe('saved');
    });

    it('takes the terminal state from the reconciled status, not from the websocket', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        expect(service.entries()[0].status).toBe('queued');

        getStatus.mockReturnValue(of(status({ running: false, events: [event('STARTED'), event('DONE', 'SUCCESS')] })));
        service.refresh();

        expect(service.entries()[0].status).toBe('saved');
        expect(service.entries().filter((entry) => !isTerminalHyperionJobStatus(entry.status)).length).toBe(0);
        expect(service.entries().filter((entry) => isTerminalHyperionJobStatus(entry.status) && !entry.seen).length).toBe(1);
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

    it('retains the server phase and cancellation permission without parsing progress messages', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'ADAPT' });
        getStatus.mockReturnValue(of(status({ running: true, cancellable: true, events: [{ ...event('PROGRESS'), phase: 'REVIEWING' }] })));
        service.refresh();
        expect(service.entries()[0]).toMatchObject({ phase: 'REVIEWING', cancellable: true });
        getStatus.mockReturnValue(of(status({ running: true, cancellable: false, events: [{ ...event('PROGRESS'), phase: 'SAVING' }] })));
        service.refresh();
        expect(service.entries()[0]).toMatchObject({ phase: 'SAVING', cancellable: false });
    });

    it('records a failed run from the authoritative response', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        getStatus.mockReturnValue(of(status({ running: false, events: [event('ERROR')] })));
        service.refresh();

        expect(service.entries()[0].status).toBe('failed');
    });

    it('opening an active job does not acknowledge its future completion', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        service.markSeen('j1');
        expect(service.entries()[0].seen).toBe(false);
        getStatus.mockReturnValue(of(status({ running: true, events: [event('STARTED')] })));
        service.refresh();
        service.markSeen('j1');
        getStatus.mockReturnValue(of(status({ running: false, events: [event('DONE', 'SUCCESS')] })));
        service.refresh();
        expect(service.entries().filter((entry) => isTerminalHyperionJobStatus(entry.status) && !entry.seen).length).toBe(1);
        service.markSeen('j1');
        expect(service.entries().filter((entry) => isTerminalHyperionJobStatus(entry.status) && !entry.seen).length).toBe(0);
    });

    it('acknowledges a terminal reconciliation that arrives after an open page starts observing', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        TestBed.runInInjectionContext(() => effect(() => service.markSeen('j1')));
        TestBed.tick();
        expect(service.entries()[0].seen).toBe(false);

        getStatus.mockReturnValue(of(status({ running: false, events: [event('DONE', 'SUCCESS')] })));
        service.refresh();
        TestBed.tick();

        expect(service.entries()[0].seen).toBe(true);
        expect(service.entries().filter((entry) => isTerminalHyperionJobStatus(entry.status) && !entry.seen).length).toBe(0);
        TestBed.tick();
        expect(service.entries().filter((entry) => isTerminalHyperionJobStatus(entry.status) && !entry.seen).length).toBe(0);
    });

    it('clears the unseen badge when a run is opened', () => {
        const service = createService();
        service.track({ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE' });
        getStatus.mockReturnValue(of(status({ running: false, events: [event('DONE', 'SUCCESS')] })));
        service.refresh();
        expect(service.entries().filter((entry) => isTerminalHyperionJobStatus(entry.status) && !entry.seen).length).toBe(1);

        service.markSeen('j1');

        expect(service.entries().filter((entry) => isTerminalHyperionJobStatus(entry.status) && !entry.seen).length).toBe(0);
        expect(service.entries()).toHaveLength(1);
    });

    it('polls active statuses and relies on authorized history for terminal rows', () => {
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
        expect(service.entries().filter((entry) => !isTerminalHyperionJobStatus(entry.status)).length).toBe(0);

        getRuns.mockReturnValue(of({ runs: [{ jobId: 'j1', exerciseId: 42, courseId: 7, kind: 'CREATE', status: 'SAVED', running: false, startedAt: '2025-01-01T00:00:00Z' }] }));
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

    it('discovers a run started in another browser even when this browser has never tracked one', () => {
        const service = createService();
        expect(service.entries()).toEqual([]);
        getRuns.mockReturnValue(
            of({
                runs: [{ jobId: 'remote', exerciseId: 42, sourceExerciseId: 41, courseId: 7, kind: 'VARIANT', status: 'SAVED', running: false, startedAt: '2025-01-01T00:00:00Z' }],
            }),
        );
        vi.advanceTimersByTime(HYPERION_JOB_POLL_INTERVAL_MS);
        expect(service.entries()[0]).toMatchObject({ jobId: 'remote', kind: 'VARIANT', status: 'saved', sourceExerciseId: 41 });
        expect(getStatus).not.toHaveBeenCalled();
    });

    it.each([
        [true, 403],
        [true, 404],
    ])('evicts a retained run after same-login access is revoked (running=%s, HTTP %s)', (running, responseStatus) => {
        getRuns.mockReturnValue(
            of({
                runs: [{ jobId: 'j1', exerciseId: 42, courseId: 7, exerciseTitle: 'Private exercise', kind: 'ADAPT', status: 'SAVED', running, startedAt: '2025-01-01T00:00:00Z' }],
            }),
        );
        const service = createService();
        expect(service.entries()).toHaveLength(1);
        getRuns.mockReturnValue(of({ runs: [] }));
        getRunStatus.mockReturnValue(throwError(() => new HttpErrorResponse({ status: responseStatus })));

        vi.advanceTimersByTime(HYPERION_JOB_POLL_INTERVAL_MS);

        expect(getRunStatus).toHaveBeenCalledWith(42, 'j1');
        expect(service.entries()).toEqual([]);
        expect(streams.get('j1')?.observed ?? false).toBe(false);
        expect(service.loadFailed()).toBe(false);
    });

    it.each([0, 404, 500, 503])('retains terminal history and reports a transient HTTP %s revalidation failure', (responseStatus) => {
        getRuns.mockReturnValue(of({ runs: [{ jobId: 'j1', exerciseId: 42, courseId: 7, kind: 'CREATE', status: 'SAVED', running: false, startedAt: '2025-01-01T00:00:00Z' }] }));
        const service = createService();
        getRuns.mockReturnValue(of({ runs: [] }));
        getRunAccess.mockReturnValue(throwError(() => new HttpErrorResponse({ status: responseStatus })));

        service.refresh();

        expect(service.entries()[0]).toMatchObject({ jobId: 'j1', status: 'saved' });
        expect(service.loadFailed()).toBe(true);
    });

    it('revalidates an older retained page without removing authorized history or refetching the current page', () => {
        getRuns.mockReturnValueOnce(
            of({ runs: [{ jobId: 'j1', exerciseId: 42, courseId: 7, kind: 'CREATE', status: 'SAVED', running: false, startedAt: '2025-01-01T00:00:00Z' }], nextBeforeId: 100 }),
        );
        const service = createService();
        expect(getRunStatus).not.toHaveBeenCalled();
        getRuns.mockReturnValueOnce(
            of({ runs: [{ jobId: 'j2', exerciseId: 43, courseId: 7, kind: 'CREATE', status: 'SAVED', running: false, startedAt: '2024-01-01T00:00:00Z' }] }),
        );

        service.loadMoreHistory();

        expect(getRunAccess).toHaveBeenCalledExactlyOnceWith(['j1']);
        expect(getRunStatus).not.toHaveBeenCalled();
        expect(service.entries().map((entry) => entry.jobId)).toEqual(['j1', 'j2']);
        expect(service.hasMoreHistory()).toBe(false);
    });

    it('reauthorizes ten retained history pages with one bounded bulk request per poll', () => {
        const pages: AuthoringRunPage[] = Array.from({ length: 10 }, (_, page) => ({
            runs: Array.from({ length: 50 }, (_, row) => ({
                jobId: `history-${page}-${row}`,
                exerciseId: 42,
                courseId: 7,
                kind: 'CREATE',
                status: 'SAVED',
                running: false,
                startedAt: '2025-01-01T00:00:00Z',
            })),
            nextBeforeId: page + 1,
        }));
        for (const page of pages) getRuns.mockReturnValueOnce(of(page));
        const service = createService();
        for (let page = 1; page < pages.length; page++) service.loadMoreHistory();
        expect(service.entries()).toHaveLength(500);
        getRunAccess.mockClear();
        getRunStatus.mockClear();
        getRuns.mockReturnValue(of(pages[0]));

        vi.advanceTimersByTime(3 * HYPERION_JOB_POLL_INTERVAL_MS);

        expect(getRunAccess).toHaveBeenCalledTimes(3);
        for (const [ids] of getRunAccess.mock.calls) expect(ids).toHaveLength(450);
        expect(getRunStatus).not.toHaveBeenCalled();
        expect(service.entries()).toHaveLength(500);
    });

    it.each(['SAVED', 'PARTIAL'] as const)('removes a %s row omitted from the authorized subset', (runStatus) => {
        getRuns.mockReturnValue(of({ runs: [{ jobId: 'j1', exerciseId: 42, courseId: 7, kind: 'CREATE', status: runStatus, running: false, startedAt: '2025-01-01T00:00:00Z' }] }));
        const service = createService();
        getRuns.mockReturnValue(of({ runs: [] }));
        getRunAccess.mockReturnValue(of([]));
        service.refresh();
        expect(service.entries()).toEqual([]);
        expect(getRunStatus).not.toHaveBeenCalled();
        expect(service.loadFailed()).toBe(false);
    });

    it('rotates bounded access batches so histories larger than 500 are fully reauthorized', () => {
        const pages: AuthoringRunPage[] = Array.from({ length: 22 }, (_, page) => ({
            runs: Array.from({ length: 50 }, (_, row) => ({
                jobId: `history-${page}-${row}`,
                exerciseId: 42,
                courseId: 7,
                kind: 'CREATE',
                status: 'SAVED',
                running: false,
                startedAt: '2025-01-01T00:00:00Z',
            })),
            nextBeforeId: page + 1,
        }));
        for (const page of pages) getRuns.mockReturnValueOnce(of(page));
        const service = createService();
        for (let page = 1; page < pages.length; page++) service.loadMoreHistory();
        getRunAccess.mockClear();
        getRuns.mockReturnValue(of(pages[0]));
        vi.advanceTimersByTime(4 * HYPERION_JOB_POLL_INTERVAL_MS);
        expect(getRunAccess).toHaveBeenCalledTimes(4);
        const checked = new Set<string>();
        for (const [ids] of getRunAccess.mock.calls) {
            expect(ids.length).toBeLessThanOrEqual(500);
            for (const id of ids) checked.add(id);
        }
        expect(checked.size).toBe(1050);
        expect(service.entries()).toHaveLength(1100);
        expect(getRunStatus).not.toHaveBeenCalled();
    });

    it('cancels pending access checks on login change and ignores their results', () => {
        getRuns.mockReturnValue(of({ runs: [{ jobId: 'j1', exerciseId: 42, courseId: 7, kind: 'CREATE', status: 'SAVED', running: false, startedAt: '2025-01-01T00:00:00Z' }] }));
        const service = createService();
        getRuns.mockReturnValue(of({ runs: [] }));
        const pending = new Subject<string[]>();
        getRunAccess.mockReturnValueOnce(pending);
        service.refresh();
        expect(pending.observed).toBe(true);
        identity.set(user('other-editor'));
        TestBed.tick();
        expect(pending.observed).toBe(false);
        pending.next(['j1']);
        expect(service.entries()).toEqual([]);
        expect(service.loadFailed()).toBe(false);
    });

    it('evicts the checked batch when the bulk endpoint denies the editor role', () => {
        getRuns.mockReturnValue(of({ runs: [{ jobId: 'j1', exerciseId: 42, courseId: 7, kind: 'CREATE', status: 'SAVED', running: false, startedAt: '2025-01-01T00:00:00Z' }] }));
        const service = createService();
        getRuns.mockReturnValue(of({ runs: [] }));
        getRunAccess.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
        service.refresh();
        expect(service.entries()).toEqual([]);
        expect(service.loadFailed()).toBe(false);
    });

    it('uses server cursors, including empty pages after permissions change', () => {
        getRuns
            .mockReturnValueOnce(of({ runs: [], nextBeforeId: 51 }))
            .mockReturnValueOnce(of({ runs: [], nextBeforeId: 1 }))
            .mockReturnValueOnce(of({ runs: [] }));
        const service = createService();
        expect(service.hasMoreHistory()).toBe(true);
        service.loadMoreHistory();
        expect(getRuns).toHaveBeenLastCalledWith(51);
        service.loadMoreHistory();
        expect(getRuns).toHaveBeenLastCalledWith(1);
        expect(service.hasMoreHistory()).toBe(false);
    });

    it('never accepts a history response from the previous login', () => {
        const pending = new Subject<AuthoringRunPage>();
        getRuns.mockReturnValueOnce(pending);
        const service = createService();
        identity.set(user('other-editor'));
        TestBed.tick();
        pending.next({ runs: [{ jobId: 'secret', exerciseId: 42, courseId: 7, kind: 'CREATE', status: 'SAVED', running: false, startedAt: '2025-01-01T00:00:00Z' }] });
        expect(service.entries()).toEqual([]);
        expect(service.hasMoreHistory()).toBe(false);
        expect(pending.observed).toBe(false);
    });

    it('reconciles each active run by its own identity, even on the same exercise', () => {
        const service = createService();
        service.track({ jobId: 'first', exerciseId: 42, courseId: 7, exerciseTitle: 'Stack', mode: 'ADAPT' });
        service.track({ jobId: 'second', exerciseId: 42, courseId: 7, exerciseTitle: 'Stack', mode: 'ADAPT' });
        getRunStatus.mockImplementation((_exercise, runId) => of(status({ jobId: runId, running: false, events: [event('DONE', 'SUCCESS')] })));
        service.refresh();
        expect(getRunStatus).toHaveBeenCalledWith(42, 'first');
        expect(getRunStatus).toHaveBeenCalledWith(42, 'second');
        expect(service.entries().every((entry) => entry.status === 'saved')).toBe(true);
    });

    it('shows durable restoration without resurrecting a prior partial outcome', () => {
        getRuns.mockReturnValue(
            of({
                runs: [
                    {
                        jobId: 'restored',
                        exerciseId: 42,
                        courseId: 7,
                        kind: 'ADAPT',
                        status: 'SAVED',
                        running: false,
                        startedAt: '2025-01-01T00:00:00Z',
                        revertedAt: '2025-01-02T00:00:00Z',
                    },
                ],
            }),
        );
        const service = createService();
        expect(service.entries()[0].status).toBe('reverted');
        expect(streams.size).toBe(0);
        service.markSeen('restored');
        service.refresh();
        expect(service.entries()[0].seen).toBe(true);
        expect(JSON.parse(localStorage.getItem(`artemis.hyperion.jobRegistry.${LOGIN}`)!)).toEqual({ version: 2, seenJobIds: ['restored'] });
    });

    it('does not discard an active run merely because it is older than a day', () => {
        const service = createService();
        const yesterday = new Date(Date.now() - 25 * 60 * 60 * 1000).toISOString();
        service.track({ jobId: 'old', exerciseId: 42, courseId: 7, exerciseTitle: 'Sorting', mode: 'GENERATE', startedAt: yesterday });

        expect(service.entries()).toHaveLength(1);
    });
});
