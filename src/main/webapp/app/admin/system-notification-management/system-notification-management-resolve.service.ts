import { Service, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { SystemNotification } from 'app/admin/system-notification-management/system-notification.model';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';

@Service()
export class SystemNotificationManagementResolve implements Resolve<SystemNotification> {
    private service = inject(SystemNotificationService);

    /**
     * Resolves the route and initializes system notification from id route param
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['id']) {
            return this.service.find(parseInt(route.params['id'], 10));
        }
        return new SystemNotification();
    }
}
