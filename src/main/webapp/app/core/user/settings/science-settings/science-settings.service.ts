import { Injectable, inject } from '@angular/core';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { HttpResponse } from '@angular/common/http';
import { Observable, ReplaySubject } from 'rxjs';
import { LocalStorageService } from 'ngx-webstorage';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { UserSettingsService } from 'app/core/user/settings/directive/user-settings.service';
import { ScienceSetting } from 'app/core/user/settings/science-settings/science-settings-structure';
import { Setting } from 'app/core/user/settings/user-settings.model';

export const SCIENCE_SETTING_LOCAL_STORAGE_KEY = 'artemisapp.science.settings';

@Injectable({ providedIn: 'root' })
export class ScienceSettingsService {
    private userSettingsService = inject(UserSettingsService);
    private localStorageService = inject(LocalStorageService);
    private profileService = inject(ProfileService);

    private currentScienceSettingsSubject = new ReplaySubject<ScienceSetting[]>(1);

    constructor() {
        // we need to handle the subscription here as this service is initialized independently of any component
        if (this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS)) {
            this.initialize();
            this.listenForScienceSettingsChanges();
        }
    }

    initialize() {
        addEventListener('storage', (event) => {
            if (event.key === 'jhi-' + SCIENCE_SETTING_LOCAL_STORAGE_KEY) {
                this.currentScienceSettingsSubject.next(this.getStoredScienceSettings());
            }
        });
        this.currentScienceSettingsSubject.next(this.getStoredScienceSettings());
    }

    private getStoredScienceSettings(): ScienceSetting[] {
        const storedIdentifier = this.localStorageService.retrieve(SCIENCE_SETTING_LOCAL_STORAGE_KEY);
        return storedIdentifier ? JSON.parse(storedIdentifier) : [];
    }

    private storeScienceSettings(settings?: ScienceSetting[]): void {
        if (settings) {
            this.localStorageService.store(SCIENCE_SETTING_LOCAL_STORAGE_KEY, JSON.stringify(settings));
        } else {
            this.localStorageService.clear(SCIENCE_SETTING_LOCAL_STORAGE_KEY);
        }
        this.currentScienceSettingsSubject.next(this.getStoredScienceSettings());
    }

    public refreshScienceSettings(): void {
        if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS)) {
            return;
        }

        this.userSettingsService.loadSettings(UserSettingsCategory.SCIENCE_SETTINGS).subscribe({
            next: (res: HttpResponse<Setting[]>) => {
                const currentScienceSettings = this.userSettingsService.loadSettingsSuccessAsIndividualSettings(
                    res.body!,
                    UserSettingsCategory.SCIENCE_SETTINGS,
                ) as ScienceSetting[];

                this.storeScienceSettings(currentScienceSettings);
                this.currentScienceSettingsSubject.next(currentScienceSettings);
            },
        });
    }

    getScienceSettings(): ScienceSetting[] {
        return this.getStoredScienceSettings();
    }

    getScienceSettingsUpdates(): Observable<ScienceSetting[]> {
        return this.currentScienceSettingsSubject.asObservable();
    }

    /**
     * Subscribes and listens for changes related to science
     */
    private listenForScienceSettingsChanges(): void {
        this.userSettingsService.userSettingsChangeEvent.subscribe(() => {
            this.refreshScienceSettings();
        });
    }

    eventLoggingAllowed(): boolean {
        const setting = this.getStoredScienceSettings().find((setting) => {
            return setting.key === 'activity';
        });
        return setting?.active ?? true;
    }
}
