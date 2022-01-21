import { Component, OnDestroy, OnInit } from '@angular/core';
import { Alert, AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-alert-overlay',
    templateUrl: './alert-overlay.component.html',
    styleUrls: ['./alert-overlay.component.scss'],
})
export class AlertOverlayComponent implements OnInit, OnDestroy {
    alerts: Alert[] = [];

    constructor(private alertService: AlertService) {}

    /**
     * get alerts on init
     */
    ngOnInit(): void {
        this.alerts = this.alertService.get();
    }

    /**
     * call clear() for alertService on destroy
     */
    ngOnDestroy(): void {
        this.alertService.clear();
    }

    close(alert: Alert): void {
        alert.close!();
    }
}
