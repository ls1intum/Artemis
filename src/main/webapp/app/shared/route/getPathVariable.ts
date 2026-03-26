import { ActivatedRoute } from '@angular/router';
import { Signal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';

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
