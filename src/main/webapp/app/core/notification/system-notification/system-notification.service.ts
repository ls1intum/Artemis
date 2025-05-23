import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { SystemNotification } from 'app/core/shared/entities/system-notification.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { convertDateFromClient, convertDateFromServer } from 'app/shared/util/date.utils';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

type EntityResponseType = HttpResponse<SystemNotification>;
type EntityArrayResponseType = HttpResponse<SystemNotification[]>;

@Injectable({ providedIn: 'root' })
export class SystemNotificationService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/communication/system-notifications';
    public publicResourceUrl = 'api/core/public/system-notifications';

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
     * Fetch active and future notifications from the server.
     */
    getActiveNotifications(): Observable<SystemNotification[]> {
        return this.http
            .get<SystemNotification[]>(`${this.publicResourceUrl}/active`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertSystemNotificationArrayResponseDatesFromServer(res)))
            .pipe(map((res) => res.body || []));
    }

    /**
     * Convert notification dates from client format to ISO format.
     * @param {SystemNotification} notification The notification to format.
     * @return {SystemNotification} A copy of notification with formatted dates.
     */
    convertSystemNotificationDatesFromClient(notification: SystemNotification): SystemNotification {
        return Object.assign({}, notification, {
            notificationDate: convertDateFromClient(notification.notificationDate),
            expireDate: convertDateFromClient(notification.expireDate),
        });
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
