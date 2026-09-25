import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { PasskeyAuthenticationGuard } from './passkey-authentication.guard';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_PASSKEY, MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN } from 'app/app.constants';
import { User } from 'app/account/user/user.model';

describe('PasskeyAuthenticationGuard', () => {
    let guard: PasskeyAuthenticationGuard;
    let accountService: AccountService;
    let router: Router;
    let profileService: ProfileService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                PasskeyAuthenticationGuard,
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
            ],
        });
        guard = TestBed.inject(PasskeyAuthenticationGuard);
        accountService = TestBed.inject(AccountService);
        router = TestBed.inject(Router);
        profileService = TestBed.inject(ProfileService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should allow activation when passkey enforcement is disabled', async () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

        const result = await guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBe(true);
    });

    it('should allow activation when user is logged in with approved passkey', async () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        vi.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);
        vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 99, login: 'admin' } as User);

        const result = await guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBe(true);
    });

    it('should redirect to passkey-required page when user is not logged in with approved passkey', async () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        vi.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(false);
        vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 99, login: 'admin' } as User);

        const mockState = { url: '/admin/user-management' } as RouterStateSnapshot;
        const result = await guard.canActivate({} as ActivatedRouteSnapshot, mockState);

        expect(result).toBeInstanceOf(UrlTree);
        expect(router.serializeUrl(result as UrlTree)).toBe('/passkey-required?returnUrl=%2Fadmin%2Fuser-management');
    });

    it('should pass the correct return URL in query parameters', async () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        vi.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(false);
        vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 99, login: 'admin' } as User);

        const mockState = { url: '/admin/metrics' } as RouterStateSnapshot;
        const result = await guard.canActivate({} as ActivatedRouteSnapshot, mockState);

        expect(result).toBeInstanceOf(UrlTree);
        const redirect = result as UrlTree;
        expect(redirect.root.children['primary'].segments.map((segment) => segment.path)).toEqual(['passkey-required']);
        expect(redirect.queryParams).toEqual({ returnUrl: '/admin/metrics' });
    });

    it('should allow activation when passkey module is disabled', async () => {
        const isModuleFeatureActiveSpy = vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => {
            if (feature === MODULE_FEATURE_PASSKEY) {
                return false;
            }
            return true;
        });

        const result = await guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBe(true);
        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_PASSKEY);
    });

    it('should allow activation when passkey is enabled but require admin feature is disabled', async () => {
        const isModuleFeatureActiveSpy = vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => {
            if (feature === MODULE_FEATURE_PASSKEY) {
                return true;
            }
            if (feature === MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN) {
                return false;
            }
            return false;
        });

        const result = await guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBe(true);
        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_PASSKEY);
        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN);
    });

    it('should enforce passkey check when both passkey and require admin features are enabled', async () => {
        const isModuleFeatureActiveSpy = vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => {
            return feature === MODULE_FEATURE_PASSKEY || feature === MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN;
        });
        vi.spyOn(accountService, 'isUserLoggedInWithApprovedPasskey').mockReturnValue(true);
        vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 99, login: 'admin' } as User);

        const result = await guard.canActivate({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);

        expect(result).toBe(true);
        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_PASSKEY);
        expect(isModuleFeatureActiveSpy).toHaveBeenCalledWith(MODULE_FEATURE_PASSKEY_REQUIRE_ADMIN);
        expect(accountService.isUserLoggedInWithApprovedPasskey).toHaveBeenCalled();
    });
});
