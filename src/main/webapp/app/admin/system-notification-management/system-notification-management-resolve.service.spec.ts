import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { isObservable, of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot } from '@angular/router';
import { SystemNotificationManagementResolve } from 'app/admin/system-notification-management/system-notification-management-resolve.service';
import { SystemNotification } from 'app/admin/system-notification-management/system-notification.model';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';

describe('SystemNotificationManagementResolve', () => {
    let resolver: SystemNotificationManagementResolve;
    const find = vi.fn();

    beforeEach(() => {
        find.mockReset();
        TestBed.configureTestingModule({ providers: [{ provide: SystemNotificationService, useValue: { find } }] });
        resolver = TestBed.inject(SystemNotificationManagementResolve);
    });

    it('resolves the Dayjs model directly for the detail and editor routes', () => {
        const notification = new SystemNotification();
        notification.id = 1;
        find.mockReturnValue(of(notification));
        const route = new ActivatedRouteSnapshot();
        route.params = { id: '1' };
        const result = resolver.resolve(route);
        if (!isObservable(result)) {
            throw new Error('Existing notifications must resolve asynchronously');
        }
        let resolved: SystemNotification | undefined;
        result.subscribe((value) => (resolved = value));
        expect(resolved).toBe(notification);
        expect(find).toHaveBeenCalledExactlyOnceWith(1);
    });

    it('propagates a missing notification instead of resolving an empty editor model', () => {
        const error = new HttpErrorResponse({ status: 404 });
        find.mockReturnValue(throwError(() => error));
        const route = new ActivatedRouteSnapshot();
        route.params = { id: '99' };
        const result = resolver.resolve(route);
        if (!isObservable(result)) {
            throw new Error('Existing notifications must resolve asynchronously');
        }
        let failure: HttpErrorResponse | undefined;
        result.subscribe({ error: (value: HttpErrorResponse) => (failure = value) });
        expect(failure).toBe(error);
    });

    it('constructs a new UI notification when no id is provided', () => {
        const result = resolver.resolve(new ActivatedRouteSnapshot());
        expect(result).toBeInstanceOf(SystemNotification);
        expect(find).not.toHaveBeenCalled();
    });
});
