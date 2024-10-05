import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Subscription } from 'rxjs';
import { LoadingNotificationService } from 'app/shared/notification/loading-notification/loading-notification.service';
import { debounceTime } from 'rxjs/operators';

@Component({
    selector: 'jhi-loading-notification',
    template: `
        @if (isLoading) {
            <div class="spinner-border" role="status" style="width: 18px; height: 18px; color: white"></div>
        }
    `,
    imports: [ArtemisSharedModule],
    standalone: true,
})
export class LoadingNotificationComponent implements OnInit, OnDestroy {
    private loadingNotificationService = inject(LoadingNotificationService);

    isLoading = false;
    loadingSubscription: Subscription;

    ngOnInit() {
        /**
         * wait 1000 ms before updating isLoading value to ensure the loading screen will not be visible for fast HttpRequests
         * */
        this.loadingSubscription = this.loadingNotificationService.loadingStatus.pipe(debounceTime(1000)).subscribe((value) => {
            this.isLoading = value;
        });
    }

    ngOnDestroy() {
        this.loadingSubscription.unsubscribe();
    }
}
