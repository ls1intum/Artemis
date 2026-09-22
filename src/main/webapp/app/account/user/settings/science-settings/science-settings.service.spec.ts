import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { SCIENCE_SETTING_LOCAL_STORAGE_KEY, ScienceCourseConsent, ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { firstValueFrom } from 'rxjs';

/** In-memory stand-in: the real service reads `localStorage`, which the test environment does not provide. */
class MockLocalStorageService {
    private readonly entries = new Map<string, unknown>();

    store<T>(key: string, value: T): void {
        this.entries.set(key, value);
    }

    retrieve<T>(key: string): T | undefined {
        return this.entries.get(key) as T | undefined;
    }

    remove(key: string): void {
        this.entries.delete(key);
    }
}

const activeConsent: ScienceCourseConsent = {
    courseId: 1,
    courseTitle: 'Course 1',
    courseShortName: 'C1',
    active: true,
    scienceEnabled: true,
};

const inactiveConsent: ScienceCourseConsent = {
    courseId: 2,
    courseTitle: 'Course 2',
    courseShortName: 'C2',
    active: false,
    scienceEnabled: true,
};

const undecidedConsent: ScienceCourseConsent = {
    courseId: 3,
    courseTitle: 'Course 3',
    courseShortName: 'C3',
    scienceEnabled: true,
};

describe('ScienceSettingsService', () => {
    let scienceSettingsService: ScienceSettingsService;
    let localStorageService: LocalStorageService;
    let profileService: ProfileService;
    let httpMock: HttpTestingController;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ProfileService, useClass: MockProfileService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        });
        await TestBed.compileComponents();
        profileService = TestBed.inject(ProfileService);
        const profileInfo = new ProfileInfo();
        profileInfo.activeModuleFeatures = [MODULE_FEATURE_ATLAS];
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);

        scienceSettingsService = TestBed.inject(ScienceSettingsService);
        localStorageService = TestBed.inject(LocalStorageService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
        localStorageService.remove(SCIENCE_SETTING_LOCAL_STORAGE_KEY);
    });

    it('should republish the cache when another tab writes a decision', () => {
        // The listener used to compare against a 'jhi-' prefix that nothing writes, so a decision taken in one tab
        // never reached another - which is only visible through the real event name and the real key.
        localStorageService.store(SCIENCE_SETTING_LOCAL_STORAGE_KEY, [activeConsent]);
        const emitted: ScienceCourseConsent[][] = [];
        scienceSettingsService.getScienceSettingsUpdates().subscribe((consents) => emitted.push(consents));

        dispatchEvent(new StorageEvent('storage', { key: SCIENCE_SETTING_LOCAL_STORAGE_KEY }));

        expect(emitted.at(-1)).toEqual([activeConsent]);
    });

    it('should ignore storage events for unrelated keys', () => {
        const emitted: ScienceCourseConsent[][] = [];
        scienceSettingsService.getScienceSettingsUpdates().subscribe((consents) => emitted.push(consents));
        const emissionsBefore = emitted.length;

        dispatchEvent(new StorageEvent('storage', { key: 'something.else' }));

        expect(emitted).toHaveLength(emissionsBefore);
    });

    it('should refresh per-course science consents from the science endpoint', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');

        scienceSettingsService.refreshScienceSettings().subscribe();

        const request = httpMock.expectOne({ method: 'GET', url: 'api/atlas/science/consents' });
        request.flush([activeConsent, inactiveConsent]);

        expect(storeSpy).toHaveBeenCalledWith(SCIENCE_SETTING_LOCAL_STORAGE_KEY, [activeConsent, inactiveConsent]);
        expect(localStorageService.retrieve(SCIENCE_SETTING_LOCAL_STORAGE_KEY)).toEqual([activeConsent, inactiveConsent]);
    });

    it('should return an empty list without a request when the atlas module is inactive', async () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

        await expect(firstValueFrom(scienceSettingsService.refreshScienceSettings())).resolves.toEqual([]);
        httpMock.expectNone({ method: 'GET', url: 'api/atlas/science/consents' });
    });

    it('should keep the cached consents when refreshing fails', () => {
        localStorageService.store(SCIENCE_SETTING_LOCAL_STORAGE_KEY, [activeConsent]);
        const emitted: ScienceCourseConsent[][] = [];
        scienceSettingsService.getScienceSettingsUpdates().subscribe((consents) => emitted.push(consents));

        scienceSettingsService.refreshScienceSettings().subscribe({ error: () => undefined });
        httpMock.expectOne({ method: 'GET', url: 'api/atlas/science/consents' }).flush('boom', { status: 500, statusText: 'Server Error' });

        expect(emitted.at(-1)).toEqual([activeConsent]);
        expect(localStorageService.retrieve(SCIENCE_SETTING_LOCAL_STORAGE_KEY)).toEqual([activeConsent]);
    });

    it('should replace a stored consent in place so the list keeps its order', () => {
        localStorageService.store(SCIENCE_SETTING_LOCAL_STORAGE_KEY, [activeConsent, inactiveConsent]);

        scienceSettingsService.saveConsentForCourse(inactiveConsent.courseId, true).subscribe();
        const request = httpMock.expectOne({ method: 'PUT', url: `api/atlas/science/courses/${inactiveConsent.courseId}/consent` });
        expect(request.request.body).toEqual({ active: true });
        const updatedConsent = { ...inactiveConsent, active: true };
        request.flush(updatedConsent);

        expect(localStorageService.retrieve(SCIENCE_SETTING_LOCAL_STORAGE_KEY)).toEqual([activeConsent, updatedConsent]);
    });

    it('should append a consent for a course the cache has not seen yet', () => {
        localStorageService.store(SCIENCE_SETTING_LOCAL_STORAGE_KEY, [activeConsent]);

        scienceSettingsService.saveConsentForCourse(undecidedConsent.courseId, true).subscribe();
        const updatedConsent = { ...undecidedConsent, active: true };
        httpMock.expectOne({ method: 'PUT', url: `api/atlas/science/courses/${undecidedConsent.courseId}/consent` }).flush(updatedConsent);

        expect(localStorageService.retrieve(SCIENCE_SETTING_LOCAL_STORAGE_KEY)).toEqual([activeConsent, updatedConsent]);
    });

    it('should not touch the cache when deleting science data', () => {
        localStorageService.store(SCIENCE_SETTING_LOCAL_STORAGE_KEY, [activeConsent]);

        scienceSettingsService.deleteScienceDataForCourse(activeConsent.courseId).subscribe();
        httpMock.expectOne({ method: 'DELETE', url: `api/atlas/science/courses/${activeConsent.courseId}/data` }).flush(null);

        expect(localStorageService.retrieve(SCIENCE_SETTING_LOCAL_STORAGE_KEY)).toEqual([activeConsent]);
    });

    describe('eventLoggingAllowed', () => {
        beforeEach(() => {
            localStorageService.store(SCIENCE_SETTING_LOCAL_STORAGE_KEY, [
                activeConsent,
                inactiveConsent,
                undecidedConsent,
                { ...activeConsent, courseId: 4, scienceEnabled: false },
            ]);
        });

        it('should allow logging for a course with an active consent', () => {
            expect(scienceSettingsService.eventLoggingAllowed(activeConsent.courseId)).toBe(true);
        });

        it('should refuse logging without a course id', () => {
            expect(scienceSettingsService.eventLoggingAllowed(undefined)).toBe(false);
        });

        it('should refuse logging for an opted-out course', () => {
            expect(scienceSettingsService.eventLoggingAllowed(inactiveConsent.courseId)).toBe(false);
        });

        it('should refuse logging while the student has not decided', () => {
            expect(scienceSettingsService.eventLoggingAllowed(undecidedConsent.courseId)).toBe(false);
        });

        it('should refuse logging for a course that no longer collects science data', () => {
            expect(scienceSettingsService.eventLoggingAllowed(4)).toBe(false);
        });

        it('should refuse logging for an unknown course', () => {
            expect(scienceSettingsService.eventLoggingAllowed(999)).toBe(false);
        });
    });
});
