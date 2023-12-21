import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { BuildQueueGuard } from 'app/localci/build-queue/build-queue.guard';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';

describe('BuildQueueGuard', () => {
    let guard: BuildQueueGuard;
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
            providers: [BuildQueueGuard, { provide: ProfileService, useValue: profileServiceMock }, { provide: Router, useValue: routerMock }],
        });

        guard = TestBed.inject(BuildQueueGuard);
        router = TestBed.inject(Router);
    });

    it('should allow access if PROFILE_LOCALCI is active', async () => {
        profileServiceMock.getProfileInfo.mockReturnValue(of({ activeProfiles: [PROFILE_LOCALCI] }));
        await guard.canActivate();
        expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should not allow access if PROFILE_LOCALCI is not active', async () => {
        profileServiceMock.getProfileInfo.mockReturnValue(of({ activeProfiles: [] }));
        await guard.canActivate();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management']);
    });
});
