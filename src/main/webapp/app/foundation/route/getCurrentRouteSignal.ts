import { Signal } from '@angular/core';
import { Router } from '@angular/router';

import { getSignalBasedOnRoute } from './getSignalBasedOnRoute';

/**
 * Returns a signal containing the current router URL.
 */
export function getCurrentRouteSignal(router: Router): Signal<string> {
    return getSignalBasedOnRoute(router, (url) => url);
}
