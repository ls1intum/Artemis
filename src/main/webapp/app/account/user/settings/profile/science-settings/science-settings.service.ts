import { Service, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { Observable, of } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';

export interface ScienceCourseConsent {
    courseId: number;
    courseTitle?: string;
    courseShortName?: string;
    active?: boolean;
    scienceEnabled: boolean;
}

/**
 * Talks to the science consent endpoints. It keeps no state: every page loads what it shows and owns the result, so
 * nothing outlives the page or the session of the user who loaded it.
 */
@Service()
export class ScienceSettingsService {
    private readonly httpClient = inject(HttpClient);
    private readonly profileService = inject(ProfileService);

    private readonly resourceURL = 'api/atlas/science';

    /**
     * Loads the consent history of every course the user may decide for, for the settings page. Each caller handles a
     * failure itself, so the global error alert is skipped.
     */
    getConsents(): Observable<ScienceCourseConsent[]> {
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS)) {
            return of([]);
        }
        const context = new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true);
        return this.httpClient.get<ScienceCourseConsent[]>(`${this.resourceURL}/consents`, { context });
    }

    /**
     * Loads the consent state of one course, on course entry. A failure is the caller's to handle, so the global error
     * alert is skipped: the student cannot act on it.
     */
    getConsentForCourse(courseId: number): Observable<ScienceCourseConsent | undefined> {
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS)) {
            return of(undefined);
        }
        const context = new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true);
        return this.httpClient.get<ScienceCourseConsent>(`${this.resourceURL}/courses/${courseId}/consent`, { context });
    }

    saveConsentForCourse(courseId: number, active: boolean): Observable<ScienceCourseConsent> {
        return this.httpClient.put<ScienceCourseConsent>(`${this.resourceURL}/courses/${courseId}/consent`, { active });
    }

    deleteScienceDataForCourse(courseId: number): Observable<void> {
        return this.httpClient.delete<void>(`${this.resourceURL}/courses/${courseId}/data`);
    }
}
