import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { map } from 'rxjs/operators';
import { createRequestOption } from 'app/shared/util/request.util';
import { Router } from '@angular/router';
import { SystemNotification } from 'app/entities/system-notification.model';

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
        const copy = this.convertDateFromClient(notification);
        return this.http.post<SystemNotification>(this.resourceUrl, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Update a notification on the server using a PUT request.
     * @param notification The notification to update.
     */
    update(notification: SystemNotification): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(notification);
        return this.http.put<SystemNotification>(this.resourceUrl, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * Find a notification on the server using a GET request.
     * @param id The id of the notification to get.
     */
    find(id: number): Observable<EntityResponseType> {
        return this.http.get<SystemNotification>(`${this.resourceUrl}/${id}`, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
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
        return this.http
            .get<SystemNotification>(`${this.resourceUrl}/active-notification`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)))
            .pipe(map((res) => res.body));
    }

    /**
     * Convert notification dates from client format to ISO format.
     * @param {SystemNotification} notification The notification to format.
     * @return {SystemNotification} A copy of notification with formatted dates.
     */
    protected convertDateFromClient(notification: SystemNotification): SystemNotification {
        const copy: SystemNotification = Object.assign({}, notification, {
            notificationDate: notification.notificationDate && dayjs(notification.notificationDate).isValid() ? dayjs(notification.notificationDate).toISOString() : undefined,
            expireDate: notification.expireDate && dayjs(notification.expireDate).isValid() ? dayjs(notification.expireDate).toISOString() : undefined,
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
            res.body.notificationDate = res.body.notificationDate ? dayjs(res.body.notificationDate) : undefined;
            res.body.expireDate = res.body.expireDate ? dayjs(res.body.expireDate) : undefined;
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
                notification.notificationDate = notification.notificationDate ? dayjs(notification.notificationDate) : undefined;
                notification.expireDate = notification.expireDate ? dayjs(notification.expireDate) : undefined;
            });
        }
        return res;
    }
}
