import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { PasskeyAuthenticationGuard } from './passkey-authentication.guard';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_PASSKEY, MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN } from 'app/app.constants';
import { User } from 'app/core/user/user.model';

describe('IsLoggedInWithPasskeyGuard', () => {
    let guard: PasskeyAuthenticationGuard;
    let accountService: AccountService;
    let router: Router;
    let profileService: ProfileService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                PasskeyAuthenticationGuard,
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
            ],
        });
        guard = TestBed.inject(PasskeyAuthenticationGuard);
        accountService = TestBed.inject(AccountService);
        router = TestBed.inject(Router);
        profileService = TestBed.inject(ProfileService);
    });

    it('should allow activation when passkey enforcement is disabled', async () => {
        jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

        const result = await guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBeTrue();
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should allow activation when user is logged in with approved passkey', async () => {
        jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);
        jest.spyOn(accountService, 'identity').mockResolvedValue({ id: 99, login: 'admin' } as User);

        const result = await guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBeTrue();
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should redirect to passkey-required page when user is not logged in with approved passkey', async () => {
        jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(false);
        jest.spyOn(accountService, 'identity').mockResolvedValue({ id: 99, login: 'admin' } as User);
        const navigateSpy = jest.spyOn(router, 'navigate');

        const mockState = { url: '/admin/user-management' } as RouterStateSnapshot;
        const result = await guard.canActivate({} as ActivatedRouteSnapshot, mockState);

        expect(result).toBeFalse();
        expect(navigateSpy).toHaveBeenCalledWith(['/passkey-required'], {
            queryParams: { returnUrl: '/admin/user-management' },
        });
    });

    it('should pass the correct return URL in query parameters', async () => {
        jest.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(false);
        jest.spyOn(accountService, 'identity').mockResolvedValue({ id: 99, login: 'admin' } as User);
        const navigateSpy = jest.spyOn(router, 'navigate');

        const mockState = { url: '/admin/metrics' } as RouterStateSnapshot;
        await guard.canActivate({} as ActivatedRouteSnapshot, mockState);

        expect(navigateSpy).toHaveBeenCalledWith(['/passkey-required'], {
            queryParams: { returnUrl: '/admin/metrics' },
        });
    });

    it('should allow activation when passkey module is disabled', async () => {
        const isModuleFeatureActiveSpy = jest.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => {
            if (feature === MODULE_FEATURE_PASSKEY) {
                return false;
            }
            return true;
        });

        const result = await guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBeTrue();
        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_PASSKEY);
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should allow activation when passkey is enabled but require admin feature is disabled', async () => {
        const isModuleFeatureActiveSpy = jest.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => {
            if (feature === MODULE_FEATURE_PASSKEY) {
                return true;
            }
            if (feature === MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN) {
                return false;
            }
            return false;
        });

        const result = await guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBeTrue();
        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_PASSKEY);
        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN);
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should enforce passkey check when both passkey and require admin features are enabled', async () => {
        const isModuleFeatureActiveSpy = jest.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => {
            return feature === MODULE_FEATURE_PASSKEY || feature === MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN;
        });
        jest.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);
        jest.spyOn(accountService, 'identity').mockResolvedValue({ id: 99, login: 'admin' } as User);

        const result = await guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBeTrue();
        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_PASSKEY);
        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN);
        expect(accountService.isUserLoggedInWithApprovedPasskey).toHaveBeenCalled();
    });
});
