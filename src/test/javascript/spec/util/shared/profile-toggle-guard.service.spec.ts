import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { ProfileToggleGuard } from 'app/shared/profile-toggle/profile-toggle-guard.service';
import { ProfileToggle, ProfileToggleService } from 'app/shared/profile-toggle/profile-toggle.service';

describe('ProfileToggleGuard', () => {
    let service: ProfileToggleGuard;

    let alertServiceStub: jest.SpyInstance;
    let alertService: AlertService;

    let profileToggleService: ProfileToggleService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ProfileToggleGuard);
                profileToggleService = TestBed.inject(ProfileToggleService);
            });

        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => jest.restoreAllMocks());

    it('should create an alert if the profile is not enabled', async () => {
        alertServiceStub = jest.spyOn(alertService, 'addErrorAlert');
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING]);

        const snapshot = { data: { profile: ProfileToggle.LECTURE } as any as ActivatedRouteSnapshot };

        await expect(service.canActivate(snapshot).toPromise()).resolves.toBeFalse();
        expect(alertServiceStub).toHaveBeenCalledOnce();
    });

    it('should not create an alert if the profile is enabled', async () => {
        alertServiceStub = jest.spyOn(alertService, 'addErrorAlert');
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING, ProfileToggle.LECTURE]);

        const snapshot = { data: { profile: ProfileToggle.LECTURE } as any as ActivatedRouteSnapshot };

        await expect(service.canActivate(snapshot).toPromise()).resolves.toBeTrue();
        expect(alertServiceStub).not.toHaveBeenCalled();
    });

    it('should not create an alert if decoupling is disabled', async () => {
        alertServiceStub = jest.spyOn(alertService, 'addErrorAlert');
        profileToggleService.initializeProfileToggles([]);

        const snapshot = { data: { profile: ProfileToggle.LECTURE } as any as ActivatedRouteSnapshot };

        await expect(service.canActivate(snapshot).toPromise()).resolves.toBeTrue();
        expect(alertServiceStub).not.toHaveBeenCalled();
    });
});
