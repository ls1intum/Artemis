import { Component, OnDestroy, OnInit } from '@angular/core';
import { JhiAlert } from 'ng-jhipster';
import { JhiAlertService } from 'ng-jhipster';
import { checkForMissingTranslationKey } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-alert',
    template: `
        <div class="alerts" role="alert">
            <div *ngFor="let alert of alerts" [ngClass]="setClasses(alert)">
                <ngb-alert *ngIf="alert && alert.type && alert.msg" [type]="alert.type" (close)="alert.close(alerts)">
                    <pre [innerHTML]="alert.msg"></pre>
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
        // This is a workaround to avoid translation not found issues.
        if (this.alerts && Array.isArray(this.alerts)) {
            this.alerts.forEach((alert) => {
                checkForMissingTranslationKey(alert);
            });
        }
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
}
