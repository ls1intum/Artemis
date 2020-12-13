import { filter } from 'rxjs/operators';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, NavigationEnd } from '@angular/router';
import { SystemNotification } from 'app/entities/system-notification.model';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';

@Component({
    selector: 'jhi-system-notification-management-detail',
    templateUrl: './system-notification-management-detail.component.html',
})
export class SystemNotificationManagementDetailComponent implements OnInit {
    notification: SystemNotification;
    isVisible: boolean;

    constructor(private systemNotificationService: SystemNotificationService, private route: ActivatedRoute, private router: Router) {}

    /**
     * Assigns the subscription to system notification service
     */
    ngOnInit() {
        this.route.data.subscribe(({ notification }) => {
            this.notification = notification.body ? notification.body : notification;
        });
        this.isVisible = this.route.children.length === 0;
        this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => (this.isVisible = this.route.children.length === 0));
    }
}
