import { ActivatedRouteSnapshot } from '@angular/router';
import { UserManagementResolve } from 'app/core/admin/user-management/user-management-resolve.service';
import { User } from 'app/core/user/user.model';
import { of } from 'rxjs';
import { AdminUserService } from 'app/core/user/shared/admin-user.service';
import { TestBed } from '@angular/core/testing';

describe('UserManagementResolve', () => {
    let adminUserService: AdminUserService;
    let resolve: UserManagementResolve;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [UserManagementResolve, { provide: AdminUserService, useValue: { findUser: jest.fn() } }],
        });

        adminUserService = TestBed.inject(AdminUserService);
        resolve = TestBed.inject(UserManagementResolve);
    });

    it('should findUser the user', () => {
        const mockReturnUser = { id: 1 } as User;
        jest.spyOn(adminUserService, 'findUser').mockReturnValue(of(mockReturnUser));

        const returned = resolve.resolve({ params: { login: 'test123' } } as any as ActivatedRouteSnapshot);
        let returnedUser = undefined;
        returned.subscribe((user) => (returnedUser = user));

        expect(returnedUser).toBe(mockReturnUser);
        expect(adminUserService.findUser).toHaveBeenCalledOnce();
        expect(adminUserService.findUser).toHaveBeenCalledWith('test123');
    });

    it('should should return new user if no login is given', () => {
        const mockReturnUser = { id: 1 } as User;
        jest.spyOn(adminUserService, 'findUser').mockReturnValue(of(mockReturnUser));

        const returned = resolve.resolve({ params: { login: undefined } } as any as ActivatedRouteSnapshot);
        let returnedUser: any = undefined;
        returned.subscribe((user) => (returnedUser = user));

        expect(returnedUser).not.toBe(mockReturnUser);
        expect(returnedUser).toBeInstanceOf(User);
        expect(adminUserService.findUser).not.toHaveBeenCalled();
    });
});
