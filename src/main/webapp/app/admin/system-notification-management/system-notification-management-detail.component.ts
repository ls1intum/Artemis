import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterOutlet } from '@angular/router';
import { faWrench } from '@fortawesome/free-solid-svg-icons';
import { SystemNotification } from 'app/entities/system-notification.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-system-notification-management-detail',
    templateUrl: './system-notification-management-detail.component.html',
    imports: [TranslateDirective, RouterLink, FaIconComponent, RouterOutlet, ArtemisDatePipe],
})
export class SystemNotificationManagementDetailComponent implements OnInit {
    private route = inject(ActivatedRoute);

    notification: SystemNotification;
    // Icons
    faWrench = faWrench;

    /**
     * Assigns the subscription to system notification service
     */
    ngOnInit() {
        this.route.data.subscribe(({ notification }) => {
            this.notification = notification.body ? notification.body : notification;
        });
    }
}
