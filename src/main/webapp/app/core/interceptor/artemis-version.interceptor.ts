import { ApplicationRef, Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { concat, interval, Observable } from 'rxjs';
import { first, tap } from 'rxjs/operators';
import { ARTEMIS_VERSION_HEADER, VERSION } from 'app/app.constants';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { SwUpdate } from '@angular/service-worker';
import { Alert, AlertService } from 'app/core/util/alert.service';

@Injectable()
export class ArtemisVersionInterceptor implements HttpInterceptor {
    private alert: Alert;

    constructor(private appRef: ApplicationRef, private updates: SwUpdate, private serverDateService: ArtemisServerDateService, private alertService: AlertService) {
        // Allow the app to stabilize first, before starting
        // polling for updates with `interval()`.
        const appIsStable = appRef.isStable.pipe(first((isStable) => isStable === true));
        appIsStable.subscribe(() => console.log('Application became stable'));
        const updateInterval = interval(10 * 1000); // every 60s
        const updateIntervalOnceAppIsStable$ = concat(appIsStable, updateInterval);

        updateIntervalOnceAppIsStable$.subscribe(() => {
            console.log('Interval triggered.');
            this.checkForUpdates(false);
        });

        updates.available.subscribe((event) => {
            console.log('current version is', event.current);
            console.log('available version is', event.available);
        });
        updates.activated.subscribe((event) => {
            console.log('old version was', event.previous);
            console.log('new version is', event.current);
        });
    }

    intercept(request: HttpRequest<any>, nextHandler: HttpHandler): Observable<HttpEvent<any>> {
        return nextHandler.handle(request).pipe(
            tap((response) => {
                if (response instanceof HttpResponse) {
                    const isTranslationStringsRequest = response.url?.includes('/i18n/');
                    const serverVersion = response.headers.get(ARTEMIS_VERSION_HEADER);
                    if (VERSION && serverVersion && VERSION !== serverVersion && !isTranslationStringsRequest) {
                        console.log('Version not equal!');
                        this.checkForUpdates(true);
                    }
                    // only invoke the time call if the call was not already the time call to prevent recursion here
                    if (!request.url.includes('time')) {
                        this.serverDateService.updateTime();
                    }
                }
            }),
        );
    }

    private checkForUpdates(overrideCheckResult: boolean) {
        // first update the service worker
        console.log('Checking for updates now!');
        this.updates.checkForUpdate().then((updateAvailable: boolean) => {
            console.log('Check complete. Result: ' + updateAvailable);

            if (updateAvailable || overrideCheckResult) {
                console.log('Showing update alert now.');

                this.alert?.close!();

                this.alert = this.alertService.addAlert({
                    type: 'info',
                    message: 'artemisApp.outdatedAlert',
                    timeout: 30000,
                    action: {
                        label: 'artemisApp.outdatedAction',
                        callback: () => this.updates.activateUpdate().then(() => document.location.reload()),
                    },
                });
                console.log('Returned');
            }
        });
    }
}
