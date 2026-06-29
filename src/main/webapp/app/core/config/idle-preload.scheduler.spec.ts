import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ApplicationRef } from '@angular/core';
import { Subject, of, throwError } from 'rxjs';
import { IdlePreloadScheduler } from 'app/core/config/idle-preload.scheduler';

const START_DELAY_MS = 10_000;
const IDLE_FALLBACK_MS = 300;

describe('IdlePreloadScheduler', () => {
    setupTestBed({ zoneless: true });

    let scheduler: IdlePreloadScheduler;

    function configure(): void {
        TestBed.configureTestingModule({
            providers: [IdlePreloadScheduler, { provide: ApplicationRef, useValue: { isStable: of(true) } }],
        });
        scheduler = TestBed.inject(IdlePreloadScheduler);
    }

    /** Advance past the stability-gated start delay and the first idle batch. */
    function reachFirstIdleBatch(): void {
        vi.advanceTimersByTime(START_DELAY_MS);
        vi.advanceTimersByTime(IDLE_FALLBACK_MS);
    }

    function startedCount(loads: ReadonlyArray<{ mock: { calls: unknown[] } }>): number {
        return loads.filter((l) => l.mock.calls.length > 0).length;
    }

    beforeEach(() => {
        vi.useFakeTimers();
        // Force the setTimeout fallback path so virtual timers fully control draining.
        vi.stubGlobal('requestIdleCallback', undefined);
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllGlobals();
        delete (navigator as { connection?: unknown }).connection;
        vi.restoreAllMocks();
    });

    it('warms nothing until the app settles and the start delay elapses', () => {
        configure();
        const load = vi.fn(() => of(undefined));
        scheduler.enqueue(load, 1);

        expect(load).not.toHaveBeenCalled();
        vi.advanceTimersByTime(START_DELAY_MS - 1);
        expect(load).not.toHaveBeenCalled();

        vi.advanceTimersByTime(1 + IDLE_FALLBACK_MS);
        expect(load).toHaveBeenCalledTimes(1);
    });

    it('drains lower tiers before higher tiers', () => {
        configure();
        const order: number[] = [];
        const make = (tier: number) => () => {
            order.push(tier);
            return of(undefined);
        };
        scheduler.enqueue(make(3), 3);
        scheduler.enqueue(make(1), 1);

        reachFirstIdleBatch();

        expect(order).toEqual([1, 3]);
    });

    it('respects the concurrency cap and resumes when a slot frees', () => {
        configure();
        const subjects = [new Subject<void>(), new Subject<void>(), new Subject<void>()];
        const loads = subjects.map((s) => vi.fn(() => s.asObservable()));
        loads.forEach((load) => scheduler.enqueue(load, 1));

        reachFirstIdleBatch();
        expect(startedCount(loads)).toBe(2);

        // Completing one in-flight load frees a slot; the next idle tick picks up the third task.
        subjects[0].complete();
        vi.advanceTimersByTime(IDLE_FALLBACK_MS);
        expect(startedCount(loads)).toBe(3);
    });

    it('does not warm anything when the connection asks to save data', () => {
        Object.defineProperty(navigator, 'connection', { value: { saveData: true }, configurable: true });
        configure();
        const load = vi.fn(() => of(undefined));

        scheduler.enqueue(load, 1);
        reachFirstIdleBatch();

        expect(load).not.toHaveBeenCalled();
    });

    it('does not warm anything on a 2g connection', () => {
        Object.defineProperty(navigator, 'connection', { value: { effectiveType: '2g' }, configurable: true });
        configure();
        const load = vi.fn(() => of(undefined));

        scheduler.enqueue(load, 1);
        reachFirstIdleBatch();

        expect(load).not.toHaveBeenCalled();
    });

    it('halves the concurrency cap on a 3g connection', () => {
        Object.defineProperty(navigator, 'connection', { value: { effectiveType: '3g' }, configurable: true });
        configure();
        const loads = [new Subject<void>(), new Subject<void>()].map((s) => vi.fn(() => s.asObservable()));
        loads.forEach((load) => scheduler.enqueue(load, 1));

        reachFirstIdleBatch();

        expect(startedCount(loads)).toBe(1);
    });

    it('drains via requestIdleCallback when it is available', () => {
        // Real browsers take the requestIdleCallback path (the spec otherwise forces the setTimeout fallback).
        vi.stubGlobal('requestIdleCallback', (cb: () => void) => {
            cb();
            return 1;
        });
        configure();
        const load = vi.fn(() => of(undefined));
        scheduler.enqueue(load, 1);

        vi.advanceTimersByTime(START_DELAY_MS);

        expect(load).toHaveBeenCalledTimes(1);
    });

    it('frees the slot and continues when a load fails', () => {
        configure();
        const failing = vi.fn(() => throwError(() => new Error('chunk failed to load')));
        const blocking = vi.fn(() => new Subject<void>().asObservable());
        const next = vi.fn(() => of(undefined));
        // Cap is 2: `failing` and `blocking` start first; `failing` errors and must free its slot for `next`.
        scheduler.enqueue(failing, 1);
        scheduler.enqueue(blocking, 1);
        scheduler.enqueue(next, 1);

        reachFirstIdleBatch();
        vi.advanceTimersByTime(IDLE_FALLBACK_MS);

        expect(failing).toHaveBeenCalledTimes(1);
        expect(next).toHaveBeenCalledTimes(1);
    });

    it('waits for ApplicationRef.isStable before draining, and falls back after the timeout', () => {
        const stable = new Subject<boolean>();
        TestBed.configureTestingModule({ providers: [IdlePreloadScheduler, { provide: ApplicationRef, useValue: { isStable: stable.asObservable() } }] });
        const sched = TestBed.inject(IdlePreloadScheduler);
        const load = vi.fn(() => of(undefined));
        sched.enqueue(load, 1);

        // Delay elapses but the app has not reported stable yet → nothing warms.
        vi.advanceTimersByTime(START_DELAY_MS + IDLE_FALLBACK_MS);
        expect(load).not.toHaveBeenCalled();

        stable.next(true);
        vi.advanceTimersByTime(START_DELAY_MS + IDLE_FALLBACK_MS);
        expect(load).toHaveBeenCalledTimes(1);
    });

    it('still warms if the app never becomes stable (isStable timeout fallback)', () => {
        const neverStable = new Subject<boolean>();
        TestBed.configureTestingModule({ providers: [IdlePreloadScheduler, { provide: ApplicationRef, useValue: { isStable: neverStable.asObservable() } }] });
        const sched = TestBed.inject(IdlePreloadScheduler);
        const load = vi.fn(() => of(undefined));
        sched.enqueue(load, 1);

        // 30s stability timeout → catchError → proceed, then the start delay + first idle tick.
        vi.advanceTimersByTime(30_000 + START_DELAY_MS + IDLE_FALLBACK_MS);

        expect(load).toHaveBeenCalledTimes(1);
    });
});
