import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { TumAetUiProgressSpinnerComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LoadingNotificationService } from 'app/core/loading-notification/loading-notification.service';

@Component({
    selector: 'jhi-loading-notification',
    imports: [TumAetUiProgressSpinnerComponent, ArtemisTranslatePipe],
    // The spinner sits on the dark navbar in both themes; the component draws in the primary colour and has no colour input.
    styles: ':host { display: flex; --tumaet-ui-primary-color: var(--white); }',
    template: `
        @if (isLoading()) {
            <tumaet-ui-progress-spinner style="width: 18px; height: 18px" [ariaLabel]="'loading' | artemisTranslate" />
        }
    `,
})
export class LoadingNotificationComponent implements OnInit, OnDestroy {
    private loadingNotificationService = inject(LoadingNotificationService);

    // Under zoneless change detection the (debounced) subscription callback below mutates a signal,
    // which automatically schedules change detection so the spinner toggles.
    readonly isLoading = signal(false);
    loadingSubscription?: Subscription;

    ngOnInit() {
        /**
         * wait 1000 ms before updating isLoading value to ensure the loading screen will not be visible for fast HttpRequests
         * */
        this.loadingSubscription = this.loadingNotificationService.loadingStatus.pipe(debounceTime(1000)).subscribe((value) => {
            this.isLoading.set(value);
        });
    }

    ngOnDestroy() {
        this.loadingSubscription?.unsubscribe();
    }
}
