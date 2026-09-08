import { Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Observable, interval, map, takeUntil } from 'rxjs';
import { GenerationMode } from 'app/localci/shared/entities/generation-sandbox-job.model';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';

const CLOCK_INTERVAL_MS = 1000;

/** The translation key for a generation job's mode label. */
export function generationModeLabelKey(mode: GenerationMode): string {
    return `artemisApp.buildAgents.generationSandboxes.${mode === 'ADAPT' ? 'adapt' : 'generate'}`;
}

/** The whole seconds that passed between an ISO timestamp and the given epoch millis, clamped at zero so a clock skew never shows a negative duration. */
export function elapsedSecondsSince(timestamp: string, nowMillis: number): number {
    return Math.max(0, Math.floor((nowMillis - Date.parse(timestamp)) / 1000));
}

/**
 * A signal of the server-adjusted current time in epoch millis, ticking once per second so live durations keep counting.
 *
 * Must be called from an injection context; the ticker stops when that context is destroyed, or earlier once `stopWhen` emits.
 *
 * @param stopWhen Emits when the observed work reached a terminal state and the duration should freeze.
 */
export function serverTimeSignal(stopWhen?: Observable<unknown>): Signal<number> {
    const serverDateService = inject(ArtemisServerDateService);
    const currentTime = () => serverDateService.now().valueOf();
    const ticks = interval(CLOCK_INTERVAL_MS).pipe(map(() => currentTime()));
    return toSignal(stopWhen ? ticks.pipe(takeUntil(stopWhen)) : ticks, { initialValue: currentTime() });
}
