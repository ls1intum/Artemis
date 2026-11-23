import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { HyperionGuard } from 'app/hyperion/shared/hyperion-guard.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('HyperionGuard', () => {
    let guard: HyperionGuard;
    let profileInfoSpy: ReturnType<typeof vi.spyOn>;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [HyperionGuard, { provide: ProfileService, useClass: MockProfileService }],
        });

        guard = TestBed.inject(HyperionGuard);
        profileInfoSpy = vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo');
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should allow access if MODULE_FEATURE_HYPERION is active', () => {
        const profile = new ProfileInfo();
        profile.activeModuleFeatures = [MODULE_FEATURE_HYPERION];
        profileInfoSpy.mockReturnValue(profile);

        expect(guard.canActivate()).toBe(true);
    });

    it('should redirect to the start page if MODULE_FEATURE_HYPERION is not active', () => {
        const profile = new ProfileInfo();
        profile.activeModuleFeatures = [];
        profileInfoSpy.mockReturnValue(profile);

        const canActivate = guard.canActivate();

        expect(canActivate).toBeInstanceOf(UrlTree);
        expect(router.serializeUrl(canActivate as UrlTree)).toBe('/');
    });
});
