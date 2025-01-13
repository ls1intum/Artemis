import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink, RouterOutlet } from '@angular/router';
import { faWrench } from '@fortawesome/free-solid-svg-icons';
import { SystemNotification } from 'app/entities/system-notification.model';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-system-notification-management-detail',
    templateUrl: './system-notification-management-detail.component.html',
    imports: [TranslateDirective, RouterLink, FaIconComponent, RouterOutlet, ArtemisDatePipe],
})
export class SystemNotificationManagementDetailComponent implements OnInit {
    private systemNotificationService = inject(SystemNotificationService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);

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
