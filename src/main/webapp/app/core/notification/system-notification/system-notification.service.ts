import { HttpResponse } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { SystemNotification } from 'app/admin/system-notification-management/system-notification.model';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { AdminSystemNotificationApi } from 'app/openapi/api/admin-system-notification-api';
import { PublicSystemNotificationApi } from 'app/openapi/api/public-system-notification-api';
import { SystemNotification as SystemNotificationDto } from 'app/openapi/model/system-notification';
import { Observable, map } from 'rxjs';

/** UI paging options use Spring's zero-based page index and ordered, repeated sort criteria. */
type SystemNotificationQueryOptions = { page?: number; size?: number; sort?: string[] };

/** Keeps instant conversion at the boundary; all REST transport is generated. */
@Service()
export class SystemNotificationService {
    private api = inject(AdminSystemNotificationApi);
    private publicApi = inject(PublicSystemNotificationApi);

    find(systemNotificationId: number): Observable<SystemNotification> {
        return this.api.getSystemNotification(systemNotificationId).pipe(map((notification) => this.fromServer(notification)));
    }

    query(req?: SystemNotificationQueryOptions): Observable<HttpResponse<SystemNotification[]>> {
        return this.api
            .getAllSystemNotifications(req?.page, req?.size, req?.sort)
            .pipe(map((response) => response.clone({ body: response.body?.map((notification) => this.fromServer(notification)) ?? null })));
    }

    /** This endpoint is public and must also load before authentication. */
    getActiveNotifications(): Observable<SystemNotification[]> {
        return this.publicApi.getActiveAndFutureSystemNotifications().pipe(map((notifications) => (notifications ?? []).map((notification) => this.fromServer(notification))));
    }

    /** REST and WebSocket messages share the server DTO. Never mutate the wire object. */
    fromServer(notification: SystemNotificationDto): SystemNotification {
        return {
            id: notification.id,
            title: notification.title,
            text: notification.text,
            type: notification.type,
            notificationDate: convertDateStringFromServer(notification.notificationDate),
            expireDate: convertDateStringFromServer(notification.expireDate),
        };
    }

    fromServerResponse(response: HttpResponse<SystemNotificationDto>): HttpResponse<SystemNotification> {
        return response.clone({ body: response.body ? this.fromServer(response.body) : null });
    }
}
