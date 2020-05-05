import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared/util/request-util';
import { Router } from '@angular/router';
import { SystemNotification } from 'app/entities/system-notification.model';
import { AccountService } from 'app/core/auth/account.service';
import { NotificationService } from 'app/overview/notification/notification.service';

type EntityResponseType = HttpResponse<SystemNotification>;
type EntityArrayResponseType = HttpResponse<SystemNotification[]>;

@Injectable({ providedIn: 'root' })
export class SystemNotificationService {
    public resourceUrl = SERVER_API_URL + 'api/system-notifications';

    constructor(private router: Router, private http: HttpClient, private accountService: AccountService, private notificationService: NotificationService) {}

    /**
     * Create a notification on the server using a POST request.
     * @param notification The notification to create.
     */
    create(notification: SystemNotification): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(notification);
        return this.http
            .post<SystemNotification>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Update a notification on the server using a PUT request.
     * @param notification The notification to update.
     */
    update(notification: SystemNotification): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(notification);
        return this.http
            .put<SystemNotification>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Find a notification on the server using a GET request.
     * @param id The id of the notification to get.
     */
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

    /**
     * Delete a notification on the server using a DELETE request.
     * @param id The id of the notification to delete.
     */
    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    /**
     * If the user is not authenticated we do an explicit request for an active system notification. Otherwise get recent system notifications.
     */
    getActiveNotification(): Observable<SystemNotification | null> {
        if (!this.accountService.isAuthenticated()) {
            return this.http
                .get<SystemNotification>(`${this.resourceUrl}/active-notification`, { observe: 'response' })
                .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)))
                .pipe(map((res) => res.body));
        }
        return this.notificationService.getRecentSystemNotification();
    }

    /**
     * Convert notification dates from client format to ISO format.
     * @param {SystemNotification} notification The notification to format.
     * @return {SystemNotification} A copy of notification with formatted dates.
     */
    protected convertDateFromClient(notification: SystemNotification): SystemNotification {
        const copy: SystemNotification = Object.assign({}, notification, {
            notificationDate:
                notification.notificationDate != null && moment(notification.notificationDate).isValid() ? moment(notification.notificationDate).toISOString(true) : null,
            expireDate: notification.expireDate != null && moment(notification.expireDate).isValid() ? moment(notification.expireDate).toISOString(true) : null,
        });
        return copy;
    }

    /**
     * Convert server response dates from server format to ISO format.
     * @param {EntityResponseType} res The server response to format.
     * @return {EntityResponseType} The server response with formatted dates.
     */
    convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.notificationDate = res.body.notificationDate != null ? moment(res.body.notificationDate) : null;
            res.body.expireDate = res.body.expireDate != null ? moment(res.body.expireDate) : null;
        }
        return res;
    }

    /**
     * Convert server response dates from server format to ISO format for an array of responses.
     * @param {EntityResponseType} res The array of server responses to format.
     * @return {EntityResponseType} The array of server responses with formatted dates.
     */
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
