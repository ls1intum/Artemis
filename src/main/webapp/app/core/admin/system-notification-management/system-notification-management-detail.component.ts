import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterOutlet } from '@angular/router';
import { faWrench } from '@fortawesome/free-solid-svg-icons';
import { SystemNotification } from 'app/core/shared/entities/system-notification.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

/**
 * Component for displaying system notification details.
 * Shows notification information loaded from the route resolver.
 */
@Component({
    selector: 'jhi-system-notification-management-detail',
    templateUrl: './system-notification-management-detail.component.html',
    imports: [TranslateDirective, RouterLink, FaIconComponent, RouterOutlet, ArtemisDatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemNotificationManagementDetailComponent implements OnInit {
    private readonly route = inject(ActivatedRoute);

    /** The notification to display, loaded from route resolver */
    readonly notification = signal<SystemNotification | undefined>(undefined);

    /** Icon for the edit button */
    protected readonly faWrench = faWrench;

    /**
     * Subscribes to route data to load the notification from the resolver.
     */
    ngOnInit(): void {
        this.route.data.subscribe(({ notification }) => {
            const resolvedNotification = notification?.body ?? notification;
            this.notification.set(resolvedNotification);
        });
    }
}
