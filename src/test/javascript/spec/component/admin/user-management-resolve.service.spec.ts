import { ActivatedRouteSnapshot } from '@angular/router';
import { UserManagementResolve } from 'app/admin/user-management/user-management-resolve.service';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { of } from 'rxjs';

describe('UserManagementResolve', () => {
    let userService: UserService;
    let resolve: UserManagementResolve;

    beforeEach(() => {
        userService = { find: jest.fn() } as any as UserService;
        resolve = new UserManagementResolve(userService);
    });

    it('should find the user', () => {
        const mockReturnUser = { id: 1 } as User;
        jest.spyOn(userService, 'find').mockReturnValue(of(mockReturnUser));

        const returned = resolve.resolve({ params: { login: 'test123' } } as any as ActivatedRouteSnapshot);
        let returnedUser = undefined;
        returned.subscribe((user) => (returnedUser = user));

        expect(returnedUser).toBe(mockReturnUser);
        expect(userService.find).toHaveBeenCalledOnce();
        expect(userService.find).toHaveBeenCalledWith('test123');
    });

    it('should should return new user if no login is given', () => {
        const mockReturnUser = { id: 1 } as User;
        jest.spyOn(userService, 'find').mockReturnValue(of(mockReturnUser));

        const returned = resolve.resolve({ params: { login: undefined } } as any as ActivatedRouteSnapshot);
        let returnedUser: any = undefined;
        returned.subscribe((user) => (returnedUser = user));

        expect(returnedUser).not.toBe(mockReturnUser);
        expect(returnedUser).toBeInstanceOf(User);
        expect(userService.find).not.toHaveBeenCalled();
    });
});
