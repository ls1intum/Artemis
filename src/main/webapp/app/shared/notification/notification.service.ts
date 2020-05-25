import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared/util/request-util';
import { Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { User } from 'app/core/user/user.model';
import { GroupNotification, GroupNotificationType } from 'app/entities/group-notification.model';
import { Notification, NotificationType } from 'app/entities/notification.model';
import { Course } from 'app/entities/course.model';

@Injectable({ providedIn: 'root' })
export class NotificationService {
    public resourceUrl = SERVER_API_URL + 'api/notifications';
    notificationObserver: BehaviorSubject<Notification | null>;
    subscribedTopics: string[] = [];
    cachedNotifications: Observable<HttpResponse<Notification[]>>;

    constructor(
        private jhiWebsocketService: JhiWebsocketService,
        private router: Router,
        private http: HttpClient,
        private accountService: AccountService,
        private translateService: TranslateService,
    ) {
        this.initNotificationObserver();
    }

    /**
     * Query all notifications.
     * @param req request options
     * @return Observable<HttpResponse<Notification[]>>
     */
    query(req?: any): Observable<HttpResponse<Notification[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<Notification[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: HttpResponse<Notification[]>) => this.convertDateArrayFromServer(res)));
    }

    /**
     * Delete notification by id.
     * @param {number} id
     * @return Observable<HttpResponse<any>>
     */
    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    protected convertDateFromClient(notification: Notification): Notification {
        return Object.assign({}, notification, {
            notificationDate: notification.notificationDate != null && notification.notificationDate.isValid() ? notification.notificationDate.toJSON() : null,
        });
    }

    protected convertDateFromServer(res: HttpResponse<Notification>): HttpResponse<Notification> {
        if (res.body) {
            res.body.notificationDate = res.body.notificationDate != null ? moment(res.body.notificationDate) : null;
        }
        return res;
    }

    protected convertDateArrayFromServer(res: HttpResponse<Notification[]>): HttpResponse<Notification[]> {
        if (res.body) {
            res.body.forEach((notification: Notification) => {
                notification.notificationDate = notification.notificationDate != null ? moment(notification.notificationDate) : null;
            });
        }
        return res;
    }

    /**
     * Subscribe to notifications for user.
     * @return Promise<any>
     */
    subscribeUserNotifications(): Promise<any> {
        return new Promise((resolve, reject) => {
            this.accountService
                .identity()
                .then((user: User) => {
                    if (user) {
                        const userTopic = `/topic/user/${user.id}/notifications`;
                        if (!this.subscribedTopics.includes(userTopic)) {
                            this.subscribedTopics.push(userTopic);
                            this.jhiWebsocketService.subscribe(userTopic);
                            this.jhiWebsocketService.receive(userTopic).subscribe((notification: Notification) => {
                                this.notificationObserver.next(notification);
                            });
                            resolve();
                        }
                    } else {
                        reject('no User');
                    }
                })
                .catch((error) => reject(error));
        });
    }

    /**
     * Subscribe to websocket for course and role.
     * @param {Course} course
     */
    public handleCourseNotifications(course: Course): void {
        let courseTopic = `/topic/course/${course.id}/${GroupNotificationType.STUDENT}`;
        if (this.accountService.isAtLeastInstructorInCourse(course)) {
            courseTopic = `/topic/course/${course.id}/${GroupNotificationType.INSTRUCTOR}`;
        } else if (this.accountService.isAtLeastTutorInCourse(course)) {
            courseTopic = `/topic/course/${course.id}/${GroupNotificationType.TA}`;
        }
        if (!this.subscribedTopics.includes(courseTopic)) {
            this.subscribedTopics.push(courseTopic);
            this.jhiWebsocketService.subscribe(courseTopic);
            this.jhiWebsocketService.receive(courseTopic).subscribe((notification: Notification) => {
                this.notificationObserver.next(notification);
            });
        }
    }

    /**
     * handleCourseNotification for each course of array
     * @param {Course[]} courses
     */
    public handleCoursesNotifications(courses: Course[]): void {
        courses.forEach((course: Course) => {
            this.handleCourseNotifications(course);
        });
    }

    /**
     * Get the notificationObserver.
     * @return {BehaviorSubject<Notification}
     */
    subscribeToSocketMessages(): BehaviorSubject<Notification | null> {
        return this.notificationObserver;
    }

    /**
     * Navigate to notification target.
     * @param {GroupNotification} notification
     */
    navigateToTarget(notification: Notification): void {
        switch (notification.notificationType) {
            case NotificationType.SINGLE:
            case NotificationType.GROUP:
                const target = JSON.parse(notification.target);
                let courseId = target.course;
                if (notification instanceof GroupNotification) {
                    courseId = notification.course.id;
                }
                this.router.navigate([target.mainPage, courseId, target.entity, target.id]);
                break;
            case NotificationType.SINGLE_NEW_ANSWER_FOR_EXERCISE:
            case NotificationType.GROUP_EXERCISE_CREATED:
            case NotificationType.GROUP_EXERCISE_PRACTICE:
            case NotificationType.GROUP_EXERCISE_STARTED:
            case NotificationType.GROUP_EXERCISE_UPDATED:
            case NotificationType.GROUP_NEW_ANSWER_FOR_EXERCISE:
            case NotificationType.GROUP_NEW_QUESTION_FOR_EXERCISE:
                this.router.navigate(['courses', notification.course.id, 'exercises', notification.notificationTarget.exercise.id]);
                break;
            case NotificationType.SINGLE_NEW_ANSWER_FOR_LECTURE:
            case NotificationType.GROUP_ATTACHMENT_UPDATED:
            case NotificationType.GROUP_NEW_ANSWER_FOR_LECTURE:
            case NotificationType.GROUP_NEW_QUESTION_FOR_LECTURE:
                this.router.navigate(['courses', notification.course.id, 'lectures', notification.notificationTarget.lecture.id]);
                break;
        }
    }

    /**
     * Returns the title stored with the given notification if there is a title.
     * Otherwise, returns the notification title for the given notification by evaluating the appropriate translation string for the notification type.
     * @param notification {Notification}
     */
    notificationTitle(notification: Notification): string {
        if (notification.title) {
            return notification.title;
        }
        const typeParts = notification.notificationType.split('-');
        return this.translateService.instant('artemisApp.notification.' + typeParts[0] + '.' + typeParts[1] + '.title');
    }

    /**
     * Set new notification observer.
     */
    private initNotificationObserver(): void {
        this.notificationObserver = new BehaviorSubject<Notification | null>(null);
    }

    /**
     * Init new observer for notifications and reset topics.
     */
    public cleanUp(): void {
        this.cachedNotifications = new Observable<HttpResponse<Notification[]>>();
        this.initNotificationObserver();
        this.subscribedTopics = [];
    }
}
