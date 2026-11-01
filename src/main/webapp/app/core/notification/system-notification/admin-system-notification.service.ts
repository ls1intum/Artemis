import { Service, inject } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { Observable, defer, map } from 'rxjs';
import { SystemNotification } from 'app/admin/system-notification-management/system-notification.model';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';
import { convertDateFromClient } from 'app/foundation/util/date.utils';
import { AdminSystemNotificationApi } from 'app/openapi/api/admin-system-notification-api';
import { SystemNotificationUpdate } from 'app/openapi/model/system-notification-update';

/** Adapts the editor's Dayjs model to the generated write contract. */
@Service()
export class AdminSystemNotificationService {
    private api = inject(AdminSystemNotificationApi);
    private systemNotificationService = inject(SystemNotificationService);

    create(notification: SystemNotification, sendMaintenanceEmail = false): Observable<HttpResponse<SystemNotification>> {
        return defer(() => this.api.createSystemNotification(this.toUpdate(notification), sendMaintenanceEmail ? true : undefined)).pipe(
            map((response) => this.systemNotificationService.fromServerResponse(response)),
        );
    }

    getMaintenanceRecipientsCount(): Observable<number> {
        return this.api.getMaintenanceRecipientsCount();
    }

    update(notification: SystemNotification): Observable<HttpResponse<SystemNotification>> {
        return defer(() => this.api.updateSystemNotification(this.toUpdate(notification))).pipe(map((response) => this.systemNotificationService.fromServerResponse(response)));
    }

    delete(systemNotificationId: number): Observable<HttpResponse<void>> {
        return this.api.deleteSystemNotification(systemNotificationId);
    }

    private toUpdate(notification: SystemNotification): SystemNotificationUpdate {
        const notificationDate = convertDateFromClient(notification.notificationDate);
        const expireDate = convertDateFromClient(notification.expireDate);
        // The editor requires both dates. Do not invent dates or hide an invalid model behind a cast.
        // defer delivers this failure through the normal save error callback, resetting the saving state.
        if (!notificationDate || !expireDate) {
            throw new Error('System notification requires both notification and expiration dates');
        }
        return {
            id: notification.id,
            title: notification.title,
            text: notification.text,
            type: notification.type,
            notificationDate,
            expireDate,
        };
    }
}
