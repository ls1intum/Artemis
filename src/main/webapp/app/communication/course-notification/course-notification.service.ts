import { Injectable, inject } from '@angular/core';
import { faComments, faPersonChalkboard, faRectangleList, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { CourseNotification } from 'app/communication/shared/entities/course-notification/course-notification';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable, of, tap } from 'rxjs';
import { CourseNotificationInfo } from 'app/communication/shared/entities/course-notification/course-notification-info';
import { CourseNotificationPage } from 'app/communication/shared/entities/course-notification/course-notification-page';
import { CourseNotificationCategory } from 'app/communication/shared/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/communication/shared/entities/course-notification/course-notification-viewing-status';
import { CourseNotificationChannel } from 'app/communication/shared/entities/course-notification/course-notification-channel';
import { convertDateFromServer } from 'app/shared/util/date.utils';

/**
 * Service for managing course notifications.
 * Handles fetching, storing, updating, and tracking notification data for courses.
 * Provides observables for notification counts and content.
 */
@Injectable({
    providedIn: 'root',
})
export class CourseNotificationService {
    public static readonly NOTIFICATION_TYPE_ICON_MAP = {
        newPostNotification: faComments,
        newAnswerNotification: faComments,
        newMentionNotification: faComments,
        newAnnouncementNotification: faComments,
        newExerciseNotification: faRectangleList,
        exerciseOpenForPracticeNotification: faRectangleList,
        exerciseAssessedNotification: faRectangleList,
        exerciseUpdatedNotification: faRectangleList,
        quizExerciseStartedNotification: faRectangleList,
        attachmentChangedNotification: faRectangleList,
        newManualFeedbackRequestNotification: faRectangleList,
        channelDeletedNotification: faComments,
        addedToChannelNotification: faComments,
        removedFromChannelNotification: faComments,
        duplicateTestCaseNotification: faTriangleExclamation,
        newCpcPlagiarismCaseNotification: faRectangleList,
        newPlagiarismCaseNotification: faRectangleList,
        programmingBuildRunUpdateNotification: faRectangleList,
        programmingTestCasesChangedNotification: faRectangleList,
        plagiarismCaseVerdictNotification: faRectangleList,
        tutorialGroupAssignedNotification: faPersonChalkboard,
        tutorialGroupUnassignedNotification: faPersonChalkboard,
        registeredToTutorialGroupNotification: faPersonChalkboard,
        deregisteredFromTutorialGroupNotification: faPersonChalkboard,
        tutorialGroupDeletedNotification: faPersonChalkboard,
    };

    public static readonly DISABLE_NOTIFICATION_CHANNEL_TYPES: Record<string, Array<CourseNotificationChannel>> = {
        newPostNotification: [CourseNotificationChannel.EMAIL],
        newAnswerNotification: [CourseNotificationChannel.EMAIL],
        newMentionNotification: [],
        newAnnouncementNotification: [],
        newExerciseNotification: [],
        exerciseOpenForPracticeNotification: [],
        exerciseAssessedNotification: [],
        exerciseUpdatedNotification: [CourseNotificationChannel.EMAIL],
        quizExerciseStartedNotification: [CourseNotificationChannel.EMAIL],
        attachmentChangedNotification: [CourseNotificationChannel.EMAIL],
        newManualFeedbackRequestNotification: [CourseNotificationChannel.EMAIL],
        channelDeletedNotification: [CourseNotificationChannel.EMAIL],
        addedToChannelNotification: [CourseNotificationChannel.EMAIL],
        removedFromChannelNotification: [CourseNotificationChannel.EMAIL],
        duplicateTestCaseNotification: [],
        newCpcPlagiarismCaseNotification: [],
        newPlagiarismCaseNotification: [],
        programmingBuildRunUpdateNotification: [CourseNotificationChannel.EMAIL],
        programmingTestCasesChangedNotification: [CourseNotificationChannel.EMAIL],
        plagiarismCaseVerdictNotification: [],
        tutorialGroupAssignedNotification: [],
        tutorialGroupUnassignedNotification: [],
        registeredToTutorialGroupNotification: [],
        deregisteredFromTutorialGroupNotification: [],
        tutorialGroupDeletedNotification: [],
    };

    // Parameter keys that should be rendered as markdown
    public static readonly NOTIFICATION_MARKDOWN_PARAMETERS = ['postMarkdownContent', 'replyMarkdownContent'];

    private readonly apiEndpoint = '/api/communication/notification/';
    public readonly pageSize = 10;

    private http = inject(HttpClient);

    private courseNotificationMap: Record<number, CourseNotification[]> = {};
    private courseNotificationPageMap: Record<number, boolean> = {};
    private notificationSubject = new BehaviorSubject<Record<number, CourseNotification[]>>({});
    private courseNotificationCountMap: Record<number, number> = {};
    private notificationCountSubject = new BehaviorSubject<Record<number, number>>({});
    private cachedNotificationInfo: HttpResponse<CourseNotificationInfo> | null = null;

    public notificationCount$: Observable<Record<number, number>> = this.notificationCountSubject.asObservable();
    public notifications$: Observable<Record<number, CourseNotification[]>> = this.notificationSubject.asObservable();

    constructor() {
        this.notificationSubject.next(this.courseNotificationMap);
        this.notificationCountSubject.next(this.courseNotificationCountMap);
    }

    /**
     * Retrieves notification configuration information.
     *
     * @returns Observable with notification information
     */
    public getInfo(): Observable<HttpResponse<CourseNotificationInfo>> {
        if (this.cachedNotificationInfo) {
            return of(this.cachedNotificationInfo);
        }

        // Otherwise, fetch from server and cache the result
        return this.http.get<CourseNotificationInfo>(this.apiEndpoint + 'info', { observe: 'response' }).pipe(
            tap((response) => {
                if (response.body) {
                    this.cachedNotificationInfo = response;
                }
            }),
        );
    }

    /**
     * Fetches the next page of notifications for a course.
     * Updates the internal notification state and notifies subscribers.
     *
     * @param courseId - The ID of the course
     * @returns Boolean indicating if more pages are available
     */
    public getNextNotificationPage(courseId: number): boolean {
        if (this.courseNotificationPageMap[courseId]) {
            // We return false here in case we reached the final page, so that components can react accordingly.
            this.notifyNotificationSubscribers();
            return false;
        }

        if (!this.courseNotificationMap[courseId]) {
            this.courseNotificationMap[courseId] = [];
        }

        const page = Math.floor(this.courseNotificationMap[courseId].length / this.pageSize);
        this.http.get<CourseNotificationPage>(this.apiEndpoint + courseId + '?page=' + page + '&size=' + this.pageSize, { observe: 'response' }).subscribe((response) => {
            const convertedResponse = this.convertResponseFromServer(response);

            if (!convertedResponse.body || !convertedResponse.body.content) {
                this.courseNotificationPageMap[courseId] = true;
                this.notifyNotificationSubscribers();
                return;
            }

            this.courseNotificationPageMap[courseId] = page + 1 >= convertedResponse.body.totalPages;
            for (const notification of convertedResponse.body.content) {
                this.addNotificationIfNotDuplicate(courseId, notification, false);
            }

            this.notifyNotificationSubscribers();
        });

        return true;
    }

    /**
     * Updates the status of multiple notifications on the server.
     *
     * @param courseId - The ID of the course
     * @param notificationIds - Array of notification IDs to update
     * @param statusType - The new status to set
     */
    public setNotificationStatus(courseId: number, notificationIds: number[], statusType: CourseNotificationViewingStatus): void {
        this.http
            .put(this.apiEndpoint + courseId + '/status', {
                notificationIds: notificationIds,
                statusType: statusType,
            })
            .subscribe();
    }

    /**
     * Archives all notifications for a course on the server.
     *
     * @param courseId - The ID of the course
     */
    public archiveAll(courseId: number): void {
        this.http.put<void>(this.apiEndpoint + courseId + '/archive-all', {}).subscribe();
    }

    /**
     * Clears all notifications for a course from the local state.
     * Updates count and notifies subscribers.
     *
     * @param courseId - The ID of the course
     */
    public archiveAllInMap(courseId: number): void {
        this.courseNotificationMap[courseId] = [];
        this.updateNotificationCountMap(courseId, 0);
        this.notifyNotificationSubscribers();
    }

    /**
     * Updates the status of multiple notifications in the local state.
     *
     * @param courseId - The ID of the course
     * @param notificationIds - Array of notification IDs to update
     * @param statusType - The new status to set
     */
    public setNotificationStatusInMap(courseId: number, notificationIds: number[], statusType: CourseNotificationViewingStatus) {
        // This will set the notifications to seen in the user interface.
        for (let i = 0; i < this.courseNotificationMap[courseId].length; i++) {
            if (notificationIds.includes(this.courseNotificationMap[courseId][i].notificationId!)) {
                this.courseNotificationMap[courseId][i].status = statusType;
            }
        }

        this.notifyNotificationSubscribers();
    }

    /**
     * Adds a new notification to the local state.
     * Initializes course notification array if needed.
     *
     * @param courseId - The ID of the course
     * @param notification - The notification to add
     */
    public addNotification(courseId: number, notification: CourseNotification) {
        if (!this.courseNotificationMap[courseId]) {
            this.courseNotificationMap[courseId] = [];
        }
        const duplicate = this.addNotificationIfNotDuplicate(courseId, notification, true);

        if (!duplicate) {
            this.notifyNotificationSubscribers();
            this.incrementNotificationCount(courseId);
        }
    }

    /**
     * Removes a notification from the local state.
     * Updates count and notifies subscribers if found.
     *
     * @param courseId - The ID of the course
     * @param notification - The notification to remove
     */
    public removeNotificationFromMap(courseId: number, notification: CourseNotification) {
        if (!this.courseNotificationMap[courseId]) {
            return;
        }

        const index = this.courseNotificationMap[courseId].findIndex((existingNotification) => {
            return existingNotification.notificationId === notification.notificationId;
        });

        if (index != -1) {
            this.courseNotificationMap[courseId].splice(index, 1);
            this.notifyNotificationSubscribers();
            this.decreaseNotificationCountBy(courseId, 1);
        }
    }

    /**
     * Sets the notification count for a course.
     * Only updates if the count has changed.
     *
     * @param courseId - The ID of the course
     * @param count - The new notification count
     */
    public updateNotificationCountMap(courseId: number, count: number) {
        if (this.courseNotificationCountMap[courseId] === count) {
            return;
        }

        this.courseNotificationCountMap[courseId] = count;
        this.notifyCountSubscribers();
    }

    /**
     * Increments the notification count for a course by 1.
     * Initializes count if not already set.
     *
     * @param courseId - The ID of the course
     */
    public incrementNotificationCount(courseId: number) {
        if (this.courseNotificationCountMap[courseId] === undefined) {
            this.courseNotificationCountMap[courseId] = 0;
        }

        this.courseNotificationCountMap[courseId] = this.courseNotificationCountMap[courseId] + 1;
        this.notifyCountSubscribers();
    }

    /**
     * Decreases the notification count for a course.
     *
     * @param courseId - The ID of the course
     * @param count - The amount to decrease by
     */
    public decreaseNotificationCountBy(courseId: number, count: number) {
        if (this.courseNotificationCountMap[courseId] === undefined) {
            return;
        }

        this.courseNotificationCountMap[courseId] = this.courseNotificationCountMap[courseId] - count;
        this.notifyCountSubscribers();
    }

    /**
     * Notifies subscribers of changes to notification counts.
     * Creates a new object to ensure change detection.
     */
    private notifyCountSubscribers(): void {
        this.notificationCountSubject.next({ ...this.courseNotificationCountMap });
    }

    /**
     * Creates an Observable for the notification count of a specific course.
     *
     * @param courseId - The ID of the course
     * @returns Observable that emits the notification count for the course
     */
    public getNotificationCountForCourse$(courseId: number): Observable<number> {
        return new Observable<number>((observer) => {
            const subscription = this.notificationCount$.subscribe((map) => {
                observer.next(map[courseId] || 0);
            });

            return () => subscription.unsubscribe();
        });
    }

    /**
     * Notifies subscribers of changes to notifications.
     * Creates a new object to ensure change detection.
     */
    private notifyNotificationSubscribers(): void {
        this.notificationSubject.next({ ...this.courseNotificationMap });
    }

    /**
     * Creates an Observable for the notifications of a specific course.
     *
     * @param courseId - The ID of the course
     * @returns Observable that emits the notifications for the course
     */
    public getNotificationsForCourse$(courseId: number): Observable<CourseNotification[]> {
        return new Observable<CourseNotification[]>((observer) => {
            const subscription = this.notifications$.subscribe((map) => {
                observer.next(map[courseId] || []);
            });

            return () => subscription.unsubscribe();
        });
    }

    /**
     * Gets the icon for a notification type.
     * Falls back to a default icon if type is not recognized.
     *
     * @param type - The notification type
     * @returns The icon for the notification type
     */
    public getIconFromType(type: string | undefined) {
        if (type === undefined || !CourseNotificationService.NOTIFICATION_TYPE_ICON_MAP.hasOwnProperty(type)) {
            return faComments;
        }

        return CourseNotificationService.NOTIFICATION_TYPE_ICON_MAP[type as keyof typeof CourseNotificationService.NOTIFICATION_TYPE_ICON_MAP];
    }

    /**
     * Determines the translation key for a notification's timestamp.
     * Returns different keys based on how recent the notification is.
     *
     * @param notification - The notification
     * @returns The translation key for the notification's timestamp
     */
    public getDateTranslationKey(notification: CourseNotification): string {
        const now = dayjs();
        const date = notification.creationDate!;
        const diffMinutes = now.diff(date, 'minute');
        const diffHours = now.diff(date, 'hour');
        const isToday = now.format('YYYY-MM-DD') === date.format('YYYY-MM-DD');
        const isYesterday = now.subtract(1, 'day').format('YYYY-MM-DD') === date.format('YYYY-MM-DD');
        const isSameWeek = now.startOf('week').isBefore(date) && now.endOf('week').isAfter(date);

        let key: string;

        if (diffMinutes <= 5) {
            key = 'now';
        } else if (diffMinutes > 5 && diffHours < 2) {
            key = 'oneHourAgo';
        } else if (diffHours >= 2 && diffHours < 9) {
            key = 'hoursAgo';
        } else if (isToday) {
            key = 'today';
        } else if (isYesterday) {
            key = 'yesterday';
        } else if (isSameWeek) {
            key = 'thisWeek';
        } else {
            key = 'date';
        }

        return `artemisApp.courseNotification.temporal.${key}`;
    }

    /**
     * Gets the parameters for a notification's timestamp translation.
     * Includes various time formats based on the notification date.
     *
     * @param notification - The notification
     * @returns Parameters for translating the notification's timestamp
     */
    public getDateTranslationParams(notification: CourseNotification): Record<string, string | number> {
        const now = dayjs();
        const date = notification.creationDate!;
        const diffHours = Math.round(now.diff(date, 'hour', true));

        return {
            hours: diffHours,
            hour: date.format('HH'),
            minute: date.format('mm'),
            day: date.format('dddd').toLowerCase(),
            date: date.format('DD.MM.YYYY'),
        };
    }

    /**
     * Adds a notification to the course map if it doesn't already exist.
     *
     * @param courseId - The ID of the course
     * @param notification - The notification to add
     * @param prepend - Whether to add the notification to the beginning of the array
     */
    private addNotificationIfNotDuplicate(courseId: number, notification: CourseNotification, prepend: boolean): boolean {
        let notificationDuplicate = false;

        for (const existingNotification of this.courseNotificationMap[courseId]) {
            if (existingNotification.notificationId === notification.notificationId) {
                notificationDuplicate = true;
                break;
            }
        }

        if (!notificationDuplicate) {
            if (prepend) {
                this.courseNotificationMap[courseId].unshift(notification);
            } else {
                this.courseNotificationMap[courseId].push(notification);
            }
        }

        return notificationDuplicate;
    }

    /**
     * Converts server response notification data to the expected format.
     * Parses dates and enums, and extracts metadata from parameters.
     *
     * @param res - The HTTP response from the server
     * @returns The processed HTTP response
     */
    private convertResponseFromServer(res: HttpResponse<CourseNotificationPage>): HttpResponse<CourseNotificationPage> {
        if (res.body && res.body.content) {
            res.body.content.forEach((notification) => {
                notification.creationDate = convertDateFromServer(notification.creationDate);
                notification.category = CourseNotificationCategory[notification.category as unknown as keyof typeof CourseNotificationCategory];
                notification.status = CourseNotificationViewingStatus[notification.status as unknown as keyof typeof CourseNotificationViewingStatus];
                if (notification.parameters && notification.parameters['courseTitle']) {
                    notification.courseName = notification.parameters['courseTitle'] as string;
                }
                if (notification.parameters && notification.parameters['courseIconUrl']) {
                    notification.courseIconUrl = notification.parameters['courseIconUrl'] as string;
                }
            });
        }
        return res;
    }
}
