import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Alert, AlertService } from 'app/foundation/service/alert.service';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgClass, NgStyle } from '@angular/common';
import { CloseCircleComponent } from 'app/ui/close-circle/close-circle.component';

@Component({
    selector: 'jhi-alert-overlay',
    templateUrl: './alert-overlay.component.html',
    styleUrls: ['./alert-overlay.component.scss'],
    imports: [FaIconComponent, TranslateDirective, NgClass, CloseCircleComponent, NgStyle],
})
export class AlertOverlayComponent implements OnInit, OnDestroy {
    alertService = inject(AlertService);

    alerts: Alert[] = [];

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
