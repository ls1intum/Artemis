import { ApplicationRef, Injectable } from '@angular/core';
import { SwUpdate } from '@angular/service-worker';
import { concat, interval, Subject } from 'rxjs';
import { first, throttleTime } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';

@Injectable()
export class CheckForUpdateService {
    private showAlert = new Subject<void>();

    constructor(private appRef: ApplicationRef, private updates: SwUpdate, private alertService: AlertService) {
        // Allow the app to stabilize first, before starting
        // polling for updates with `interval()`.
        const appIsStable = appRef.isStable.pipe(first((isStable) => isStable === true));
        const updateInterval = interval(60 * 1000); // every 60s
        const updateIntervalOnceAppIsStable$ = concat(appIsStable, updateInterval);

        updateIntervalOnceAppIsStable$.subscribe(() => this.checkForUpdates());

        // TODO: remove these logs after successful implementation
        updates.available.subscribe((event) => {
            console.log('current version is', event.current);
            console.log('available version is', event.available);
        });
        updates.activated.subscribe((event) => {
            console.log('old version was', event.previous);
            console.log('new version is', event.current);
        });

        this.showAlert.pipe(throttleTime(30000)).subscribe(() => {
            // show the outdated alert for 30s so users update by reloading the browser
            // also see https://angular.io/guide/service-worker-communications#forcing-update-activation
            this.alertService.addAlert({
                type: 'info',
                message: 'artemisApp.outdatedAlert',
                timeout: 30000,
                action: {
                    label: 'artemisApp.outdatedAction',
                    callback: () => updates.activateUpdate().then(() => document.location.reload()),
                },
            });
        });
    }

    public checkForUpdates() {
        // first update the service worker
        this.updates.checkForUpdate().then((updateAvailable: boolean) => {
            if (updateAvailable) {
                this.showAlert.next();
            }
        });
    }
}
