import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { SystemNotification, SystemNotificationService } from 'app/entities/system-notification';

@Component({
    selector: 'jhi-notification-mgmt-detail',
    templateUrl: './notification-management-detail.component.html'
})
export class NotificationMgmtDetailComponent implements OnInit, OnDestroy {
    notification: SystemNotification;
    private subscription: Subscription;

    constructor(private systemNotificationService: SystemNotificationService, private route: ActivatedRoute) {}

    ngOnInit() {
        this.subscription = this.route.params.subscribe(params => {
            this.load(params['id']);
        });
    }

    load(id: string) {
        this.systemNotificationService.find(parseInt(id)).subscribe(response => {
            this.notification = response.body;
        });
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }
}
