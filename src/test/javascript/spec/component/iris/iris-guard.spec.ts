import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_IRIS } from 'app/app.constants';
import { IrisGuard } from 'app/iris/iris-guard.service';
import { MockProvider } from 'ng-mocks';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

describe('IrisGuard', () => {
    let guard: IrisGuard;
    let profileInfoSpy: jest.SpyInstance;
    let navigateSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [IrisGuard, MockProvider(ProfileService), MockProvider(Router)],
        });

        guard = TestBed.inject(IrisGuard);
        profileInfoSpy = jest.spyOn(TestBed.inject(ProfileService), 'getProfileInfo');
        navigateSpy = jest.spyOn(TestBed.inject(Router), 'navigate');
    });

    it('should allow access if PROFILE_IRIS is active', async () => {
        const returnedProfiles = new ProfileInfo();
        returnedProfiles.activeProfiles = [PROFILE_IRIS];
        profileInfoSpy.mockReturnValue(of(returnedProfiles));

        const canActivate = await guard.canActivate();

        expect(canActivate).toBeTrue();
        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should not allow access if PROFILE_IRIS is not active', async () => {
        const returnedProfiles = new ProfileInfo();
        returnedProfiles.activeProfiles = [];
        profileInfoSpy.mockReturnValue(of(returnedProfiles));

        const canActivate = await guard.canActivate();

        expect(canActivate).toBeFalse();
        expect(navigateSpy).toHaveBeenCalledWith(['/']);
    });
});
