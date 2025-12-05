import { Injectable, OnDestroy, inject } from '@angular/core';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { Subject, Subscription } from 'rxjs';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { CourseNotificationCategory } from 'app/communication/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';

/**
 * Service for handling course notification websocket connections.
 * Manages websocket subscriptions for course notifications and propagates
 * received notifications to the appropriate services.
 */
@Injectable({
    providedIn: 'root',
})
export class CourseNotificationWebsocketService implements OnDestroy {
    private websocketService = inject(WebsocketService);
    private courseNotificationService = inject(CourseNotificationService);
    private courseManagementService = inject(CourseManagementService);
    private accountService = inject(AccountService);

    private courseWebsocketSubscriptions: Record<number, Subscription> = {};
    private websocketNotificationSubject = new Subject<CourseNotification>();
    private userSubscription: Subscription;
    private coursesSubscription: Subscription | undefined = undefined;

    private currentUser: User | undefined;

    public websocketNotification$ = this.websocketNotificationSubject.asObservable();

    constructor() {
        this.userSubscription = this.accountService.getAuthenticationState().subscribe((user) => {
            if (user && (this.currentUser === undefined || this.currentUser.id !== user.id)) {
                this.currentUser = user;

                this.cleanupSubscriptions();
                this.subscribeToUserCourses();
            }
        });
    }

    /**
     * Clean up all subscriptions when the service is destroyed
     */
    ngOnDestroy(): void {
        this.cleanupSubscriptions();
        this.userSubscription.unsubscribe();
    }

    /**
     * Subscribes to all courses for the current user
     */
    private subscribeToUserCourses(): void {
        if (this.coursesSubscription) {
            this.coursesSubscription.unsubscribe();
        }

        this.coursesSubscription = this.courseManagementService.getCoursesForNotifications().subscribe((courses) => {
            if (!courses) {
                return;
            }

            courses.forEach((course) => {
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
                const category = this.parseCategory(notification.category);
                const status = this.parseViewingStatus(notification.status);
                if (category === undefined || status === undefined) {
                    return;
                }
                const courseNotification = new CourseNotification(
                    notification.notificationId!,
                    notification.courseId!,
                    notification.notificationType!,
                    category,
                    status,
                    convertDateFromServer(notification.creationDate!)!,
                    notification.parameters!,
                    notification.relativeWebAppUrl!,
                );
                this.courseNotificationService.addNotification(courseId, courseNotification);
                this.websocketNotificationSubject.next(courseNotification);
            });

        return this.courseWebsocketSubscriptions[courseId];
    }

    /**
     * Cleans up all websocket and course subscriptions
     */
    private cleanupSubscriptions(): void {
        if (this.coursesSubscription) {
            this.coursesSubscription.unsubscribe();
            this.coursesSubscription = undefined;
        }

        Object.keys(this.courseWebsocketSubscriptions).forEach((courseId) => {
            const numericCourseId = Number(courseId);
            if (this.courseWebsocketSubscriptions[numericCourseId]) {
                this.courseWebsocketSubscriptions[numericCourseId].unsubscribe();
                delete this.courseWebsocketSubscriptions[numericCourseId];
            }
        });
    }

    private parseCategory(category: unknown): CourseNotificationCategory | undefined {
        if (typeof category === 'number' && CourseNotificationCategory[category] !== undefined) {
            return category as CourseNotificationCategory;
        }
        if (typeof category === 'string' && category in CourseNotificationCategory) {
            return CourseNotificationCategory[category as keyof typeof CourseNotificationCategory];
        }
        return undefined;
    }

    private parseViewingStatus(status: unknown): CourseNotificationViewingStatus | undefined {
        if (typeof status === 'number' && CourseNotificationViewingStatus[status] !== undefined) {
            return status as CourseNotificationViewingStatus;
        }
        if (typeof status === 'string' && status in CourseNotificationViewingStatus) {
            return CourseNotificationViewingStatus[status as keyof typeof CourseNotificationViewingStatus];
        }
        return undefined;
    }
}
