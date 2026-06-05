import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { LoadingNotificationService } from 'app/core/loading-notification/loading-notification.service';

@Component({
    selector: 'jhi-loading-notification',
    template: `
        @if (isLoading) {
            <div class="spinner-border" role="status" style="width: 18px; height: 18px; color: white"></div>
        }
    `,
})
export class LoadingNotificationComponent implements OnInit, OnDestroy {
    private loadingNotificationService = inject(LoadingNotificationService);
    // Under zoneless change detection the (debounced) subscription callback below must explicitly
    // schedule change detection, otherwise the spinner would never toggle.
    private readonly changeDetectorRef = inject(ChangeDetectorRef);

    isLoading = false;
    loadingSubscription: Subscription;

    ngOnInit() {
        /**
         * wait 1000 ms before updating isLoading value to ensure the loading screen will not be visible for fast HttpRequests
         * */
        this.loadingSubscription = this.loadingNotificationService.loadingStatus.pipe(debounceTime(1000)).subscribe((value) => {
            this.isLoading = value;
            this.changeDetectorRef.markForCheck();
        });
    }

    ngOnDestroy() {
        this.loadingSubscription.unsubscribe();
    }
}
