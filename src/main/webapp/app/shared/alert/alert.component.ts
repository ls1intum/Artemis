import { Component, OnDestroy, OnInit } from '@angular/core';
import { JhiAlert } from 'ng-jhipster';
import { AlertService } from 'app/core/alert/alert.service';

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

    constructor(private alertService: AlertService) {}

    ngOnInit(): void {
        this.alerts = this.alertService.get();
    }

    setClasses(alert: JhiAlert): { [key: string]: boolean } {
        const classes = { 'jhi-toast': Boolean(alert.toast) };
        if (alert.position) {
            return { ...classes, [alert.position]: true };
        }
        return classes;
    }

    ngOnDestroy(): void {
        this.alertService.clear();
    }
}
