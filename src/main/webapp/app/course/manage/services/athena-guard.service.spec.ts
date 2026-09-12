import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATHENA } from 'app/app.constants';
import { AthenaGuard } from 'app/course/manage/services/athena-guard.service';
import { MockProvider } from 'ng-mocks';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('AthenaGuard', () => {
    let guard: AthenaGuard;
    let profileInfoSpy: ReturnType<typeof vi.spyOn>;
    let navigateSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [AthenaGuard, MockProvider(ProfileService), MockProvider(Router), { provide: ProfileService, useClass: MockProfileService }],
        });

        guard = TestBed.inject(AthenaGuard);
        profileInfoSpy = vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo');
        navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should allow access if MODULE_FEATURE_ATHENA is active', async () => {
        const profile = new ProfileInfo();
        profile.activeModuleFeatures = [MODULE_FEATURE_ATHENA];
        profileInfoSpy.mockReturnValue(profile);

        const canActivate = guard.canActivate();

        expect(canActivate).toBe(true);
        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should not allow access if MODULE_FEATURE_ATHENA is not active', async () => {
        const profile = new ProfileInfo();
        profile.activeModuleFeatures = [];
        profileInfoSpy.mockReturnValue(profile);

        const canActivate = guard.canActivate();

        expect(canActivate).toBe(false);
        expect(navigateSpy).toHaveBeenCalledWith(['/']);
    });
});
