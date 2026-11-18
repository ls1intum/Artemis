import { Provider } from '@angular/core';
import { ActivatedRouteSnapshot } from '@angular/router';

export function mockedActivatedRouteSnapshot(snapshotPath: any): Provider {
    return {
        provide: ActivatedRouteSnapshot,
        useValue: {
            routeConfig: { path: snapshotPath },
        },
    };
}
