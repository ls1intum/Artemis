import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CourseNotification } from 'app/entities/course-notification/course-notification';
import { Subscription } from 'rxjs';
import { CourseNotificationComponent } from 'app/communication/course-notification/course-notification/course-notification.component';
import { CourseNotificationWebsocketService } from 'app/communication/course-notification/course-notification-websocket.service';
import { animate, style, transition, trigger } from '@angular/animations';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { CourseNotificationViewingStatus } from 'app/entities/course-notification/course-notification-viewing-status';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faTimes } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-course-notification-popup-overlay',
    imports: [CourseNotificationComponent, CommonModule, FaIconComponent],
    templateUrl: './course-notification-popup-overlay.component.html',
    styleUrls: ['./course-notification-popup-overlay.component.scss'],
    animations: [
        trigger('notificationAnimation', [
            transition(':enter', [style({ opacity: 0, transform: 'scale(0)' }), animate('200ms ease-in-out', style({ opacity: 1, transform: 'scale(100%)' }))]),
            transition(':leave', [animate('200ms ease-in-out', style({ opacity: 0, transform: 'scale(0)' }))]),
        ]),
    ],
})
export class CourseNotificationPopupOverlayComponent implements OnInit, OnDestroy {
    protected readonly popupTimeInMilliseconds = 20000;

    private readonly courseNotificationWebsocketService = inject(CourseNotificationWebsocketService);
    private readonly courseNotificationService = inject(CourseNotificationService);

    protected notifications: CourseNotification[] = [];
    protected isExpanded: boolean = false;

    private courseNotificationWebsocketSubscription: Subscription;

    // Icons
    protected readonly faTimes = faTimes;

    ngOnInit(): void {
        this.courseNotificationWebsocketSubscription = this.courseNotificationWebsocketService.websocketNotification$.subscribe((notification) => {
            this.notifications.push(notification);

            setTimeout(() => {
                this.removeNotification(notification.notificationId!);
            }, this.popupTimeInMilliseconds);
        });
    }

    ngOnDestroy(): void {
        this.courseNotificationWebsocketSubscription.unsubscribe();
    }

    removeNotification(notificationId: number): void {
        const indexToRemove = this.notifications.findIndex((notification) => notification.notificationId === notificationId);

        if (indexToRemove !== -1) {
            this.notifications.splice(indexToRemove, 1);
        }

        if (this.notifications.length === 0) {
            this.isExpanded = false;
        }
    }

    closeClicked(notification: CourseNotification) {
        this.courseNotificationService.setNotificationStatus(notification.courseId!, [notification.notificationId!], CourseNotificationViewingStatus.SEEN);
        this.courseNotificationService.setNotificationStatusInMap(notification.courseId!, [notification.notificationId!], CourseNotificationViewingStatus.SEEN);
        this.courseNotificationService.decreaseNotificationCountBy(notification.courseId!, 1);
        this.removeNotification(notification.notificationId!);
    }

    overlayClicked() {
        if (this.isExpanded || this.notifications.length <= 1) {
            return;
        }

        this.isExpanded = true;
    }

    collapseOverlayClicked() {
        if (!this.isExpanded) {
            return;
        }

        // To avoid overlap with the overlayClicked function, we do this on the next tick
        setTimeout(() => {
            this.isExpanded = false;
        });
    }
}
