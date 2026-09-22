import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { Observable, ReplaySubject, catchError, map, of, tap, throwError } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';

export const SCIENCE_SETTING_LOCAL_STORAGE_KEY = 'artemisapp.science.settings';

export interface ScienceCourseConsent {
    courseId: number;
    courseTitle?: string;
    courseShortName?: string;
    active?: boolean;
    scienceEnabled: boolean;
}

@Injectable({ providedIn: 'root' })
export class ScienceSettingsService {
    private readonly httpClient = inject(HttpClient);
    private readonly localStorageService = inject(LocalStorageService);
    private readonly profileService = inject(ProfileService);

    private readonly resourceURL = 'api/atlas/science';
    private readonly currentScienceSettingsSubject = new ReplaySubject<ScienceCourseConsent[]>(1);

    constructor() {
        if (this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS)) {
            // Keeps a second tab in step: consent is cached in local storage so that logging an interaction event does
            // not need a round trip, and a decision taken elsewhere has to invalidate that cache here too.
            addEventListener('storage', (event) => {
                // LocalStorageService writes the key as given, so this compares against the bare key. It used to look
                // for a 'jhi-' prefix that nothing writes, which meant a decision taken in one tab never reached another.
                if (event.key === SCIENCE_SETTING_LOCAL_STORAGE_KEY) {
                    this.currentScienceSettingsSubject.next(this.getStoredScienceSettings());
                }
            });
            this.currentScienceSettingsSubject.next(this.getStoredScienceSettings());
        }
    }

    private getStoredScienceSettings(): ScienceCourseConsent[] {
        return this.localStorageService.retrieve<ScienceCourseConsent[]>(SCIENCE_SETTING_LOCAL_STORAGE_KEY) || [];
    }

    private storeScienceSettings(consents?: ScienceCourseConsent[]): void {
        if (consents) {
            this.localStorageService.store(SCIENCE_SETTING_LOCAL_STORAGE_KEY, consents);
        } else {
            this.localStorageService.remove(SCIENCE_SETTING_LOCAL_STORAGE_KEY);
        }
        this.currentScienceSettingsSubject.next(this.getStoredScienceSettings());
    }

    refreshScienceSettings(): Observable<ScienceCourseConsent[]> {
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS)) {
            return of([]);
        }

        return this.httpClient.get<ScienceCourseConsent[]>(`${this.resourceURL}/consents`, { observe: 'response' }).pipe(
            map((res: HttpResponse<ScienceCourseConsent[]>) => res.body ?? []),
            tap((currentScienceSettings) => {
                this.storeScienceSettings(currentScienceSettings);
            }),
            catchError((error) => {
                this.currentScienceSettingsSubject.next(this.getStoredScienceSettings());
                return throwError(() => error);
            }),
        );
    }

    getScienceSettingsUpdates(): Observable<ScienceCourseConsent[]> {
        return this.currentScienceSettingsSubject.asObservable();
    }

    saveConsentForCourse(courseId: number, active: boolean): Observable<ScienceCourseConsent> {
        return this.httpClient.put<ScienceCourseConsent>(`${this.resourceURL}/courses/${courseId}/consent`, { active }).pipe(
            tap((updatedConsent) => {
                // Replaced in place rather than prepended, so a toggle does not reorder the list under the cursor. A
                // course the cache has not seen yet is appended, which only happens when it was enabled mid-session.
                const stored = this.getStoredScienceSettings();
                const known = stored.some((consent) => consent.courseId === courseId);
                this.storeScienceSettings(known ? stored.map((consent) => (consent.courseId === courseId ? updatedConsent : consent)) : [...stored, updatedConsent]);
            }),
        );
    }

    deleteScienceDataForCourse(courseId: number): Observable<void> {
        return this.httpClient.delete<void>(`${this.resourceURL}/courses/${courseId}/data`);
    }

    eventLoggingAllowed(courseId?: number): boolean {
        if (!courseId) {
            return false;
        }
        const consent = this.getStoredScienceSettings().find((storedConsent) => storedConsent.courseId === courseId);
        return !!consent && consent.scienceEnabled === true && consent.active === true;
    }
}
