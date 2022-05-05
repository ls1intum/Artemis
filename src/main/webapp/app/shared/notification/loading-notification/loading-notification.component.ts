import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { LoadingNotificationService } from 'app/shared/notification/loading-notification/loading-notification.service';
import { debounceTime } from 'rxjs/operators';

@Component({
    selector: 'jhi-loading-notification',
    template: ` <div *ngIf="isLoading" class="spinner-border" role="status" style="width: 18px; height: 18px; color: white"></div> `,
})
export class LoadingNotificationComponent implements OnInit, OnDestroy {
    isLoading = false;
    loadingSubscription: Subscription;

    constructor(private loadingNotificationService: LoadingNotificationService) {}

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
