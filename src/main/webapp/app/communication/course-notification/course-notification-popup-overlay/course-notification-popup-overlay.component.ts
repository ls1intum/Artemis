import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { Subscription } from 'rxjs';
import { CourseNotificationComponent } from 'app/communication/course-notification/course-notification/course-notification.component';
import { CourseNotificationWebsocketService } from 'app/communication/course-notification/course-notification-websocket.service';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ConversationSelectionState } from 'app/communication/shared/course-conversations/course-conversation-selection.state';
import { CourseNotificationCategory } from 'app/communication/shared/entities/course-notification/course-notification-category';

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

    private readonly route = inject(ActivatedRoute);
    private readonly communicationState = inject(ConversationSelectionState);

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

            if (!this.shouldShowNotification(notification)) {
                // Calling closeClicked ensures the notification gets marked as seen
                this.closeClicked(notification);
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
     * Checks whether it makes sense to show a notification to the user in the current context, e.g. when a conversation is open.
     *
     * @param notification - The notification to potentially show
     * @returns shouldShow - Whether the notification should be shown
     */
    shouldShowNotification(notification: CourseNotification): boolean {
        const courseId = this.route.firstChild?.firstChild?.snapshot.paramMap.get('courseId');

        // Course is not open
        if (!courseId || Number(courseId) !== notification.courseId) {
            return true;
        }

        const routeParams = this.route.snapshot.queryParamMap;
        const notificationParams = notification.parameters;
        if (!notificationParams) {
            // No filtering possible without parameters
            return true;
        }

        // Communication
        const isCommunicationNotification = notification.category == CourseNotificationCategory.COMMUNICATION;
        const openConversationId = routeParams.get('conversationId');
        const isCommunicationOpen = openConversationId != null;
        if (isCommunicationNotification && !isCommunicationOpen) {
            return true;
        }

        const isAnnouncementOrPost = ['newPostNotification', 'newAnnouncementNotification'].includes(notification.notificationType ?? '');
        const isCorrespondingChannelOpen = 'channelId' in notificationParams && openConversationId == notificationParams['channelId'];
        if (isAnnouncementOrPost && isCorrespondingChannelOpen) {
            return false;
        }

        const threadId = this.communicationState.openPostId();
        const isAnswerNotification = notification.notificationType === 'newAnswerNotification';
        const isCorrespondingThreadOpen = 'postId' in notificationParams && threadId == notificationParams['postId'];
        if (isAnswerNotification && isCorrespondingThreadOpen) {
            return false;
        }

        return true;
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
