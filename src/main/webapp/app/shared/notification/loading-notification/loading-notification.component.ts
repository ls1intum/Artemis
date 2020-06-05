import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { LoadingNotificationService } from 'app/shared/notification/loading-notification/loading-notification.service';
import { debounceTime } from 'rxjs/operators';

@Component({
    selector: 'jhi-loading-notification',
    template: ` <div *ngIf="isLoading" class="slider">
        <div class="line"></div>
        <div class="subline inc"></div>
        <div class="subline dec"></div>
    </div>`,
    styles: [
        `
            body {
                background: #ffffff;
                margin: 50px 300px;
            }

            .slider {
                position: absolute;
                width: 100%;
                height: 5px;
                overflow-x: hidden;
            }

            .line {
                position: absolute;
                opacity: 0.4;
                background: #4a8df8;
                width: 150%;
                height: 5px;
            }

            .subline {
                position: absolute;
                background: #4a8df8;
                height: 5px;
            }
            .inc {
                animation: increase 2s infinite;
            }
            .dec {
                animation: decrease 2s 0.5s infinite;
            }

            @keyframes increase {
                from {
                    left: -5%;
                    width: 5%;
                }
                to {
                    left: 130%;
                    width: 100%;
                }
            }
            @keyframes decrease {
                from {
                    left: -80%;
                    width: 80%;
                }
                to {
                    left: 110%;
                    width: 10%;
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
        this.loadingSubscription = this.loadingNotificationService.loadingStatus.pipe(debounceTime(200)).subscribe((value) => {
            this.isLoading = value;
        });
    }

    ngOnDestroy() {
        this.loadingSubscription.unsubscribe();
    }
}
