import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { createRequestOption } from 'app/shared/util/request.util';
import { Router } from '@angular/router';
import { SystemNotification } from 'app/entities/system-notification.model';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';

type EntityResponseType = HttpResponse<SystemNotification>;
type EntityArrayResponseType = HttpResponse<SystemNotification[]>;

@Injectable({ providedIn: 'root' })
export class SystemNotificationService {
    public resourceUrl = SERVER_API_URL + 'api/system-notifications';

    constructor(private router: Router, private http: HttpClient) {}

    /**
     * Create a notification on the server using a POST request.
     * @param notification The notification to create.
     */
    create(notification: SystemNotification): Observable<EntityResponseType> {
        const copy = this.convertSystemNotificationDatesFromClient(notification);
        return this.http
            .post<SystemNotification>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertSystemNotificationResponseDatesFromServer(res)));
    }

    /**
     * Update a notification on the server using a PUT request.
     * @param notification The notification to update.
     */
    update(notification: SystemNotification): Observable<EntityResponseType> {
        const copy = this.convertSystemNotificationDatesFromClient(notification);
        return this.http
            .put<SystemNotification>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertSystemNotificationResponseDatesFromServer(res)));
    }

    /**
     * Find a notification on the server using a GET request.
     * @param systemNotificationId The id of the notification to get.
     */
    find(systemNotificationId: number): Observable<EntityResponseType> {
        return this.http
            .get<SystemNotification>(`${this.resourceUrl}/${systemNotificationId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertSystemNotificationResponseDatesFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<SystemNotification[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertSystemNotificationArrayResponseDatesFromServer(res)));
    }

    /**
     * Delete a notification on the server using a DELETE request.
     * @param systemNotificationId The id of the notification to delete.
     */
    delete(systemNotificationId: number): Observable<HttpResponse<void>> {
        return this.http.delete<any>(`${this.resourceUrl}/${systemNotificationId}`, { observe: 'response' });
    }

    /**
     * If the user is not authenticated we do an explicit request for an active system notification. Otherwise get recent system notifications.
     */
    getActiveNotification(): Observable<SystemNotification | null> {
        return this.http
            .get<SystemNotification>(`${this.resourceUrl}/active-notification`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertSystemNotificationResponseDatesFromServer(res)))
            .pipe(map((res) => res.body));
    }

    /**
     * Convert notification dates from client format to ISO format.
     * @param {SystemNotification} notification The notification to format.
     * @return {SystemNotification} A copy of notification with formatted dates.
     */
    protected convertSystemNotificationDatesFromClient(notification: SystemNotification): SystemNotification {
        const copy: SystemNotification = Object.assign({}, notification, {
            notificationDate: convertDateFromClient(notification.notificationDate),
            expireDate: convertDateFromClient(notification.expireDate),
        });
        return copy;
    }

    /**
     * Convert server response dates from server format to ISO format.
     * @param {EntityResponseType} res The server response to format.
     * @return {EntityResponseType} The server response with formatted dates.
     */
    convertSystemNotificationResponseDatesFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.notificationDate = convertDateFromServer(res.body.notificationDate);
            res.body.expireDate = convertDateFromServer(res.body.expireDate);
        }
        return res;
    }

    /**
     * Convert server response dates from server format to ISO format for an array of responses.
     * @param {EntityResponseType} res The array of server responses to format.
     * @return {EntityResponseType} The array of server responses with formatted dates.
     */
    convertSystemNotificationArrayResponseDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((notification: SystemNotification) => {
                notification.notificationDate = convertDateFromServer(notification.notificationDate);
                notification.expireDate = convertDateFromServer(notification.expireDate);
            });
        }
        return res;
    }
}
