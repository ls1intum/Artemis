import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_METIS } from 'app/app.constants';
import { MetisGuard } from 'app/metis/shared/metis-guard.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('MetisGuard', () => {
    let guard: MetisGuard;
    let profileInfoSpy: ReturnType<typeof vi.spyOn>;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MetisGuard, { provide: ProfileService, useClass: MockProfileService }],
        });

        guard = TestBed.inject(MetisGuard);
        profileInfoSpy = vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo');
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should allow access if MODULE_FEATURE_METIS is active', () => {
        const profile = new ProfileInfo();
        profile.activeModuleFeatures = [MODULE_FEATURE_METIS];
        profileInfoSpy.mockReturnValue(profile);

        const canActivate = guard.canActivate();

        expect(canActivate).toBe(true);
    });

    it('should not allow access if MODULE_FEATURE_METIS is not active', () => {
        const profile = new ProfileInfo();
        profile.activeModuleFeatures = [];
        profileInfoSpy.mockReturnValue(profile);

        const canActivate = guard.canActivate();

        expect(canActivate).toBeInstanceOf(UrlTree);
        expect(router.serializeUrl(canActivate as UrlTree)).toBe('/');
    });
});
