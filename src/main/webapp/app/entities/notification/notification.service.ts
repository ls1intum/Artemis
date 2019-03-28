import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_FORMAT } from 'app/shared/constants/input.constants';
import { map } from 'rxjs/operators';
import { Notification } from 'app/entities/notification';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { AccountService, JhiWebsocketService, User } from 'app/core';
import { Router } from '@angular/router';
import { Course } from 'app/entities/course';

type EntityResponseType = HttpResponse<Notification>;
type EntityArrayResponseType = HttpResponse<Notification[]>;

@Injectable({ providedIn: 'root' })
export class NotificationService {
    public resourceUrl = SERVER_API_URL + 'api/notifications';

    constructor(private jhiWebsocketService: JhiWebsocketService,
                private router: Router,
                private http: HttpClient,
                private accountService: AccountService) {
    }

    create(notification: Notification): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(notification);
        return this.http
            .post<Notification>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(notification: Notification): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(notification);
        return this.http
            .put<Notification>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<Notification>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Notification[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    getRecentNotificationsForUser(): Observable<EntityArrayResponseType> {
        return this.http
            .get<Notification[]>(`${this.resourceUrl}/for-user`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    protected convertDateFromClient(notification: Notification): Notification {
        const copy: Notification = Object.assign({}, notification, {
            notificationDate:
                notification.notificationDate != null && notification.notificationDate.isValid()
                    ? notification.notificationDate.toJSON()
                    : null
        });
        return copy;
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
    private userChannel: string;

    subscribeUserNotifications(): Promise<any> {
        return new Promise(((resolve, reject) => {
            this.accountService.identity().then((account: User) => {
                this.jhiWebsocketService.subscribe(`topic/user/${account.id}/singleUser`);
                resolve()
            }).catch(error => reject(error));
        }));
    }

    onUserNotification(): Observable<any> {
        return this.jhiWebsocketService.receive(this.userChannel);
    }

    subscribeGroupNotification(course: Course) {
        if (this.accountService.hasAnyAuthorityDirect(['ROLE_USER'])) {
            this.jhiWebsocketService.subscribe(`topic/course/${course.id}/student`);
        }
        if (this.accountService.hasAnyAuthorityDirect(['ROLE_TA'])) {
            this.jhiWebsocketService.subscribe(`topic/course/${course.id}/tutor`);
        }
        if (this.accountService.hasAnyAuthorityDirect(['ROLE_INSTRUCTOR'])) {
            this.jhiWebsocketService.subscribe(`topic/course/${course.id}/instructor`);
        }

    }

    interpretNotification(notification: Notification): void {
        console.log("STARTING NOTIFICATION", notification)
        console.log("STARTING NOTIFICATION", notification.target)
        this.formatTarget(notification.target)
    }

    formatTarget(target: object) {
        // entityName
        // entityId
        // courseId
        // page
        this.router.navigate(['overview', target.course, 'exercises', target.id])
    }

}
