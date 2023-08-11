import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../test.module';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { ProfileToggle, ProfileToggleService } from 'app/shared/profile-toggle/profile-toggle.service';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { MockRouter } from '../helpers/mocks/mock-router';

describe('ProfileToggleService', () => {
    const router = new MockRouter();

    let alertServiceStub: jest.SpyInstance;
    let alertService: AlertService;

    let profileToggleService: ProfileToggleService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [{ provide: Router, useValue: router }],
        })
            .compileComponents()
            .then(() => {
                profileToggleService = TestBed.inject(ProfileToggleService);
            });

        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => jest.restoreAllMocks());

    it('should create an alert if the profile gets disabled/enabled', () => {
        alertServiceStub = jest.spyOn(alertService, 'addAlert');
        // First enabled
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING, ProfileToggle.LECTURE]);

        router.addActivationStart({ outlet: 'primary', data: { profile: ProfileToggle.LECTURE } } as any as ActivatedRouteSnapshot);

        // Then disabled
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING]);

        expect(alertServiceStub).toHaveBeenCalledOnce();
        expect(alertServiceStub).toHaveBeenCalledWith(
            expect.objectContaining({
                type: AlertType.DANGER,
            }),
        );

        jest.resetAllMocks();

        // Then enabled again
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING, ProfileToggle.LECTURE]);

        expect(alertServiceStub).toHaveBeenCalledOnce();
        expect(alertServiceStub).toHaveBeenCalledWith(
            expect.objectContaining({
                type: AlertType.SUCCESS,
            }),
        );
    });

    it('should not create an alert if the current route does not use profiles', () => {
        router.addActivationStart({ outlet: 'primary' } as any as ActivatedRouteSnapshot);

        alertServiceStub = jest.spyOn(alertService, 'addAlert');

        // First enabled
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING, ProfileToggle.LECTURE]);

        // Then disabled
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING]);

        expect(alertServiceStub).not.toHaveBeenCalledOnce();
    });
});
