import { Component, OnDestroy, OnInit } from '@angular/core';
import { Alert, AlertService } from 'app/core/util/alert.service';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { animate, group, style, transition, trigger } from '@angular/animations';

@Component({
    selector: 'jhi-alert-overlay',
    templateUrl: './alert-overlay.component.html',
    styleUrls: ['./alert-overlay.component.scss'],
    animations: [
        trigger('alertAnimation', [
            transition(':enter', [
                style({
                    height: '0',
                    marginBottom: '0',
                    transform: 'translateX(120%)',
                    zIndex: 500,
                }),
                group([
                    animate(
                        '0.3s ease-in-out',
                        style({
                            height: '*',
                            marginBottom: '15px',
                        }),
                    ),
                    animate(
                        '0.3s 0.1s cubic-bezier(.2,1.22,.64,1)',
                        style({
                            transform: 'translateX(0)',
                        }),
                    ),
                ]),
            ]),
            transition(':leave', [
                animate(
                    '0.2s ease-in',
                    style({
                        height: '0',
                        marginBottom: '0',
                        transform: 'translateX(120%)',
                    }),
                ),
            ]),
        ]),
        trigger('dismissAllAnimation', [
            transition(':enter', [
                style({
                    opacity: '0',
                }),
                animate(
                    '0.2s ease-in-out',
                    style({
                        opacity: '1',
                    }),
                ),
            ]),
            transition(':leave', [
                animate(
                    '0.2s ease-out',
                    style({
                        opacity: '0',
                    }),
                ),
            ]),
        ]),
    ],
})
export class AlertOverlayComponent implements OnInit, OnDestroy {
    alerts: Alert[] = [];

    constructor(public alertService: AlertService) {}

    times = faTimes;

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
        this.alertService.closeAll();
    }
}
