import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { Observable, ReplaySubject, catchError, map, of, tap, throwError } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { UserSettingsService } from 'app/account/user/settings/directive/user-settings.service';
import { ScienceSetting } from 'app/account/user/settings/science-settings/science-settings-structure';
import { Setting } from 'app/account/user/settings/user-settings.model';
import { UserSettingsCategory } from 'app/foundation/constants/user-settings.constants';

export const SCIENCE_SETTING_LOCAL_STORAGE_KEY = 'artemisapp.science.settings';

export interface ScienceCourseConsent {
    courseId: number;
    courseTitle?: string;
    courseShortName?: string;
    active?: boolean;
    decisionDate?: string;
    scienceEnabled: boolean;
}

export type ScienceSettingsStorageEntry = ScienceCourseConsent | ScienceSetting;

@Injectable({ providedIn: 'root' })
export class ScienceSettingsService {
    private readonly httpClient = inject(HttpClient);
    private readonly localStorageService = inject(LocalStorageService);
    private readonly profileService = inject(ProfileService);
    private readonly userSettingsService = inject(UserSettingsService);

    private readonly resourceURL = 'api/atlas/science';
    private readonly currentScienceSettingsSubject = new ReplaySubject<ScienceSettingsStorageEntry[]>(1);

    constructor() {
        if (this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS)) {
            this.initialize();
            this.listenForScienceSettingsChanges();
        }
    }

    initialize(): void {
        addEventListener('storage', (event) => {
            if (event.key === 'jhi-' + SCIENCE_SETTING_LOCAL_STORAGE_KEY) {
                this.currentScienceSettingsSubject.next(this.getStoredScienceSettings());
            }
        });
        this.currentScienceSettingsSubject.next(this.getStoredScienceSettings());
    }

    private getStoredScienceSettings(): ScienceSettingsStorageEntry[] {
        return this.localStorageService.retrieve<ScienceSettingsStorageEntry[]>(SCIENCE_SETTING_LOCAL_STORAGE_KEY) || [];
    }

    private storeScienceSettings(settings?: ScienceSettingsStorageEntry[]): void {
        if (settings) {
            this.localStorageService.store(SCIENCE_SETTING_LOCAL_STORAGE_KEY, settings);
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

    getScienceSettings(): ScienceSettingsStorageEntry[] {
        return this.getStoredScienceSettings();
    }

    getScienceSettingsUpdates(): Observable<ScienceSettingsStorageEntry[]> {
        return this.currentScienceSettingsSubject.asObservable();
    }

    getConsentForCourse(courseId: number): Observable<ScienceCourseConsent> {
        return this.httpClient.get<ScienceCourseConsent>(`${this.resourceURL}/courses/${courseId}/consent`);
    }

    saveConsentForCourse(courseId: number, active: boolean): Observable<ScienceCourseConsent> {
        return this.httpClient.put<ScienceCourseConsent>(`${this.resourceURL}/courses/${courseId}/consent`, { active }).pipe(
            tap((updatedConsent) => {
                const settings = this.getStoredScienceSettings().filter((setting) => !isScienceCourseConsent(setting) || setting.courseId !== courseId);
                this.storeScienceSettings([updatedConsent, ...settings]);
                this.userSettingsService.sendApplyChangesEvent('scienceSettings');
            }),
        );
    }

    deleteScienceDataForCourse(courseId: number): Observable<void> {
        return this.httpClient.delete<void>(`${this.resourceURL}/courses/${courseId}/data`);
    }

    /**
     * Compatibility wrapper for older tests and the generic user settings infrastructure.
     */
    loadLegacySettings(): Observable<HttpResponse<Setting[]>> {
        return this.userSettingsService.loadSettings(UserSettingsCategory.SCIENCE_SETTINGS);
    }

    eventLoggingAllowed(courseId?: number): boolean {
        if (!courseId) {
            return false;
        }
        const setting = this.getStoredScienceSettings()
            .filter(isScienceCourseConsent)
            .find((storedSetting) => storedSetting.courseId === courseId);
        return !!setting && setting.scienceEnabled === true && setting.active === true;
    }

    private listenForScienceSettingsChanges(): void {
        this.userSettingsService.userSettingsChangeEvent.subscribe(() => {
            this.refreshScienceSettings().subscribe({ error: () => undefined });
        });
    }
}

export function isScienceCourseConsent(setting: ScienceSettingsStorageEntry): setting is ScienceCourseConsent {
    return 'courseId' in setting && typeof setting.courseId === 'number';
}

export type { ScienceSetting };
