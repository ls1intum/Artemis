import { Component, OnDestroy, OnInit } from '@angular/core';
import { JhiAlert } from 'ng-jhipster';
import { AlertService } from 'app/core/alert/alert.service';

@Component({
    selector: 'jhi-alert',
    template: `
        <div class="alerts" role="alert">
            <div *ngFor="let alert of alerts" [ngClass]="{ 'alert.position': true, toast: alert.toast }">
                <ngb-alert *ngIf="alert && alert.type && alert.msg" [type]="alert.type" (close)="alert.close(alerts)">
                    <pre [innerHTML]="alert.msg"></pre>
                </ngb-alert>
            </div>
        </div>
    `,
})
export class JhiAlertComponent implements OnInit, OnDestroy {
    alerts: JhiAlert[];

    constructor(private alertService: AlertService) {}

    ngOnInit() {
        this.alerts = this.alertService.get();
    }

    setClasses(alert: JhiAlert) {
        return {
            'jhi-toast': alert.toast,
            [alert.position!]: true,
        };
    }

    ngOnDestroy() {
        this.alerts = [];
    }
}
