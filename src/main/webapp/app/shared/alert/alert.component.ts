import { Component, OnDestroy, OnInit } from '@angular/core';
import { JhiAlert } from 'ng-jhipster';
import { JhiAlertService } from 'ng-jhipster';

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
        this.alerts.forEach((alert) => {
            // This is a workaround to avoid translation not found issues.
            if (alert?.msg?.startsWith('translation-not-found')) {
                // In case a translation key is not found, remove the 'translation-not-found[...]' annotation
                const alertMessageMatch = alert.msg.match(/translation-not-found\[(.*?)\]$/);
                if (alertMessageMatch && alertMessageMatch.length > 1) {
                    alert.msg = alertMessageMatch[1];
                } else {
                    // Fallback, in case the bracket is missing
                    alert.msg = alert.msg.replace('translation-not-found', '');
                }
                // TODO: sent a sentry warning with the translation key
            }
        });
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
