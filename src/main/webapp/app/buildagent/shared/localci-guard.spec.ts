import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Router } from '@angular/router';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { LocalCIGuard } from 'app/buildagent/shared/localci-guard.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('LocalCIGuard', () => {
    setupTestBed({ zoneless: true });

    let guard: LocalCIGuard;
    let router: Router;
    let profileService: ProfileService;

    beforeEach(() => {
        const routerMock = {
            navigate: vi.fn(),
        };

        TestBed.configureTestingModule({
            providers: [LocalCIGuard, { provide: ProfileService, useClass: MockProfileService }, { provide: Router, useValue: routerMock }],
        });

        guard = TestBed.inject(LocalCIGuard);
        router = TestBed.inject(Router);
        profileService = TestBed.inject(ProfileService);
    });

    it('should allow access if PROFILE_LOCALCI is active', async () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [PROFILE_LOCALCI] } as ProfileInfo);
        await guard.canActivate();
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should not allow access if PROFILE_LOCALCI is not active', async () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [] } as unknown as ProfileInfo);
        await guard.canActivate();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management']);
    });
});
