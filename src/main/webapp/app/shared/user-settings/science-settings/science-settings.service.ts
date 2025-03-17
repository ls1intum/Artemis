import { Injectable, inject } from '@angular/core';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Setting } from 'app/shared/user-settings/user-settings.model';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { HttpResponse } from '@angular/common/http';
import { Observable, ReplaySubject } from 'rxjs';
import { ScienceSetting } from 'app/shared/user-settings/science-settings/science-settings-structure';
import { LocalStorageService } from 'ngx-webstorage';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_ATLAS } from 'app/app.constants';

export const SCIENCE_SETTING_LOCAL_STORAGE_KEY = 'artemisapp.science.settings';

@Injectable({ providedIn: 'root' })
export class ScienceSettingsService {
    private userSettingsService = inject(UserSettingsService);
    private localStorageService = inject(LocalStorageService);
    private profileService = inject(ProfileService);

    private currentScienceSettingsSubject = new ReplaySubject<ScienceSetting[]>(1);

    constructor() {
        // we need to handle the subscription here as this service is initialized independently of any component
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo.activeProfiles.includes(PROFILE_ATLAS)) {
                this.initialize();
                this.listenForScienceSettingsChanges();
            }
        });
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
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (!profileInfo.activeProfiles.includes(PROFILE_ATLAS)) {
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
