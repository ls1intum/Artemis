import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { ScienceCourseConsent, ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { firstValueFrom } from 'rxjs';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';

const activeConsent: ScienceCourseConsent = { courseId: 1, courseTitle: 'Course 1', courseShortName: 'C1', active: true, scienceEnabled: true };
const inactiveConsent: ScienceCourseConsent = { courseId: 2, courseTitle: 'Course 2', courseShortName: 'C2', active: false, scienceEnabled: true };

describe('ScienceSettingsService', () => {
    let scienceSettingsService: ScienceSettingsService;
    let profileService: ProfileService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: ProfileService, useClass: MockProfileService }],
        });
        profileService = TestBed.inject(ProfileService);
        const profileInfo = new ProfileInfo();
        profileInfo.activeModuleFeatures = [MODULE_FEATURE_ATLAS];
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);

        scienceSettingsService = TestBed.inject(ScienceSettingsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should load the consents of every course from the list endpoint', async () => {
        const consents = firstValueFrom(scienceSettingsService.getConsents());

        const request = httpMock.expectOne({ method: 'GET', url: 'api/atlas/science/consents' });
        // The caller handles a failure itself, so the global error alert is skipped.
        expect(request.request.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
        request.flush([activeConsent, inactiveConsent]);

        await expect(consents).resolves.toEqual([activeConsent, inactiveConsent]);
    });

    it('should load the consent of one course from the course endpoint', async () => {
        const consent = firstValueFrom(scienceSettingsService.getConsentForCourse(1));

        const request = httpMock.expectOne({ method: 'GET', url: 'api/atlas/science/courses/1/consent' });
        expect(request.request.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
        request.flush({ courseId: 1, active: true, scienceEnabled: true });

        await expect(consent).resolves.toEqual({ courseId: 1, active: true, scienceEnabled: true });
    });

    it('should not send a request while the atlas module is inactive', async () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

        await expect(firstValueFrom(scienceSettingsService.getConsents())).resolves.toEqual([]);
        await expect(firstValueFrom(scienceSettingsService.getConsentForCourse(1))).resolves.toBeUndefined();
        httpMock.expectNone(() => true);
    });

    it('should store a decision for a course', async () => {
        const saved = firstValueFrom(scienceSettingsService.saveConsentForCourse(2, true));

        const request = httpMock.expectOne({ method: 'PUT', url: 'api/atlas/science/courses/2/consent' });
        expect(request.request.body).toEqual({ active: true });
        request.flush({ ...inactiveConsent, active: true });

        await expect(saved).resolves.toEqual({ ...inactiveConsent, active: true });
    });

    it('should delete the science data of a course', () => {
        scienceSettingsService.deleteScienceDataForCourse(1).subscribe();

        httpMock.expectOne({ method: 'DELETE', url: 'api/atlas/science/courses/1/data' }).flush(null);
    });
});
