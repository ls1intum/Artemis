import { ActivatedRouteSnapshot } from '@angular/router';
import { of } from 'rxjs';
import { SystemNotificationService } from 'app/shared/notification/system-notification/system-notification.service';
import { SystemNotificationManagementResolve } from 'app/admin/system-notification-management/system-notification-management-resolve.service';
import { SystemNotification } from 'app/entities/system-notification.model';
import { HttpResponse } from '@angular/common/http';

describe('SystemNotificationManagementResolveService', () => {
    let systemNotificationService: SystemNotificationService;
    let systemNotificationManagementResolve: SystemNotificationManagementResolve;

    beforeEach(() => {
        systemNotificationService = { find: jest.fn() } as any as SystemNotificationService;
        systemNotificationManagementResolve = new SystemNotificationManagementResolve(systemNotificationService);
    });

    it('should search for notification by id', () => {
        const toReturn = new HttpResponse<SystemNotification>({ body: new SystemNotification() });
        const spy = jest.spyOn(systemNotificationService, 'find').mockReturnValue(of(toReturn));

        let result = undefined;
        // @ts-ignore
        systemNotificationManagementResolve.resolve({ params: { id: '1' } } as any as ActivatedRouteSnapshot).subscribe((noti) => (result = noti));
        expect(result).toBe(toReturn.body);
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(1);
    });

    it('should return new notification if no id is given', () => {
        const toReturn = new HttpResponse<SystemNotification>({ body: new SystemNotification() });
        const spy = jest.spyOn(systemNotificationService, 'find').mockReturnValue(of(toReturn));

        const result = systemNotificationManagementResolve.resolve({ params: { id: undefined } } as any as ActivatedRouteSnapshot);
        expect(result).not.toBe(toReturn);
        expect(result).toBeInstanceOf(SystemNotification);
        expect(spy).toHaveBeenCalledTimes(0);
    });
});
