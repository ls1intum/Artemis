import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATHENA } from 'app/app.constants';
import { AthenaGuard } from 'app/course/manage/services/athena-guard.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';

describe('AthenaGuard', () => {
    let guard: AthenaGuard;
    let router: MockRouter;
    let profileInfoSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            providers: [AthenaGuard, { provide: ProfileService, useClass: MockProfileService }, { provide: Router, useValue: router }],
        });

        guard = TestBed.inject(AthenaGuard);
        profileInfoSpy = vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should allow access if MODULE_FEATURE_ATHENA is active', () => {
        const profile = new ProfileInfo();
        profile.activeModuleFeatures = [MODULE_FEATURE_ATHENA];
        profileInfoSpy.mockReturnValue(profile);

        expect(guard.canActivate()).toBe(true);
        expect(router.createUrlTree).not.toHaveBeenCalled();
    });

    it('should redirect to the start page if MODULE_FEATURE_ATHENA is not active', () => {
        const profile = new ProfileInfo();
        profile.activeModuleFeatures = [];
        profileInfoSpy.mockReturnValue(profile);
        const redirect = new UrlTree();
        router.createUrlTree.mockReturnValue(redirect);

        expect(guard.canActivate()).toBe(redirect);
        expect(router.createUrlTree).toHaveBeenCalledWith(['/']);
    });
});
