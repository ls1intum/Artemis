import { ApplicationRef, Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, first, timeout } from 'rxjs/operators';

/** How long after the app first settles we wait before warming the first chunk (~user's "10s after first navigation"). */
const PRELOAD_START_DELAY_MS = 10_000;
/** Fallback if `ApplicationRef.isStable` never emits `true` (mirrors {@link file://../interceptor/artemis-version.interceptor.ts}). */
const STABLE_TIMEOUT_MS = 30_000;
/** `requestIdleCallback` timeout so warming still makes progress on a permanently busy main thread. */
const IDLE_TIMEOUT_MS = 2_000;
/** Delay between tasks when `requestIdleCallback` is unavailable (e.g. Safari, jsdom). */
const IDLE_FALLBACK_DELAY_MS = 300;
/** Max chunks fetched/evaluated at once so background warming never starves active requests. */
const MAX_CONCURRENT = 2;
/** Reduced concurrency on a `3g` connection. */
const SLOW_CONNECTION_MAX_CONCURRENT = 1;

interface PreloadTask {
    readonly tier: number;
    readonly load: () => Observable<unknown>;
}

type NetworkInformation = { saveData?: boolean; effectiveType?: string };
type RequestIdleCallback = (cb: () => void, opts?: { timeout: number }) => number;

/**
 * Drains a tiered queue of route-chunk loaders in the background, one small batch at a time during browser
 * idle, after the app has settled. Used by {@link file://./role-aware-preloading.strategy.ts}: the strategy
 * decides *whether* a route's chunk should be warmed (role gate) and *how soon* (tier); this scheduler decides
 * *when* (idle, after a delay) and *how fast* (concurrency cap, network guard).
 *
 * A loader is run via its `load()` call, which downloads and evaluates the chunk and — because Angular's
 * `RouterPreloader` recurses inside `load()` — enqueues the route's eligible children as it goes. Each `load()`
 * settles as soon as its own chunk is fetched (children return synchronously to the preloader), so a slot is
 * never held open waiting for a whole subtree.
 */
@Injectable({ providedIn: 'root' })
export class IdlePreloadScheduler {
    private readonly appRef = inject(ApplicationRef);

    private readonly queue: PreloadTask[] = [];
    private active = 0;
    private ready = false;
    private armed = false;

    /**
     * Queue a route-chunk loader for background warming. No-op (the chunk stays on-demand) when the connection
     * asks us to save data. The first call arms the start gate; draining begins once the app is stable and the
     * start delay has elapsed.
     */
    enqueue(load: () => Observable<unknown>, tier: number): void {
        if (this.isWarmingDisabled()) {
            return;
        }
        this.queue.push({ load, tier });
        this.arm();
        this.pump();
    }

    /** Arms the one-shot start gate: wait for app stability (with a fallback) plus the start delay, then drain. */
    private arm(): void {
        if (this.armed) {
            return;
        }
        this.armed = true;
        this.appRef.isStable
            .pipe(
                first((isStable) => isStable === true),
                timeout(STABLE_TIMEOUT_MS),
                catchError(() => of(true)),
            )
            .subscribe(() => {
                setTimeout(() => {
                    this.ready = true;
                    this.pump();
                }, PRELOAD_START_DELAY_MS);
            });
    }

    /** Fills free concurrency slots with the highest-priority (lowest-tier) queued tasks, each on the next idle tick. */
    private pump(): void {
        if (!this.ready) {
            return;
        }
        const maxConcurrent = this.maxConcurrent();
        while (this.active < maxConcurrent) {
            const task = this.dequeue();
            if (!task) {
                return;
            }
            this.active++;
            this.runWhenIdle(task);
        }
    }

    /** Removes and returns the lowest-tier task (FIFO within a tier). */
    private dequeue(): PreloadTask | undefined {
        if (this.queue.length === 0) {
            return undefined;
        }
        let bestIndex = 0;
        for (let i = 1; i < this.queue.length; i++) {
            if (this.queue[i].tier < this.queue[bestIndex].tier) {
                bestIndex = i;
            }
        }
        return this.queue.splice(bestIndex, 1)[0];
    }

    private runWhenIdle(task: PreloadTask): void {
        this.scheduleIdle(() => {
            let settled = false;
            const done = () => {
                if (settled) {
                    return;
                }
                settled = true;
                this.active--;
                // A finished load() has, via Angular's preloader recursion, enqueued this route's eligible
                // children — pump() picks them up next.
                this.pump();
            };
            try {
                // Failures are non-critical: a chunk that fails to warm is simply fetched on demand later.
                task.load().subscribe({ error: done, complete: done });
            } catch {
                done();
            }
        });
    }

    private scheduleIdle(cb: () => void): void {
        const ric = (globalThis as { requestIdleCallback?: RequestIdleCallback }).requestIdleCallback;
        if (typeof ric === 'function') {
            ric(() => cb(), { timeout: IDLE_TIMEOUT_MS });
            return;
        }
        setTimeout(cb, IDLE_FALLBACK_DELAY_MS);
    }

    private maxConcurrent(): number {
        return this.connection()?.effectiveType === '3g' ? SLOW_CONNECTION_MAX_CONCURRENT : MAX_CONCURRENT;
    }

    /** True when the user has opted into data saving or is on a very slow connection — keep routes on-demand. */
    private isWarmingDisabled(): boolean {
        const connection = this.connection();
        if (!connection) {
            return false;
        }
        return connection.saveData === true || connection.effectiveType === '2g' || connection.effectiveType === 'slow-2g';
    }

    private connection(): NetworkInformation | undefined {
        return typeof navigator === 'undefined' ? undefined : (navigator as { connection?: NetworkInformation }).connection;
    }
}
