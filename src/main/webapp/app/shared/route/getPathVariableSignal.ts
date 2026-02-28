import { ActivatedRoute } from '@angular/router';
import { Signal, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs/operators';

export function getNumericPathVariableSignal(route: ActivatedRoute, variableName: string, ancestorRank: number = 0): Signal<number | undefined> {
    if (!Number.isInteger(ancestorRank) || ancestorRank < 0) {
        return signal(undefined);
    }

    let currentRoute: ActivatedRoute | null = route;
    for (let i = 0; i < ancestorRank; i++) {
        currentRoute = currentRoute.parent;
        if (!currentRoute) {
            return signal(undefined);
        }
    }

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
