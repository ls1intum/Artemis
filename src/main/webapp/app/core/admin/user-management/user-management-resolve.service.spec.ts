/**
 * Vitest tests for UserManagementResolve service.
 * Tests the route resolver that fetches user data based on login parameter.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { ActivatedRouteSnapshot } from '@angular/router';

import { UserManagementResolve } from 'app/core/admin/user-management/user-management-resolve.service';
import { User } from 'app/core/user/user.model';
import { AdminUserService } from 'app/core/user/shared/admin-user.service';

describe('UserManagementResolve', () => {
    setupTestBed({ zoneless: true });

    let adminUserService: AdminUserService;
    let resolve: UserManagementResolve;

    /** Mock service that provides a spy for the findUser method */
    const mockAdminUserService = {
        findUser: vi.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [UserManagementResolve, { provide: AdminUserService, useValue: mockAdminUserService }],
        }).compileComponents();

        adminUserService = TestBed.inject(AdminUserService);
        resolve = TestBed.inject(UserManagementResolve);
    });

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should fetch user by login when login parameter is provided', () => {
        const expectedUser = { id: 1 } as User;
        const routeSnapshot = { params: { login: 'test123' } } as unknown as ActivatedRouteSnapshot;

        vi.spyOn(adminUserService, 'findUser').mockReturnValue(of(expectedUser));

        const result = resolve.resolve(routeSnapshot);
        let resolvedUser: User | undefined;
        result.subscribe((user) => (resolvedUser = user));

        expect(resolvedUser).toBe(expectedUser);
        expect(adminUserService.findUser).toHaveBeenCalledOnce();
        expect(adminUserService.findUser).toHaveBeenCalledWith('test123');
    });

    it('should return new User instance when login parameter is undefined', () => {
        const existingUser = { id: 1 } as User;
        const routeSnapshot = { params: { login: undefined } } as unknown as ActivatedRouteSnapshot;

        vi.spyOn(adminUserService, 'findUser').mockReturnValue(of(existingUser));

        const result = resolve.resolve(routeSnapshot);
        let resolvedUser: User | undefined;
        result.subscribe((user) => (resolvedUser = user));

        // Should return a new User instance, not the mocked one
        expect(resolvedUser).not.toBe(existingUser);
        expect(resolvedUser).toBeInstanceOf(User);
        expect(adminUserService.findUser).not.toHaveBeenCalled();
    });
});
