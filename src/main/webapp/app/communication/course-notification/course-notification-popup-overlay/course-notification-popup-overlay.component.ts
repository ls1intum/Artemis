import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { Subscription } from 'rxjs';
import { CourseNotificationComponent } from 'app/communication/course-notification/course-notification/course-notification.component';
import { CourseNotificationWebsocketService } from 'app/communication/course-notification/course-notification-websocket.service';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';
import { CommonModule } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';

/**
 * Component that displays real-time notification popups.
 * Shows notifications received via websocket in a collapsible overlay.
 * Handles automatic timeout and manual dismissal of notifications.
 */
@Component({
    selector: 'jhi-course-notification-popup-overlay',
    imports: [CourseNotificationComponent, CommonModule, FaIconComponent],
    templateUrl: './course-notification-popup-overlay.component.html',
    styleUrls: ['./course-notification-popup-overlay.component.scss'],
})
export class CourseNotificationPopupOverlayComponent implements OnInit, OnDestroy {
    protected readonly popupTimeInMilliseconds = 40000;

    private readonly courseNotificationWebsocketService = inject(CourseNotificationWebsocketService);
    private readonly courseNotificationService = inject(CourseNotificationService);

    protected notifications: CourseNotification[] = [];
    protected isExpanded: boolean = false;

    private courseNotificationWebsocketSubscription: Subscription;

    // Icons
    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;

    ngOnInit(): void {
        this.courseNotificationWebsocketSubscription = this.courseNotificationWebsocketService.websocketNotification$.subscribe((notification) => {
            if (this.notifications.findIndex((existingNotification) => existingNotification.notificationId === notification.notificationId) !== -1) {
                return;
            }

            this.notifications.push(notification);

            setTimeout(() => {
                this.removeNotification(notification.notificationId!);
            }, this.popupTimeInMilliseconds);
        });
    }

    ngOnDestroy(): void {
        this.courseNotificationWebsocketSubscription.unsubscribe();
    }

    /**
     * Removes a notification from the display.
     * If all notifications are removed, collapses the overlay.
     *
     * @param notificationId - The ID of the notification to remove
     */
    removeNotification(notificationId: number): void {
        const indexToRemove = this.notifications.findIndex((notification) => notification.notificationId === notificationId);

        if (indexToRemove !== -1) {
            this.notifications.splice(indexToRemove, 1);
        }

        if (this.notifications.length === 0) {
            this.isExpanded = false;
        }
    }

    /**
     * Handles the close button click for a notification.
     * Marks the notification as seen both in local state and on server.
     * Updates notification count and removes it from display.
     *
     * @param notification - The notification being closed
     */
    closeClicked(notification: CourseNotification) {
        this.courseNotificationService.setNotificationStatus(notification.courseId!, [notification.notificationId!], CourseNotificationViewingStatus.SEEN);
        this.courseNotificationService.setNotificationStatusInMap(notification.courseId!, [notification.notificationId!], CourseNotificationViewingStatus.SEEN);
        this.courseNotificationService.decreaseNotificationCountBy(notification.courseId!, 1);
        this.removeNotification(notification.notificationId!);
    }

    /**
     * Handles clicks on the notification overlay.
     * Expands the overlay if it's not already expanded and there are multiple notifications.
     */
    overlayClicked() {
        if (this.isExpanded || this.notifications.length <= 1) {
            return;
        }

        this.isExpanded = true;
    }

    /**
     * Handles clicks on the collapse button.
     * Collapses the expanded overlay using a timeout to avoid
     * conflicts with the overlay click handler.
     */
    collapseOverlayClicked() {
        if (!this.isExpanded) {
            return;
        }

        // To avoid overlap with the overlayClicked function, we do this on the next tick
        setTimeout(() => {
            this.isExpanded = false;
        });
    }

    /**
     * Clears all currently visible notifications and marks them as seen.
     */
    clearAllNotifications() {
        if (!this.isExpanded) {
            return;
        }

        const notificationCourseMap: Record<string, Array<CourseNotification>> = {};

        this.notifications.forEach((notification) => {
            if (!notificationCourseMap[notification.courseId!]) {
                notificationCourseMap[notification.courseId!] = [notification];
            } else {
                notificationCourseMap[notification.courseId!].push(notification);
            }
        });

        for (const courseId of Object.keys(notificationCourseMap)) {
            const courseIdNumber = Number(courseId);
            this.courseNotificationService.setNotificationStatus(
                courseIdNumber,
                notificationCourseMap[courseId].map((notification) => notification.notificationId!),
                CourseNotificationViewingStatus.SEEN,
            );
            this.courseNotificationService.setNotificationStatusInMap(
                courseIdNumber,
                notificationCourseMap[courseId].map((notification) => notification.notificationId!),
                CourseNotificationViewingStatus.SEEN,
            );
            this.courseNotificationService.decreaseNotificationCountBy(courseIdNumber, notificationCourseMap[courseId].length);
        }

        // To avoid overlap with the overlayClicked function, we do this on the next tick
        setTimeout(() => {
            this.notifications = [];
            this.isExpanded = false;
        });
    }
}
