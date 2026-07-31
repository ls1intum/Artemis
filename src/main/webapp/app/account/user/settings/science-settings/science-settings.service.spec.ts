import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { SCIENCE_SETTING_LOCAL_STORAGE_KEY, ScienceCourseConsent, ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { UserSettingsService } from 'app/account/user/settings/directive/user-settings.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { Subject } from 'rxjs';

const activeConsent: ScienceCourseConsent = {
    courseId: 1,
    courseTitle: 'Course 1',
    courseShortName: 'C1',
    active: true,
    decisionDate: '2026-07-31T10:00:00Z',
    scienceEnabled: true,
};

const inactiveConsent: ScienceCourseConsent = {
    courseId: 2,
    courseTitle: 'Course 2',
    courseShortName: 'C2',
    active: false,
    decisionDate: '2026-07-31T10:00:00Z',
    scienceEnabled: true,
};

describe('ScienceSettingsService', () => {
    let scienceSettingsService: ScienceSettingsService;
    let userSettingsService: UserSettingsService;
    let localStorageService: LocalStorageService;
    let profileService: ProfileService;
    let httpMock: HttpTestingController;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: ProfileService, useClass: MockProfileService }],
        });
        await TestBed.compileComponents();
        scienceSettingsService = TestBed.inject(ScienceSettingsService);
        userSettingsService = TestBed.inject(UserSettingsService);
        localStorageService = TestBed.inject(LocalStorageService);
        profileService = TestBed.inject(ProfileService);
        httpMock = TestBed.inject(HttpTestingController);

        const profileInfo = new ProfileInfo();
        profileInfo.activeModuleFeatures = [MODULE_FEATURE_ATLAS];
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
        localStorageService.remove(SCIENCE_SETTING_LOCAL_STORAGE_KEY);
    });

    it('should refresh per-course science consents from the science endpoint', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');

        scienceSettingsService.refreshScienceSettings();

        const request = httpMock.expectOne({ method: 'GET', url: 'api/atlas/science/consents' });
        request.flush([activeConsent, inactiveConsent]);

        expect(storeSpy).toHaveBeenCalledWith(SCIENCE_SETTING_LOCAL_STORAGE_KEY, [activeConsent, inactiveConsent]);
        expect(scienceSettingsService.getScienceSettings()).toEqual([activeConsent, inactiveConsent]);
    });

    it('should refresh settings after user settings changed', () => {
        const changes$ = new Subject<string>();
        userSettingsService.userSettingsChangeEvent = changes$.asObservable();

        scienceSettingsService['listenForScienceSettingsChanges']();

        changes$.next('');

        const request = httpMock.expectOne({ method: 'GET', url: 'api/atlas/science/consents' });
        request.flush([activeConsent]);

        expect(scienceSettingsService.getScienceSettings()).toEqual([activeConsent]);
    });

    it('should not refresh settings when ATLAS module is not active', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

        scienceSettingsService.refreshScienceSettings();

        httpMock.expectNone('api/atlas/science/consents');
    });

    it('should provide updates for stored science consents', () => {
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue([activeConsent]);

        scienceSettingsService.initialize();

        scienceSettingsService.getScienceSettingsUpdates().subscribe((updatedSettings) => {
            expect(updatedSettings).toEqual([activeConsent]);
        });
    });

    it('should allow event logging only for active consent in the requested course', () => {
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue([activeConsent, inactiveConsent]);

        expect(scienceSettingsService.eventLoggingAllowed(activeConsent.courseId)).toBe(true);
        expect(scienceSettingsService.eventLoggingAllowed(inactiveConsent.courseId)).toBe(false);
        expect(scienceSettingsService.eventLoggingAllowed()).toBe(false);
        expect(scienceSettingsService.eventLoggingAllowed(99)).toBe(false);
    });

    it('should reject event logging when the course is no longer science-enabled', () => {
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue([{ ...activeConsent, scienceEnabled: false }]);

        expect(scienceSettingsService.eventLoggingAllowed(activeConsent.courseId)).toBe(false);
    });

    it('should get consent for a single course', () => {
        scienceSettingsService.getConsentForCourse(activeConsent.courseId).subscribe((consent) => {
            expect(consent).toEqual(activeConsent);
        });

        const request = httpMock.expectOne({ method: 'GET', url: `api/atlas/science/courses/${activeConsent.courseId}/consent` });
        request.flush(activeConsent);
    });

    it('should save per-course consent and update local storage', () => {
        const sendApplyChangesEventSpy = vi.spyOn(userSettingsService, 'sendApplyChangesEvent');
        const storeSpy = vi.spyOn(localStorageService, 'store');
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue([inactiveConsent]);
        const updatedConsent = { ...activeConsent, active: true };

        scienceSettingsService.saveConsentForCourse(activeConsent.courseId, true).subscribe((consent) => {
            expect(consent).toEqual(updatedConsent);
        });

        const request = httpMock.expectOne({ method: 'PUT', url: `api/atlas/science/courses/${activeConsent.courseId}/consent` });
        expect(request.request.body).toEqual({ active: true });
        request.flush(updatedConsent);

        expect(storeSpy).toHaveBeenCalledWith(SCIENCE_SETTING_LOCAL_STORAGE_KEY, [updatedConsent, inactiveConsent]);
        expect(sendApplyChangesEventSpy).toHaveBeenCalledWith('scienceSettings');
    });

    it('should delete science data for a course', () => {
        let completed = false;
        scienceSettingsService.deleteScienceDataForCourse(activeConsent.courseId).subscribe(() => {
            completed = true;
        });

        const request = httpMock.expectOne({ method: 'DELETE', url: `api/atlas/science/courses/${activeConsent.courseId}/data` });
        request.flush(null);

        expect(completed).toBe(true);
    });

    it('should remove settings when undefined is provided', () => {
        const removeSpy = vi.spyOn(localStorageService, 'remove');
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue([]);

        scienceSettingsService['storeScienceSettings'](undefined);

        expect(removeSpy).toHaveBeenCalledWith(SCIENCE_SETTING_LOCAL_STORAGE_KEY);
    });
});
