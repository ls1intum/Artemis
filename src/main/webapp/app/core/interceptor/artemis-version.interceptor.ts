import { ApplicationRef, Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { concat, EMPTY, interval, Observable } from 'rxjs';
import { catchError, first, tap, timeout } from 'rxjs/operators';
import { ARTEMIS_VERSION_HEADER, VERSION } from 'app/app.constants';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { SwUpdate } from '@angular/service-worker';
import { Alert, AlertService } from 'app/core/util/alert.service';

@Injectable()
export class ArtemisVersionInterceptor implements HttpInterceptor {
    // The currently displayed alert
    private alert: Alert;
    // Set to true if we ever saw an outdated indication since last reload
    // for some reason, SwUpdate.checkForUpdate returns false after a while even though we didn't actually update yet.
    // We will show alerts in any case until the next reload, so we store this here.
    // Note: We can't just display an alert one time, because it may get cleared in several occasions
    private hasSeenOutdatedInThisSession = false;

    constructor(private appRef: ApplicationRef, private updates: SwUpdate, private serverDateService: ArtemisServerDateService, private alertService: AlertService) {
        console.log('UMMMM');
        // Allow the app to stabilize first, before starting
        // polling for updates with `interval()`.
        const appIsStable = appRef.isStable.pipe(
            first((isStable) => isStable === true),
            // Sometimes, the application does not become stable apparently.
            // This is a workaround. Using the same timeout as the service worker as well.
            // TODO: Look for the cause why the app doesn't become stable
            timeout(30000),
            // Ignore error thrown by timeout
            catchError(() => EMPTY),
        );
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

            if (this.hasSeenOutdatedInThisSession || updateAvailable || overrideCheckResult) {
                console.log('Showing update alert now.');
                this.hasSeenOutdatedInThisSession = true;

                // Close previous alert to avoid duplicates
                this.alert?.close!();

                // Show fresh alert without timeout
                this.alert = this.alertService.addAlert({
                    type: 'info',
                    message: 'artemisApp.outdatedAlert',
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
