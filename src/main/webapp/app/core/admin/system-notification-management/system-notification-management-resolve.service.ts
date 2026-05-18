import { HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { SystemNotification, SystemNotificationDTO } from 'app/core/shared/entities/system-notification.model';
import { filter, map } from 'rxjs/operators';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';

@Injectable({ providedIn: 'root' })
export class SystemNotificationManagementResolve implements Resolve<SystemNotificationDTO> {
    private service = inject(SystemNotificationService);

    /**
     * Resolves the route and initializes system notification from id route param
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['id']) {
            return this.service.find(parseInt(route.params['id'], 10)).pipe(
                filter((response: HttpResponse<SystemNotificationDTO>) => response.ok),
                map((response: HttpResponse<SystemNotificationDTO>) => response.body!),
            );
        }
        return new SystemNotification();
    }
}
