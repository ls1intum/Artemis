import { Component, DestroyRef, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CourseNotification, payloadOf } from 'app/notification/shared/entities/course-notification/course-notification';
import { Subscription, filter } from 'rxjs';
import { CourseNotificationComponent } from 'app/notification/course-notification/course-notification/course-notification.component';
import { CourseNotificationWebsocketService } from 'app/notification/course-notification/course-notification-websocket.service';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { CourseNotificationViewingStatus } from 'app/notification/shared/entities/course-notification/course-notification-viewing-status';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, ActivatedRouteSnapshot, NavigationEnd, PRIMARY_OUTLET, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faTimes, faTrash } from '@fortawesome/free-solid-svg-icons';
import { ConversationSelectionState } from 'app/communication/shared/course-conversations/course-conversation-selection.state';
import { CourseNotificationCategory } from 'app/notification/shared/entities/course-notification/course-notification-category';
import { ButtonModule } from 'primeng/button';

/**
 * Component that displays real-time notification popups.
 * Shows current-course notifications inside a course and all-course notifications elsewhere.
 * Handles automatic timeout and manual dismissal of notifications.
 */
@Component({
    selector: 'jhi-course-notification-popup-overlay',
    imports: [CourseNotificationComponent, CommonModule, FaIconComponent, ButtonModule],
    templateUrl: './course-notification-popup-overlay.component.html',
    styleUrls: ['./course-notification-popup-overlay.component.scss'],
})
export class CourseNotificationPopupOverlayComponent implements OnInit, OnDestroy {
    protected readonly popupTimeInMilliseconds = 40000;

    private readonly courseNotificationWebsocketService = inject(CourseNotificationWebsocketService);
    private readonly courseNotificationService = inject(CourseNotificationService);

    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly destroyRef = inject(DestroyRef);
    private readonly communicationState = inject(ConversationSelectionState);

    protected readonly notifications = signal<CourseNotification[]>([]);
    protected readonly isExpanded = signal(false);

    private courseNotificationWebsocketSubscription?: Subscription;

    // Icons
    protected readonly faTimes = faTimes;
    protected readonly faTrash = faTrash;

    ngOnInit(): void {
        this.courseNotificationWebsocketSubscription = this.courseNotificationWebsocketService.websocketNotification$.subscribe((notification) => {
            const courseId = this.getActiveCourseId();
            // Hidden other-course notifications remain unread in the all-course history.
            if (courseId !== undefined && notification.courseId !== courseId) {
                return;
            }
            if (this.notifications().findIndex((existingNotification) => existingNotification.notificationId === notification.notificationId) !== -1) {
                return;
            }

            if (!this.shouldShowNotification(notification)) {
                // Calling closeClicked ensures the notification gets marked as seen
                this.closeClicked(notification);
                return;
            }

            this.notifications.update((notifications) => [...notifications, notification]);

            setTimeout(() => {
                this.removeNotification(notification.notificationId!);
            }, this.popupTimeInMilliseconds);
        });
        this.router.events
            .pipe(
                filter((event) => event instanceof NavigationEnd),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(() => {
                const courseId = this.getActiveCourseId();
                if (courseId !== undefined) {
                    this.notifications.update((notifications) => notifications.filter((notification) => notification.courseId === courseId));
                    if (this.notifications().length === 0) {
                        this.isExpanded.set(false);
                    }
                }
            });
    }

    ngOnDestroy(): void {
        this.courseNotificationWebsocketSubscription?.unsubscribe();
    }

    /**
     * Removes a notification from the display.
     * If all notifications are removed, collapses the overlay.
     *
     * @param notificationId - The ID of the notification to remove
     */
    removeNotification(notificationId: number): void {
        const indexToRemove = this.notifications().findIndex((notification) => notification.notificationId === notificationId);

        if (indexToRemove !== -1) {
            this.notifications.update((notifications) => notifications.filter((_, index) => index !== indexToRemove));
        }

        if (this.notifications().length === 0) {
            this.isExpanded.set(false);
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
        const courseId = this.getActiveCourseId();

        if (courseId === undefined) {
            return true;
        }
        if (courseId !== notification.courseId) {
            return false;
        }

        const routeParams = this.route.snapshot.queryParamMap;
        if (!notification.payload) {
            // Nothing to compare the open view against
            return true;
        }

        // Communication
        const isCommunicationNotification = notification.category == CourseNotificationCategory.COMMUNICATION;
        const openConversationId = routeParams.get('conversationId');
        const isCommunicationOpen = openConversationId != null;
        if (isCommunicationNotification && !isCommunicationOpen) {
            return true;
        }

        // The channel a post was written in, read from the payload of the type that carries it.
        const openedChannelId = payloadOf(notification, 'newPostNotification')?.channelId ?? payloadOf(notification, 'newAnnouncementNotification')?.channelId;
        if (openedChannelId !== undefined && openConversationId == String(openedChannelId)) {
            return false;
        }

        const openThreadId = this.communicationState.openPostId();
        const answeredPostId = payloadOf(notification, 'newAnswerNotification')?.postId;
        if (answeredPostId !== undefined && openThreadId == answeredPostId) {
            return false;
        }

        return true;
    }

    /** Resolve the deepest course parameter on the primary route, including management routes. */
    private getActiveCourseId(): number | undefined {
        let route: ActivatedRouteSnapshot | undefined = this.router.routerState.snapshot.root;
        let courseId: number | undefined;
        while (route) {
            const value = route.paramMap.get('courseId');
            if (value !== null) {
                courseId = Number(value);
            }
            route = route.children.find((child) => child.outlet === PRIMARY_OUTLET);
        }
        return courseId !== undefined && Number.isSafeInteger(courseId) && courseId > 0 ? courseId : undefined;
    }

    /**
     * Handles clicks on the notification overlay.
     * Expands the overlay if it's not already expanded and there are multiple notifications.
     */
    overlayClicked() {
        if (this.isExpanded() || this.notifications().length <= 1) {
            return;
        }

        this.isExpanded.set(true);
    }

    /**
     * Handles clicks on the collapse button.
     * Collapses the expanded overlay using a timeout to avoid
     * conflicts with the overlay click handler.
     */
    collapseOverlayClicked() {
        if (!this.isExpanded()) {
            return;
        }

        // To avoid overlap with the overlayClicked function, we do this on the next tick
        setTimeout(() => {
            this.isExpanded.set(false);
        });
    }

    /**
     * Clears all currently visible notifications and marks them as seen.
     */
    clearAllNotifications() {
        if (!this.isExpanded()) {
            return;
        }

        const notificationCourseMap: Record<string, Array<CourseNotification>> = {};

        this.notifications().forEach((notification) => {
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
            this.notifications.set([]);
            this.isExpanded.set(false);
        });
    }
}
