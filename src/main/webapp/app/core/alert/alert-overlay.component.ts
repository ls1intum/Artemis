import { Component, OnDestroy, inject } from '@angular/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgClass, NgStyle } from '@angular/common';
import { CloseCircleComponent } from 'app/shared-ui/close-circle/close-circle.component';

@Component({
    selector: 'jhi-alert-overlay',
    templateUrl: './alert-overlay.component.html',
    styleUrls: ['./alert-overlay.component.scss'],
    imports: [FaIconComponent, TranslateDirective, NgClass, CloseCircleComponent, NgStyle],
})
export class AlertOverlayComponent implements OnDestroy {
    alertService = inject(AlertService);

    // Reactive view of the currently shown alerts. Binding to the signal lets the overlay update
    // under zoneless change detection when alerts are added or (auto-)dismissed.
    alerts = this.alertService.alerts;

    times = faTimes;

    /**
     * call closeAll() for alertService on destroy
     */
    ngOnDestroy(): void {
        this.alertService.closeAll();
    }
}
