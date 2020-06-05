import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { LoadingNotificationService } from 'app/shared/notification/loading-notification/loading-notification.service';
import { debounceTime } from 'rxjs/operators';

@Component({
    selector: 'jhi-loading-notification',
    template: ` <div *ngIf="isLoading" class="progress-line"></div> `,
    styles: [
        `
            .progress-line,
            .progress-line:before {
                height: 7px;
                width: 100%;
                margin: 0;
            }
            .progress-line {
                background-color: #b3d4fc;
                display: -webkit-flex;
                display: flex;
            }
            .progress-line:before {
                background-color: #3f51b5;
                content: '';
                -webkit-animation: running-progress 1.3s cubic-bezier(0.4, 0, 0.2, 1) infinite;
                animation: running-progress 1.3s cubic-bezier(0.4, 0, 0.2, 1) infinite;
            }
            @-webkit-keyframes running-progress {
                0% {
                    margin-left: 0px;
                    margin-right: 100%;
                }
                50% {
                    margin-left: 25%;
                    margin-right: 0%;
                }
                100% {
                    margin-left: 100%;
                    margin-right: 0;
                }
            }
            @keyframes running-progress {
                0% {
                    margin-left: 0px;
                    margin-right: 100%;
                }
                50% {
                    margin-left: 25%;
                    margin-right: 0%;
                }
                100% {
                    margin-left: 100%;
                    margin-right: 0;
                }
            }
        `,
    ],
})
export class LoadingNotificationComponent implements OnInit, OnDestroy {
    isLoading = false;
    loadingSubscription: Subscription;

    constructor(private loadingNotificationService: LoadingNotificationService) {}

    ngOnInit() {
        /**
         * wait 200 ms before updating isLoading value to ensure the loading screen will not be visible for fast HttpRequests
         * */
        console.log('loading status');
        console.log(this.loadingNotificationService.loadingStatus);
        this.loadingSubscription = this.loadingNotificationService.loadingStatus.pipe(debounceTime(200)).subscribe((value) => {
            console.log('before: ' + this.isLoading);
            this.isLoading = value;
            console.log('after: ' + this.isLoading);
        });
    }

    ngOnDestroy() {
        this.loadingSubscription.unsubscribe();
    }
}
