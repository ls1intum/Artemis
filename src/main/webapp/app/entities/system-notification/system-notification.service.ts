import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { Router } from '@angular/router';
import { SystemNotification } from 'app/entities/system-notification/system-notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { NotificationService } from 'app/entities/notification';

type EntityResponseType = HttpResponse<SystemNotification>;
type EntityArrayResponseType = HttpResponse<SystemNotification[]>;

@Injectable({ providedIn: 'root' })
export class SystemNotificationService {
    public resourceUrl = SERVER_API_URL + 'api/system-notifications';

    constructor(private router: Router, private http: HttpClient, private accountService: AccountService, private notificationService: NotificationService) {}

    create(notification: SystemNotification): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(notification);
        return this.http
            .post<SystemNotification>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(notification: SystemNotification): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(notification);
        return this.http
            .put<SystemNotification>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<SystemNotification>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<SystemNotification[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    getActiveNotification(): Observable<SystemNotification | null> {
        // If the user is not authenticated we do an explicit request for an active system notification.
        if (!this.accountService.isAuthenticated()) {
            return this.http
                .get<SystemNotification>(`${this.resourceUrl}/active-notification`, { observe: 'response' })
                .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)))
                .pipe(map(res => res.body));
        }
        return this.notificationService.getRecentSystemNotification();
    }

    protected convertDateFromClient(notification: SystemNotification): SystemNotification {
        const copy: SystemNotification = Object.assign({}, notification, {
            notificationDate:
                notification.notificationDate != null && moment(notification.notificationDate).isValid() ? moment(notification.notificationDate).toISOString(true) : null,
            expireDate: notification.expireDate != null && moment(notification.expireDate).isValid() ? moment(notification.expireDate).toISOString(true) : null,
        });
        return copy;
    }

    convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.notificationDate = res.body.notificationDate != null ? moment(res.body.notificationDate) : null;
            res.body.expireDate = res.body.expireDate != null ? moment(res.body.expireDate) : null;
        }
        return res;
    }

    convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((notification: SystemNotification) => {
                notification.notificationDate = notification.notificationDate != null ? moment(notification.notificationDate) : null;
                notification.expireDate = notification.expireDate != null ? moment(notification.expireDate) : null;
            });
        }
        return res;
    }
}
