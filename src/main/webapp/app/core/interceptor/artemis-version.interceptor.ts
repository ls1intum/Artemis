import { ApplicationRef, Inject, Injectable, InjectionToken } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, concat, interval, of } from 'rxjs';
import { catchError, first, tap, timeout } from 'rxjs/operators';
import { ARTEMIS_VERSION_HEADER, VERSION } from 'app/app.constants';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { SwUpdate } from '@angular/service-worker';
import { Alert, AlertService, AlertType } from 'app/core/util/alert.service';

export const WINDOW_INJECTOR_TOKEN = new InjectionToken<Window>('Window');

@Injectable()
export class ArtemisVersionInterceptor implements HttpInterceptor {
    // The currently displayed alert
    private alert: Alert;
    // Indicates whether we ever saw an outdated state since last reload
    private hasSeenOutdatedInThisSession = false;

    constructor(
        private appRef: ApplicationRef,
        private updates: SwUpdate,
        private serverDateService: ArtemisServerDateService,
        private alertService: AlertService,
        @Inject(WINDOW_INJECTOR_TOKEN) private injectedWindow: Window,
    ) {
        // Allow the app to stabilize first, before starting
        // polling for updates with `interval()`.
        const appIsStableOrTimeout = appRef.isStable.pipe(
            first((isStable) => isStable === true),
            // Sometimes, the application does not become stable apparently.
            // This is a workaround. Using the same timeout as the service worker as well.
            // TODO: Look for the cause why the app doesn't become stable
            timeout(30000),
            // Ignore error thrown by timeout
            catchError(() => of(true)),
        );
        const updateInterval = interval(60 * 1000); // every 60s
        const updateIntervalOnceAppIsStable$ = concat(appIsStableOrTimeout, updateInterval);

        updateIntervalOnceAppIsStable$.subscribe(() => this.checkForUpdates(false));
    }

    intercept(request: HttpRequest<any>, nextHandler: HttpHandler): Observable<HttpEvent<any>> {
        return nextHandler.handle(request).pipe(
            tap((response) => {
                if (response instanceof HttpResponse) {
                    const isTranslationStringsRequest = response.url?.includes('/i18n/');
                    const serverVersion = response.headers.get(ARTEMIS_VERSION_HEADER);
                    if (VERSION && serverVersion && VERSION !== serverVersion && !isTranslationStringsRequest) {
                        // Version mismatch detected from HTTP headers. Let SW look for updates!
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

    /**
     * Tells the service worker to check for updates and display an update alert if an update is available.
     * This is either exactly if
     * - the service worker detects an update right now, or
     * - the first condition was ever true since the app loaded (aka last reload)
     *
     * We need to have this second option because the "checkForUpdate()" call sometimes starts to return false after a while, even though we didn't reload / update yet.
     * And if service workers are not available we can't actually check for updates, so we have to rely on ever having seen a different version number in a request
     *
     * @param hasUpdate if it is known that there is an update, only relevant if service workers are not available
     *
     * @private
     */
    private checkForUpdates(hasUpdate: boolean) {
        // don't spam errors when service workers are not available, instead rely on the Content-Version header of responses
        const update = this.updates.isEnabled ? this.updates.checkForUpdate() : Promise.resolve(hasUpdate);

        // first update the service worker
        update.then((updateAvailable: boolean) => {
            if (this.hasSeenOutdatedInThisSession || updateAvailable) {
                this.hasSeenOutdatedInThisSession = true;

                // If we haven't shown an alert yet or the alert has been closed: Spawn new alert
                if (!this.alert?.isOpen) {
                    this.alert = this.alertService.addAlert({
                        type: AlertType.INFO,
                        message: 'artemisApp.outdatedAlert',
                        timeout: 0,
                        action: {
                            label: 'artemisApp.outdatedAction',
                            callback: () =>
                                // Apply the update
                                this.updates
                                    .activateUpdate()
                                    // Ignore any error. Any error happening here doesn't matter
                                    // If we reach this point, we want to load an update
                                    // so in any case, we should reload
                                    .catch(() => {})
                                    // Reload the page with the new version
                                    .then(() => this.injectedWindow.location.reload()),
                        },
                    });
                }
            }
        });
    }
}
