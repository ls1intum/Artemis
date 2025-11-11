import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { IsLoggedInWithPasskeyGuard } from './is-logged-in-with-passkey.guard';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../test/helpers/mocks/service/mock-account.service';
import { MockRouter } from '../../../test/helpers/mocks/mock-router';

describe('IsLoggedInWithPasskeyGuard', () => {
    let guard: IsLoggedInWithPasskeyGuard;
    let accountService: AccountService;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [IsLoggedInWithPasskeyGuard, { provide: AccountService, useClass: MockAccountService }, { provide: Router, useClass: MockRouter }],
        });
        guard = TestBed.inject(IsLoggedInWithPasskeyGuard);
        accountService = TestBed.inject(AccountService);
        router = TestBed.inject(Router);
    });

    it('should allow activation when user is logged in with passkey', () => {
        jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(true);

        const result = guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBeTrue();
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should redirect to passkey-required page when user is not logged in with passkey', () => {
        jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(false);
        const navigateSpy = jest.spyOn(router, 'navigate');

        const mockState = { url: '/admin/user-management' } as RouterStateSnapshot;
        const result = guard.canActivate({} as ActivatedRouteSnapshot, mockState);

        expect(result).toBeFalse();
        expect(navigateSpy).toHaveBeenCalledWith(['/passkey-required'], {
            queryParams: { returnUrl: '/admin/user-management' },
        });
    });

    it('should pass the correct return URL in query parameters', () => {
        jest.spyOn(accountService, 'isLoggedInWithPasskey').mockReturnValue(false);
        const navigateSpy = jest.spyOn(router, 'navigate');

        const mockState = { url: '/admin/metrics' } as RouterStateSnapshot;
        guard.canActivate({} as ActivatedRouteSnapshot, mockState);

        expect(navigateSpy).toHaveBeenCalledWith(['/passkey-required'], {
            queryParams: { returnUrl: '/admin/metrics' },
        });
    });
});
