import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_IRIS } from 'app/app.constants';
import { IrisGuard } from 'app/iris/shared/iris-guard.service';
import { MockProvider } from 'ng-mocks';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('IrisGuard', () => {
    let guard: IrisGuard;
    let profileInfoSpy: jest.SpyInstance;
    let navigateSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [IrisGuard, MockProvider(ProfileService), MockProvider(Router), { provide: ProfileService, useClass: MockProfileService }],
        });

        guard = TestBed.inject(IrisGuard);
        profileInfoSpy = jest.spyOn(TestBed.inject(ProfileService), 'getProfileInfo');
        navigateSpy = jest.spyOn(TestBed.inject(Router), 'navigate');
    });

    it('should allow access if PROFILE_IRIS is active', async () => {
        const profile = new ProfileInfo();
        profile.activeProfiles = [PROFILE_IRIS];
        profileInfoSpy.mockReturnValue(profile);

        const canActivate = guard.canActivate();

        expect(canActivate).toBeTrue();
        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should not allow access if PROFILE_IRIS is not active', async () => {
        const profile = new ProfileInfo();
        profile.activeProfiles = [];
        profileInfoSpy.mockReturnValue(profile);

        const canActivate = guard.canActivate();

        expect(canActivate).toBeFalse();
        expect(navigateSpy).toHaveBeenCalledWith(['/']);
    });
});
