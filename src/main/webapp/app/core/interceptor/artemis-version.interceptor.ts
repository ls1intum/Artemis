import { ApplicationRef, Injectable, InjectionToken, inject } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, concat, interval, of } from 'rxjs';
import { catchError, first, tap, timeout } from 'rxjs/operators';
import { ARTEMIS_VERSION_HEADER, VERSION } from 'app/app.constants';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { SwUpdate } from '@angular/service-worker';
import { Alert, AlertService, AlertType } from 'app/foundation/service/alert.service';

export const WINDOW_INJECTOR_TOKEN = new InjectionToken<Window>('Window');

@Injectable()
export class ArtemisVersionInterceptor implements HttpInterceptor {
    private appRef = inject(ApplicationRef);
    private updates = inject(SwUpdate);
    private serverDateService = inject(ArtemisServerDateService);
    private alertService = inject(AlertService);
    private injectedWindow = inject<Window>(WINDOW_INJECTOR_TOKEN);

    // The currently displayed alert
    private alert?: Alert;
    // Indicates whether we ever saw an outdated state since last reload
    private hasSeenOutdatedInThisSession = false;

    constructor() {
        const appRef = this.appRef;

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

        // The service worker cannot serve the version this client was loaded from anymore, so only a reload helps
        this.updates.unrecoverable.subscribe(() => this.checkForUpdates(true));
    }

    intercept(request: HttpRequest<unknown>, nextHandler: HttpHandler): Observable<HttpEvent<unknown>> {
        return nextHandler.handle(request).pipe(
            tap((response) => {
                if (response instanceof HttpResponse) {
                    const isTranslationStringsRequest = response.url?.includes('/i18n/');
                    const serverVersion = response.headers.get(ARTEMIS_VERSION_HEADER);
                    if (VERSION && serverVersion && VERSION !== serverVersion && !isTranslationStringsRequest) {
                        // Version mismatch detected from HTTP headers. Let SW look for updates!
                        // A newer server version means this client is outdated, even if the service worker does not report an update (e.g. because
                        // another tab already activated it). An older server version can only be a node that has not been updated yet.
                        this.checkForUpdates(isNewerVersion(serverVersion, VERSION));
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
     * @param hasUpdate if it is known that there is an update, independent of what the service worker reports
     */
    private checkForUpdates(hasUpdate: boolean) {
        // don't spam errors when service workers are not available, instead rely on the Content-Version header of responses
        // a failing check (e.g. a broken service worker) must not suppress an update that is known from the header
        const update = this.updates.isEnabled ? this.updates.checkForUpdate().catch(() => false) : Promise.resolve(false);

        // first update the service worker
        void update.then((updateAvailable: boolean) => {
            if (this.hasSeenOutdatedInThisSession || updateAvailable || hasUpdate) {
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

/**
 * Checks whether the given version is newer than the current one, comparing the numeric major, minor and patch parts.
 * A version that cannot be parsed counts as newer, so that an unexpected format does not hide an update.
 *
 * @param version the version to check, e.g. the version the server reports
 * @param currentVersion the version of this client
 */
function isNewerVersion(version: string, currentVersion: string): boolean {
    const parse = (value: string) =>
        value
            .split('-')[0]
            .split('.')
            .map((part) => Number.parseInt(part, 10));
    const parts = parse(version);
    const currentParts = parse(currentVersion);
    if ([...parts, ...currentParts].some((part) => Number.isNaN(part))) {
        return true;
    }
    for (let i = 0; i < Math.max(parts.length, currentParts.length); i++) {
        const difference = (parts[i] ?? 0) - (currentParts[i] ?? 0);
        if (difference !== 0) {
            return difference > 0;
        }
    }
    return false;
}
