import { Injectable, inject } from '@angular/core';
import { faComments } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { CourseNotification } from 'app/entities/course-notification/course-notification';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { CourseNotificationInfo } from 'app/entities/course-notification/course-notification-info';
import { CourseNotificationPage } from 'app/entities/course-notification/course-notification-page';
import { convertDateFromServer } from 'app/utils/date.utils';
import { CourseNotificationCategory } from 'app/entities/course-notification/course-notification-category';
import { CourseNotificationViewingStatus } from 'app/entities/course-notification/course-notification-viewing-status';
import { CourseNotificationChannel } from 'app/entities/course-notification/course-notification-channel';

@Injectable({
    providedIn: 'root',
})
export class CourseNotificationService {
    public static readonly NOTIFICATION_TYPE_ICON_MAP = {
        newPostNotification: faComments,
    };

    public static readonly DISABLE_NOTIFICATION_CHANNEL_TYPES: Record<string, Array<CourseNotificationChannel>> = {
        newPostNotification: [CourseNotificationChannel.EMAIL],
    };

    private readonly apiEndpoint = '/api/communication/notification/';
    public readonly pageSize = 10;

    private http = inject(HttpClient);

    private courseNotificationMap: Record<number, CourseNotification[]> = {};
    private courseNotificationPageMap: Record<number, boolean> = {};
    private notificationSubject = new BehaviorSubject<Record<number, CourseNotification[]>>({});
    private courseNotificationCountMap: Record<number, number> = {};
    private notificationCountSubject = new BehaviorSubject<Record<number, number>>({});

    public notificationCount$: Observable<Record<number, number>> = this.notificationCountSubject.asObservable();
    public notifications$: Observable<Record<number, CourseNotification[]>> = this.notificationSubject.asObservable();

    constructor() {
        this.notificationSubject.next(this.courseNotificationMap);
        this.notificationCountSubject.next(this.courseNotificationCountMap);
    }

    public getInfo(): Observable<HttpResponse<CourseNotificationInfo>> {
        return this.http.get<CourseNotificationInfo>(this.apiEndpoint + '/info', { observe: 'response' });
    }

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

    public setNotificationStatus(courseId: number, notificationIds: number[], statusType: CourseNotificationViewingStatus): void {
        this.http
            .put(this.apiEndpoint + courseId + '/status', {
                notificationIds: notificationIds,
                statusType: statusType,
            })
            .subscribe();
    }

    public archiveAll(courseId: number): void {
        this.http.put<void>(this.apiEndpoint + courseId + '/archive-all', {}).subscribe();
    }

    public archiveAllInMap(courseId: number): void {
        this.courseNotificationMap[courseId] = [];
        this.updateNotificationCountMap(courseId, 0);
        this.notifyNotificationSubscribers();
    }

    public setNotificationStatusInMap(courseId: number, notificationIds: number[], statusType: CourseNotificationViewingStatus) {
        // This will set the notifications to seen on the frontend.
        for (let i = 0; i < this.courseNotificationMap[courseId].length; i++) {
            if (notificationIds.includes(this.courseNotificationMap[courseId][i].notificationId!)) {
                this.courseNotificationMap[courseId][i].status = statusType;
            }
        }

        this.notifyNotificationSubscribers();
    }

    public addNotification(courseId: number, notification: CourseNotification) {
        if (!this.courseNotificationMap[courseId]) {
            this.courseNotificationMap[courseId] = [];
        }
        this.addNotificationIfNotDuplicate(courseId, notification, true);
        this.notifyNotificationSubscribers();
        this.incrementNotificationCount(courseId);
    }

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

    public updateNotificationCountMap(courseId: number, count: number) {
        if (this.courseNotificationCountMap[courseId] === count) {
            return;
        }

        this.courseNotificationCountMap[courseId] = count;
        this.notifyCountSubscribers();
    }

    public incrementNotificationCount(courseId: number) {
        if (this.courseNotificationCountMap[courseId] === undefined) {
            this.courseNotificationCountMap[courseId] = 0;
        }

        this.courseNotificationCountMap[courseId] = this.courseNotificationCountMap[courseId] + 1;
        this.notifyCountSubscribers();
    }

    public decreaseNotificationCountBy(courseId: number, count: number) {
        if (this.courseNotificationCountMap[courseId] === undefined) {
            return;
        }

        this.courseNotificationCountMap[courseId] = this.courseNotificationCountMap[courseId] - count;
        this.notifyCountSubscribers();
    }

    private notifyCountSubscribers(): void {
        this.notificationCountSubject.next({ ...this.courseNotificationCountMap });
    }

    public getNotificationCountForCourse$(courseId: number): Observable<number> {
        return new Observable<number>((observer) => {
            const subscription = this.notificationCount$.subscribe((map) => {
                observer.next(map[courseId] || 0);
            });

            return () => subscription.unsubscribe();
        });
    }

    private notifyNotificationSubscribers(): void {
        this.notificationSubject.next({ ...this.courseNotificationMap });
    }

    public getNotificationsForCourse$(courseId: number): Observable<CourseNotification[]> {
        return new Observable<CourseNotification[]>((observer) => {
            const subscription = this.notifications$.subscribe((map) => {
                observer.next(map[courseId] || 0);
            });

            return () => subscription.unsubscribe();
        });
    }

    public getIconFromType(type: string | undefined) {
        if (type === undefined || !CourseNotificationService.NOTIFICATION_TYPE_ICON_MAP.hasOwnProperty(type)) {
            return faComments;
        }

        return CourseNotificationService.NOTIFICATION_TYPE_ICON_MAP[type as keyof typeof CourseNotificationService.NOTIFICATION_TYPE_ICON_MAP];
    }

    public getDateTranslationKey(notification: CourseNotification): string {
        const now = dayjs();
        const date = notification.creationDate!;
        const diffMinutes = now.diff(date, 'minute');
        const diffHours = now.diff(date, 'hour');
        const isToday = now.format('YYYY-MM-DD') === date.format('YYYY-MM-DD');
        const isYesterday = now.subtract(1, 'day').format('YYYY-MM-DD') === date.format('YYYY-MM-DD');
        const isSameWeek = now.startOf('week').isBefore(date) && now.endOf('week').isAfter(date);

        let key = '';

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

    private addNotificationIfNotDuplicate(courseId: number, notification: CourseNotification, prepend: boolean) {
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
    }

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
                    notification.courseIconUrl = notification.parameters['courseIconUrl'] as string | null;
                }
            });
        }
        return res;
    }
}
