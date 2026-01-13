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
import { SystemNotification } from 'app/core/shared/entities/system-notification.model';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';

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
        const expectedNotification = new SystemNotification();
        const httpResponse = new HttpResponse<SystemNotification>({ body: expectedNotification });
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
        const existingNotification = new SystemNotification();
        const httpResponse = new HttpResponse<SystemNotification>({ body: existingNotification });
        const routeSnapshot = { params: { id: undefined } } as unknown as ActivatedRouteSnapshot;

        vi.spyOn(systemNotificationService, 'find').mockReturnValue(of(httpResponse));

        const result = resolver.resolve(routeSnapshot);

        // Should return a new SystemNotification instance, not the mocked one
        expect(result).not.toBe(existingNotification);
        expect(result).toBeInstanceOf(SystemNotification);
        expect(systemNotificationService.find).not.toHaveBeenCalled();
    });
});
