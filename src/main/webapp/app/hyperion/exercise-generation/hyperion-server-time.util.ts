import { Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Observable, interval, map, takeUntil } from 'rxjs';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';

/** Server-adjusted time, ticking until this injection context is destroyed or the observed job finishes. */
export function serverTimeSignal(stopWhen?: Observable<unknown>): Signal<number> {
    const serverDateService = inject(ArtemisServerDateService);
    const currentTime = () => serverDateService.now().valueOf();
    const ticks = interval(1000).pipe(map(() => currentTime()));
    return toSignal(stopWhen ? ticks.pipe(takeUntil(stopWhen)) : ticks, { initialValue: currentTime() });
}

/** Elapsed whole seconds, never negative when a timestamp is slightly ahead of the server clock. */
export function elapsedSecondsSince(timestamp: string | undefined, now: number): number {
    return timestamp ? Math.max(0, Math.floor((now - Date.parse(timestamp)) / 1000)) : 0;
}
