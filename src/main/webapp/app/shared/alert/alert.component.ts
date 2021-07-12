import { Component, OnDestroy, OnInit } from '@angular/core';
import { JhiAlert } from 'ng-jhipster';
import { JhiAlertService } from 'ng-jhipster';
import { checkForMissingTranslationKey } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-alert',
    template: `
        <div class="alerts mt-1" role="alert">
            <div *ngFor="let alert of alerts" [ngClass]="setClasses(alert)">
                <ngb-alert *ngIf="alert && alert.type && alert.msg && alert.close" [type]="alert.type" (close)="alert.close(alerts)">
                    <pre [innerHTML]="getAlertMessage(alert)"></pre>
                </ngb-alert>
            </div>
        </div>
    `,
})
export class AlertComponent implements OnInit, OnDestroy {
    alerts: JhiAlert[] = [];

    constructor(private alertService: JhiAlertService) {}

    /**
     * get alerts on init
     */
    ngOnInit(): void {
        this.alerts = this.alertService.get();
    }

    /**
     * set classes for alert
     * @param {JhiAlert} alert
     * @return {{ [key: string]: boolean }}
     */
    setClasses(alert: JhiAlert): { [key: string]: boolean } {
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

    /**
     * The recveived alert may contain a message which could not be translated.
     * We slice the wrapping 'translation-not-found[..]' and return the response.
     * @param alert which contains the alert message
     */
    getAlertMessage(alert: JhiAlert): String {
        checkForMissingTranslationKey(alert);
        return alert.msg;
    }
}
