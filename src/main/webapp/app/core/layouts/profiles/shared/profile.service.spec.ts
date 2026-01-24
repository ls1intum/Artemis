import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PROFILE_DEV, PROFILE_PROD } from 'app/app.constants';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';
import { expectedProfileInfo } from './profile.constants';

describe('ProfileService', () => {
    let service: ProfileService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                LocalStorageService,
                SessionStorageService,
                { provide: Router, useClass: MockRouter },
                { provide: BrowserFingerprintService, useValue: { initialize: jest.fn() } },
            ],
        });
        service = TestBed.inject(ProfileService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Service methods', () => {
        it('should call correct URL', () => {
            service.loadProfileInfo();

            const req = httpMock.expectOne({ method: 'GET' });
            const infoUrl = 'management/info';
            expect(req.request.url).toEqual(infoUrl);
        });

        it('should get the profile info', async () => {
            const featureSpy = jest.spyOn(service['featureToggleService'], 'initializeFeatureToggles');
            const fingerprintSpy = jest.spyOn(service['browserFingerprintService'], 'initialize');

            const promise = service.loadProfileInfo();
            const req = httpMock.expectOne('management/info');
            expect(req.request.method).toBe('GET');

            req.flush(expectedProfileInfo);

            await promise; // wait for the async method to complete

            expect(service.getProfileInfo()).toEqual(expectedProfileInfo);
            expect(featureSpy).toHaveBeenCalledWith(expectedProfileInfo.features);
            expect(fingerprintSpy).toHaveBeenCalledWith(expectedProfileInfo.studentExamStoreSessionData);
        });

        it('should return true if the profile is active', () => {
            // @ts-ignore
            service.profileInfo = { activeProfiles: [PROFILE_DEV, PROFILE_PROD] };
            expect(service.isProfileActive(PROFILE_DEV)).toBeTrue();
            expect(service.isProfileActive(PROFILE_PROD)).toBeTrue();
        });

        it('should return false if the profile is not active', () => {
            // @ts-ignore
            service.profileInfo = { activeProfiles: [PROFILE_PROD] };
            expect(service.isProfileActive(PROFILE_DEV)).toBeFalse();
        });

        it('should return false if activeProfiles is undefined', () => {
            // @ts-ignore
            service.profileInfo = {};
            expect(service.isProfileActive(PROFILE_DEV)).toBeFalse();
        });
    });
});
