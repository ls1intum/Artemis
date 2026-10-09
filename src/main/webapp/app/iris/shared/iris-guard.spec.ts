import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { IrisGuard } from 'app/iris/shared/iris-guard.service';
import { MockProvider } from 'ng-mocks';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('IrisGuard', () => {
    let guard: IrisGuard;
    let profileInfoSpy: ReturnType<typeof vi.spyOn>;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [IrisGuard, MockProvider(ProfileService), { provide: ProfileService, useClass: MockProfileService }],
        });

        guard = TestBed.inject(IrisGuard);
        profileInfoSpy = vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo');
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should allow access if MODULE_FEATURE_IRIS is active', async () => {
        const profile = new ProfileInfo();
        profile.activeModuleFeatures = [MODULE_FEATURE_IRIS];
        profileInfoSpy.mockReturnValue(profile);

        const canActivate = guard.canActivate();

        expect(canActivate).toBe(true);
    });

    it('should not allow access if MODULE_FEATURE_IRIS is not active', async () => {
        const profile = new ProfileInfo();
        profile.activeModuleFeatures = [];
        profileInfoSpy.mockReturnValue(profile);

        const canActivate = guard.canActivate();

        expect(canActivate).toBeInstanceOf(UrlTree);
        expect(router.serializeUrl(canActivate as UrlTree)).toBe('/');
    });
});
