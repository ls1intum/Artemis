import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../test.module';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { AlertService, AlertType } from 'app/core/util/alert.service';
import { ProfileToggleGuard } from 'app/shared/profile-toggle/profile-toggle-guard.service';
import { ProfileToggle, ProfileToggleService } from 'app/shared/profile-toggle/profile-toggle.service';
import { Router } from '@angular/router';
import { MockRouter } from '../helpers/mocks/mock-router';

describe('ProfileToggleService', () => {
    const router = new MockRouter();

    let service: ProfileToggleService;

    let alertServiceStub: jest.SpyInstance;
    let alertService: AlertService;

    let profileToggleService: ProfileToggleService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            declarations: [CourseExerciseDetailsComponent],
            providers: [{ provide: Router, useValue: router }],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ProfileToggleGuard);
                profileToggleService = TestBed.inject(ProfileToggleService);
            });

        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => jest.restoreAllMocks());

    it('should create an alert if the profile gets disabled/enabled', () => {
        alertServiceStub = jest.spyOn(alertService, 'addAlert');
        // First enabled
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING, ProfileToggle.LECTURE]);

        router.addActivationStart({ outlet: 'primary', data: { profile: ProfileToggle.LECTURE } });

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
        router.addActivationStart({ outlet: 'primary' });

        alertServiceStub = jest.spyOn(alertService, 'addAlert');

        // First enabled
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING, ProfileToggle.LECTURE]);

        // Then disabled
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING]);

        expect(alertServiceStub).not.toHaveBeenCalledOnce();
    });
});
