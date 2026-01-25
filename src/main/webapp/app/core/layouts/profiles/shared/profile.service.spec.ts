import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_EXAM, PROFILE_DEV, PROFILE_PROD } from 'app/app.constants';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';
import { expectedProfileInfo } from 'test/helpers/mocks/service/mock-profile-info';

describe('ProfileService', () => {
    setupTestBed({ zoneless: true });

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
                { provide: BrowserFingerprintService, useValue: { initialize: vi.fn() } },
            ],
        });
        service = TestBed.inject(ProfileService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('Service methods', () => {
        it('should call correct URL', () => {
            service.loadProfileInfo();

            const req = httpMock.expectOne({ method: 'GET' });
            const infoUrl = 'management/info';
            expect(req.request.url).toEqual(infoUrl);
        });

        it('should get the profile info', async () => {
            const featureSpy = vi.spyOn(service['featureToggleService'], 'initializeFeatureToggles');
            const fingerprintSpy = vi.spyOn(service['browserFingerprintService'], 'initialize');

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
            expect(service.isProfileActive(PROFILE_DEV)).toBe(true);
            expect(service.isProfileActive(PROFILE_PROD)).toBe(true);
        });

        it('should return false if the profile is not active', () => {
            // @ts-ignore
            service.profileInfo = { activeProfiles: [PROFILE_PROD] };
            expect(service.isProfileActive(PROFILE_DEV)).toBe(false);
        });

        it('should return false if activeProfiles is undefined', () => {
            // @ts-ignore
            service.profileInfo = {};
            expect(service.isProfileActive(PROFILE_DEV)).toBe(false);
        });

        describe('isModuleFeatureActive', () => {
            it('should return true if the module feature is active', () => {
                // @ts-ignore
                service.profileInfo = { activeModuleFeatures: [MODULE_FEATURE_ATLAS, MODULE_FEATURE_EXAM] };
                expect(service.isModuleFeatureActive(MODULE_FEATURE_ATLAS)).toBe(true);
                expect(service.isModuleFeatureActive(MODULE_FEATURE_EXAM)).toBe(true);
            });

            it('should return false if the module feature is not active', () => {
                // @ts-ignore
                service.profileInfo = { activeModuleFeatures: [MODULE_FEATURE_ATLAS] };
                expect(service.isModuleFeatureActive(MODULE_FEATURE_EXAM)).toBe(false);
            });

            it('should return false if activeModuleFeatures is undefined', () => {
                // @ts-ignore
                service.profileInfo = {};
                expect(service.isModuleFeatureActive(MODULE_FEATURE_ATLAS)).toBe(false);
            });
        });

        describe('isDevelopment', () => {
            it('should return true when dev profile is active', () => {
                // @ts-ignore
                service.profileInfo = { activeProfiles: [PROFILE_DEV] };
                expect(service.isDevelopment()).toBe(true);
            });

            it('should return false when dev profile is not active', () => {
                // @ts-ignore
                service.profileInfo = { activeProfiles: [PROFILE_PROD] };
                expect(service.isDevelopment()).toBe(false);
            });

            it('should return false when activeProfiles is undefined', () => {
                // @ts-ignore
                service.profileInfo = {};
                expect(service.isDevelopment()).toBe(false);
            });
        });

        describe('isProduction', () => {
            it('should return true when prod profile is active', () => {
                // @ts-ignore
                service.profileInfo = { activeProfiles: [PROFILE_PROD] };
                expect(service.isProduction()).toBe(true);
            });

            it('should return false when prod profile is not active', () => {
                // @ts-ignore
                service.profileInfo = { activeProfiles: [PROFILE_DEV] };
                expect(service.isProduction()).toBe(false);
            });

            it('should return false when activeProfiles is undefined', () => {
                // @ts-ignore
                service.profileInfo = {};
                expect(service.isProduction()).toBe(false);
            });
        });

        describe('isTestServer', () => {
            it('should return true when testServer is true', () => {
                // @ts-ignore
                service.profileInfo = { testServer: true };
                expect(service.isTestServer()).toBe(true);
            });

            it('should return false when testServer is false', () => {
                // @ts-ignore
                service.profileInfo = { testServer: false };
                expect(service.isTestServer()).toBe(false);
            });

            it('should return false when testServer is undefined', () => {
                // @ts-ignore
                service.profileInfo = {};
                expect(service.isTestServer()).toBe(false);
            });
        });
    });
});
