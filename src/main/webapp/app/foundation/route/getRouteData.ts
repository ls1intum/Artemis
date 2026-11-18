import { ActivatedRoute, Data } from '@angular/router';

/**
 * Returns the first matching route data entry found while traversing from the current route up to the root.
 */
export function getRouteData<T>(route: ActivatedRoute, dataKey: string): T | undefined {
    for (const currentRoute of [...route.snapshot.pathFromRoot].reverse()) {
        if (hasDataKey(currentRoute.data, dataKey)) {
            return currentRoute.data[dataKey] as T | undefined;
        }
    }
    return undefined;
}

function hasDataKey(data: Data, dataKey: string): boolean {
    return Object.prototype.hasOwnProperty.call(data, dataKey);
}
