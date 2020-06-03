import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { LoadingNotificationService } from 'app/shared/notification/loading-notification/loading-notification.service';
import { debounceTime } from 'rxjs/operators';

@Component({
    selector: 'jhi-loading-notification',
    template: `
        <div *ngIf="isLoading" class="progress" style="height: 7px;">
            <div class="progress-bar progress-bar-striped progress-bar-animated w-100" role="progressbar" aria-valuenow="75" aria-valuemin="0" aria-valuemax="100"></div>
        </div>
    `,
})
export class LoadingNotificationComponent implements OnInit, OnDestroy {
    isLoading: boolean;
    loadingSubscription: Subscription;

    constructor(private loadingNotificationService: LoadingNotificationService) {}

    ngOnInit() {
        this.loadingSubscription = this.loadingNotificationService.loadingStatus.pipe(debounceTime(200)).subscribe((value) => {
            this.isLoading = value;
        });
    }

    ngOnDestroy() {
        this.loadingSubscription.unsubscribe();
    }
}
