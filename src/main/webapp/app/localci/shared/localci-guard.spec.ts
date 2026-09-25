import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Router, UrlTree } from '@angular/router';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { LocalCIGuard } from 'app/localci/shared/localci-guard.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('LocalCIGuard', () => {
    let guard: LocalCIGuard;
    let router: Router;
    let profileService: ProfileService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [LocalCIGuard, { provide: ProfileService, useClass: MockProfileService }],
        });

        guard = TestBed.inject(LocalCIGuard);
        router = TestBed.inject(Router);
        profileService = TestBed.inject(ProfileService);
    });

    it('should allow access if PROFILE_LOCALCI is active', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [PROFILE_LOCALCI] } as ProfileInfo);
        expect(guard.canActivate()).toBe(true);
    });

    it('should not allow access if PROFILE_LOCALCI is not active', () => {
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeProfiles: [] } as unknown as ProfileInfo);
        const result = guard.canActivate();
        expect(result).toBeInstanceOf(UrlTree);
        expect(router.serializeUrl(result as UrlTree)).toBe('/courses');
    });
});
