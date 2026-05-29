import { ActivatedRoute } from '@angular/router';
import { Signal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';

/**
 * Returns a signal for the first matching numeric path parameter found while traversing from the current route up to the root.
 */
export function getNumericPathVariableSignal(route: ActivatedRoute, variableName: string): Signal<number | undefined> {
    let currentRoute: ActivatedRoute | null = route;

    while (currentRoute) {
        if (currentRoute.snapshot.paramMap.get(variableName) !== null) {
            return toSignal(
                currentRoute.paramMap.pipe(
                    map((paramMap) => {
                        const numberAsString = paramMap.get(variableName);
                        if (numberAsString === null) {
                            return undefined;
                        }
                        const result = Number(numberAsString);
                        return Number.isFinite(result) ? result : undefined;
                    }),
                ),
                { initialValue: undefined },
            );
        }
        currentRoute = currentRoute.parent;
    }

    return signal(undefined);
}
