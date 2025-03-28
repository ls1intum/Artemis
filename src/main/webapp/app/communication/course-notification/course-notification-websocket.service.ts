import { Injectable, inject } from '@angular/core';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { Subject, Subscription } from 'rxjs';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { CourseNotificationCategory } from 'app/communication/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { CourseManagementService } from 'app/core/course/manage/course-management.service';
import { convertDateFromServer } from 'app/shared/util/date.utils';

/**
 * Service for handling course notification websocket connections.
 * Manages websocket subscriptions for course notifications and propagates
 * received notifications to the appropriate services.
 */
@Injectable({
    providedIn: 'root',
})
export class CourseNotificationWebsocketService {
    private websocketService = inject(WebsocketService);
    private courseNotificationService = inject(CourseNotificationService);
    private courseManagementService = inject(CourseManagementService);

    private courseWebsocketSubscriptions: Record<number, Subscription> = {};
    private websocketNotificationSubject = new Subject<CourseNotification>();

    public websocketNotification$ = this.websocketNotificationSubject.asObservable();

    constructor() {
        this.courseManagementService.getCoursesForNotifications().subscribe((courses) => {
            if (!courses) {
                return;
            }

            courses!.forEach((course) => {
                if (course.id) {
                    this.subscribeToCourseTopic(course.id);
                }
            });
        });
    }

    /**
     * Creates a websocket subscription for the specified course.
     * Handles incoming notifications by converting them to CourseNotification objects,
     * adding them to the notification service, and emitting them via subject.
     *
     * @param courseId - The ID of the course to subscribe to
     * @returns The websocket subscription
     */
    private subscribeToCourseTopic(courseId: number): Subscription {
        if (this.courseWebsocketSubscriptions[courseId]) {
            return this.courseWebsocketSubscriptions[courseId];
        }

        const topicPrefix = '/user/topic/communication/notification/';
        this.courseWebsocketSubscriptions[courseId] = this.websocketService
            .subscribe(topicPrefix + courseId)
            .receive(topicPrefix + courseId)
            .subscribe((notification: CourseNotification) => {
                const courseNotification = new CourseNotification(
                    notification.notificationId!,
                    notification.courseId!,
                    notification.notificationType!,
                    CourseNotificationCategory[notification.category as unknown as keyof typeof CourseNotificationCategory],
                    CourseNotificationViewingStatus[notification.status as unknown as keyof typeof CourseNotificationViewingStatus],
                    convertDateFromServer(notification.creationDate!)!,
                    notification.parameters!,
                    notification.relativeWebAppUrl!,
                );
                this.courseNotificationService.addNotification(courseId, courseNotification);
                this.websocketNotificationSubject.next(courseNotification);
            });

        return this.courseWebsocketSubscriptions[courseId];
    }
}
