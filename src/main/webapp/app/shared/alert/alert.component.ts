import { Component, OnDestroy, OnInit } from '@angular/core';
import { Alert, AlertService } from 'app/core/util/alert.service';
import { checkForMissingTranslationKey } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-alert',
    templateUrl: './alert.component.html',
})
export class AlertComponent implements OnInit, OnDestroy {
    alerts: Alert[] = [];

    constructor(private alertService: AlertService) {}

    /**
     * get alerts on init
     */
    ngOnInit(): void {
        this.alerts = this.alertService.get();
    }

    /**
     * set classes for alert
     * @param {Alert} alert
     * @return {{ [key: string]: boolean }}
     */
    setClasses(alert: Alert): { [key: string]: boolean } {
        const classes = { 'jhi-toast': Boolean(alert.toast) };
        if (alert.position) {
            return { ...classes, [alert.position]: true };
        }
        return classes;
    }

    /**
     * call clear() for alertService on destroy
     */
    ngOnDestroy(): void {
        this.alertService.clear();
    }

    close(alert: Alert): void {
        alert.close?.(this.alerts);
    }

    /**
     * The received alert may contain a message which could not be translated.
     * We slice the wrapping 'translation-not-found[..]' and return the response.
     * @param alert which contains the alert message
     */
    getAlertMessage(alert: Alert) {
        checkForMissingTranslationKey(alert);
        return alert.message;
    }
}
