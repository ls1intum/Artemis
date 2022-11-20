import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Router } from '@angular/router';
import { SystemNotification } from 'app/entities/system-notification.model';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';

type EntityResponseType = HttpResponse<SystemNotification>;

@Injectable({ providedIn: 'root' })
export class AdminSystemNotificationService {
    public resourceUrl = SERVER_API_URL + 'api/admin/system-notifications';

    constructor(private router: Router, private http: HttpClient, private systemNotificationService: SystemNotificationService) {}

    /**
     * Create a notification on the server using a POST request.
     * @param notification The notification to create.
     */
    create(notification: SystemNotification): Observable<EntityResponseType> {
        const copy = this.systemNotificationService.convertSystemNotificationDatesFromClient(notification);
        return this.http
            .post<SystemNotification>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.systemNotificationService.convertSystemNotificationResponseDatesFromServer(res)));
    }

    /**
     * Update a notification on the server using a PUT request.
     * @param notification The notification to update.
     */
    update(notification: SystemNotification): Observable<EntityResponseType> {
        const copy = this.systemNotificationService.convertSystemNotificationDatesFromClient(notification);
        return this.http
            .put<SystemNotification>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.systemNotificationService.convertSystemNotificationResponseDatesFromServer(res)));
    }

    /**
     * Delete a notification on the server using a DELETE request.
     * @param systemNotificationId The id of the notification to delete.
     */
    delete(systemNotificationId: number): Observable<HttpResponse<void>> {
        return this.http.delete<any>(`${this.resourceUrl}/${systemNotificationId}`, { observe: 'response' });
    }
}
