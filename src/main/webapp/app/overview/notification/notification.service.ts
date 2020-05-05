import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
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
import { SystemNotification } from 'app/entities/system-notification.model';
import { Notification, NotificationType } from 'app/entities/notification.model';
import { Course } from 'app/entities/course.model';

type EntityResponseType = HttpResponse<Notification>;
type EntityArrayResponseType = HttpResponse<Notification[]>;

@Injectable({ providedIn: 'root' })
export class NotificationService {
    public resourceUrl = SERVER_API_URL + 'api/notifications';
    notificationObserver: BehaviorSubject<Notification | null>;
    subscribedTopics: string[] = [];
    cachedNotifications: Observable<EntityArrayResponseType>;

    constructor(private jhiWebsocketService: JhiWebsocketService, private router: Router, private http: HttpClient, private accountService: AccountService) {
        this.initNotificationObserver();
    }

    /**
     * create new notification
     * @param {Notification} notification
     * @return Observable<EntityResponseType>
     */
    create(notification: Notification): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(notification);
        return this.http
            .post<Notification>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * update notification
     * @param {Notification} notification
     * @return Observable<EntityResponseType>
     */
    update(notification: Notification): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(notification);
        return this.http
            .put<Notification>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * find by id
     * @param {number} id
     * @return Observable<EntityResponseType>
     */
    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<Notification>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * find notifications for query
     * @param {any} req
     * @return Observable<EntityArrayResponseType>
     */
    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Notification[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    /**
     * delete by id
     * @param {number} id
     * @return Observable<HttpResponse<any>>
     */
    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    /**
     * get recent notifications
     * @return Observable<EntityArrayResponseType>
     */
    getRecentNotifications(): Observable<EntityArrayResponseType> {
        if (!this.cachedNotifications) {
            this.cachedNotifications = this.http
                .get<Notification[]>(`${this.resourceUrl}/recent-for-user`, { observe: 'response' })
                .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
        }
        return this.cachedNotifications;
    }

    /**
     * get recent notifications for user
     * @return Observable<Notification[]>
     */
    getRecentNotificationsForUser(): Observable<Notification[]> {
        return this.getRecentNotifications().pipe(map((res: EntityArrayResponseType) => this.filterUserAndGroupNotifications(res)));
    }

    /**
     * get recent system notifications
     * @return Observable<SystemNotification>
     */
    getRecentSystemNotification(): Observable<SystemNotification> {
        return this.getRecentNotifications().pipe(map((res: EntityArrayResponseType) => this.filterSystemNotification(res)!));
    }

    protected convertDateFromClient(notification: Notification): Notification {
        return Object.assign({}, notification, {
            notificationDate: notification.notificationDate != null && notification.notificationDate.isValid() ? notification.notificationDate.toJSON() : null,
        });
    }

    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.notificationDate = res.body.notificationDate != null ? moment(res.body.notificationDate) : null;
        }
        return res;
    }

    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((notification: Notification) => {
                notification.notificationDate = notification.notificationDate != null ? moment(notification.notificationDate) : null;
            });
        }
        return res;
    }

    protected filterUserAndGroupNotifications(res: EntityArrayResponseType): Notification[] {
        let notifications: Notification[] = [];
        if (res.body) {
            notifications = res.body.filter((notification: Notification) => {
                return [NotificationType.GROUP, NotificationType.SINGLE].includes(notification.notificationType);
            });
        }
        return notifications;
    }

    protected filterSystemNotification(res: EntityArrayResponseType): SystemNotification | null {
        let systemNotification: SystemNotification | null = null;
        if (res.body) {
            const receivedSystemNotifications = res.body.filter((el) => el.notificationType === NotificationType.SYSTEM);
            if (receivedSystemNotifications && receivedSystemNotifications.length > 0) {
                systemNotification = receivedSystemNotifications[0] as SystemNotification;
            }
        }
        return systemNotification;
    }

    /**
     * subscribe to notifications for user
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
     * subscribe to websocket for course and role
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
     * get the notificationObserver
     * @return {BehaviorSubject<Notification}
     */
    subscribeToSocketMessages(): BehaviorSubject<Notification | null> {
        return this.notificationObserver;
    }

    /**
     * navigate to notification target
     * @param {GroupNotification} notification
     */
    interpretNotification(notification: GroupNotification): void {
        const target = JSON.parse(notification.target);
        const courseId = target.course || notification.course.id;
        this.router.navigate([target.mainPage, courseId, target.entity, target.id]);
    }

    /**
     * set new notification observer
     */
    private initNotificationObserver(): void {
        this.notificationObserver = new BehaviorSubject<Notification | null>(null);
    }

    /**
     * init new observer for notifications and reset topics
     */
    public cleanUp(): void {
        this.cachedNotifications = new Observable<EntityArrayResponseType>();
        this.initNotificationObserver();
        this.subscribedTopics = [];
    }
}
