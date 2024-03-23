import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { LocalVCGuard } from 'app/localvc/localvc-guard.service';

describe('LocalVCGuard', () => {
    let guard: LocalVCGuard;
    let router: Router;
    let profileServiceMock: { getProfileInfo: jest.Mock };

    beforeEach(() => {
        // Define profileServiceMock here so it's accessible in the tests
        profileServiceMock = {
            getProfileInfo: jest.fn(),
        };

        const routerMock = {
            navigate: jest.fn(),
        };

        TestBed.configureTestingModule({
            providers: [LocalVCGuard, { provide: ProfileService, useValue: profileServiceMock }, { provide: Router, useValue: routerMock }],
        });

        guard = TestBed.inject(LocalVCGuard);
        router = TestBed.inject(Router);
    });

    it('should allow access if PROFILE_LOCALVC is active', async () => {
        profileServiceMock.getProfileInfo.mockReturnValue(of({ activeProfiles: [PROFILE_LOCALVC] }));
        await guard.canActivate();
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should not allow access if PROFILE_LOCALVC is not active', async () => {
        profileServiceMock.getProfileInfo.mockReturnValue(of({ activeProfiles: [] }));
        await guard.canActivate();
        expect(router.navigate).toHaveBeenCalledWith(['/']);
    });

    it('should not allow access and navigate to "/" if an error occurs while fetching profile information', async () => {
        // Mock the profileService to return a rejected promise with an error message
        const errorMessage = 'Test error';
        profileServiceMock.getProfileInfo.mockRejectedValue(errorMessage);

        // Spy on console.error
        const consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {}); // Mock console.error to do nothing

        // Call the canActivate method
        const canActivateResult = await guard.canActivate();

        // Assert that the router's navigate method is called with ['/']
        expect(router.navigate).toHaveBeenCalledWith(['/']);

        // Assert that the canActivate method returns false
        expect(canActivateResult).toBeFalse();

        // Assert that console.error was called with the expected prefix string
        expect(consoleErrorSpy).toHaveBeenCalledWith('Error fetching profile information:', expect.anything());
    });
});
