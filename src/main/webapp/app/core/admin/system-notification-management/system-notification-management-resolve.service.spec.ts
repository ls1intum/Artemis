/**
 * Vitest tests for SystemNotificationManagementResolve service.
 * Tests the route resolver that fetches system notification data based on ID parameter.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRouteSnapshot } from '@angular/router';

import { SystemNotificationManagementResolve } from 'app/core/admin/system-notification-management/system-notification-management-resolve.service';
import { SystemNotification, SystemNotificationDTO } from 'app/core/shared/entities/system-notification.model';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';
import dayJs from 'dayjs/esm';

describe('SystemNotificationManagementResolve', () => {
    setupTestBed({ zoneless: true });

    let systemNotificationService: SystemNotificationService;
    let resolver: SystemNotificationManagementResolve;

    /** Mock service with spy method */
    const mockSystemNotificationService = {
        find: vi.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [SystemNotificationManagementResolve, { provide: SystemNotificationService, useValue: mockSystemNotificationService }],
        }).compileComponents();

        systemNotificationService = TestBed.inject(SystemNotificationService);
        resolver = TestBed.inject(SystemNotificationManagementResolve);
    });

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should fetch notification by id when id parameter is provided', () => {
        const expectedNotification = {
            id: 1,
            notificationDate: dayJs(new Date('2023-01-01T00:00:00.000Z')),
            notificationText: 'Test notification',
            notificationType: 'INFO',
        };
        const httpResponse = new HttpResponse<SystemNotificationDTO>({ body: expectedNotification });
        const routeSnapshot = { params: { id: '1' } } as unknown as ActivatedRouteSnapshot;

        vi.spyOn(systemNotificationService, 'find').mockReturnValue(of(httpResponse));

        let result: SystemNotification | undefined;
        // @ts-ignore - resolve may return Observable or direct value
        resolver.resolve(routeSnapshot).subscribe((notification: SystemNotification) => (result = notification));

        expect(result).toBe(expectedNotification);
        expect(systemNotificationService.find).toHaveBeenCalledOnce();
        expect(systemNotificationService.find).toHaveBeenCalledWith(1);
    });

    it('should return new notification when id parameter is not provided', () => {
        const expectedNotificationWithoutId = {
            id: 1,
            notificationDate: dayJs(new Date('2023-01-01T00:00:00.000Z')),
            notificationText: 'Test notification',
            notificationType: 'INFO',
        };
        const httpResponse = new HttpResponse<SystemNotificationDTO>({ body: expectedNotificationWithoutId });
        const routeSnapshot = { params: { id: undefined } } as unknown as ActivatedRouteSnapshot;

        vi.spyOn(systemNotificationService, 'find').mockReturnValue(of(httpResponse));

        const result = resolver.resolve(routeSnapshot);

        // Should return a new SystemNotification instance, not the mocked one
        expect(result).not.toBe(expectedNotificationWithoutId);
        expect(result).toBeInstanceOf(SystemNotification);
        expect(systemNotificationService.find).not.toHaveBeenCalled();
    });
});
