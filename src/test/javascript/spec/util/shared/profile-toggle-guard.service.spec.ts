import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { mockedActivatedRouteSnapshot } from '../../helpers/mocks/activated-route/mock-activated-route-snapshot';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ProfileToggleGuard } from 'app/shared/profile-toggle/profile-toggle-guard.service';
import { ProfileToggle, ProfileToggleService } from 'app/shared/profile-toggle/profile-toggle.service';

describe('ProfileToggleGuard', () => {
    const route = 'courses/:courseId/lectures';
    let service: ProfileToggleGuard;

    let alertServiceStub: jest.SpyInstance;
    let alertService: AlertService;

    let profileToggleService: ProfileToggleService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                mockedActivatedRouteSnapshot(route),
                { provide: AccountService, useClass: MockAccountService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                MockProvider(StateStorageService),
            ],
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

        const snapshot = { data: { profile: ProfileToggle.LECTURE } };

        await expect(service.canActivate(snapshot).toPromise()).resolves.toBeFalse();
        expect(alertServiceStub).toHaveBeenCalledOnce();
    });

    it('should not create an alert if the profile is enabled', async () => {
        alertServiceStub = jest.spyOn(alertService, 'addErrorAlert');
        profileToggleService.initializeProfileToggles([ProfileToggle.DECOUPLING, ProfileToggle.LECTURE]);

        const snapshot = { data: { profile: ProfileToggle.LECTURE } };

        await expect(service.canActivate(snapshot).toPromise()).resolves.toBeTrue();
        expect(alertServiceStub).not.toHaveBeenCalled();
    });

    it('should not create an alert if decoupling is disabled', async () => {
        alertServiceStub = jest.spyOn(alertService, 'addErrorAlert');
        profileToggleService.initializeProfileToggles([]);

        const snapshot = { data: { profile: ProfileToggle.LECTURE } };

        await expect(service.canActivate(snapshot).toPromise()).resolves.toBeTrue();
        expect(alertServiceStub).not.toHaveBeenCalled();
    });
});
