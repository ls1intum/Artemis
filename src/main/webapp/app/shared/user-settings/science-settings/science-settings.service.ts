import { Injectable } from '@angular/core';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';
import { Setting } from 'app/shared/user-settings/user-settings.model';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { HttpResponse } from '@angular/common/http';
import { Observable, ReplaySubject } from 'rxjs';
import { ScienceSetting } from 'app/shared/user-settings/science-settings/science-settings-structure';

@Injectable({ providedIn: 'root' })
export class ScienceSettingsService {
    private currentScienceSettings: ScienceSetting[] = [];
    private currentScienceSettingsSubject = new ReplaySubject<ScienceSetting[]>(1);

    constructor(private userSettingsService: UserSettingsService) {
        this.listenForScienceSettingsChanges();
    }

    public refreshScienceSettings(): void {
        this.userSettingsService.loadSettings(UserSettingsCategory.SCIENCE_SETTINGS).subscribe({
            next: (res: HttpResponse<Setting[]>) => {
                this.currentScienceSettings = this.userSettingsService.loadSettingsSuccessAsIndividualSettings(
                    res.body!,
                    UserSettingsCategory.SCIENCE_SETTINGS,
                ) as ScienceSetting[];

                this.currentScienceSettingsSubject.next(this.currentScienceSettings);
            },
        });
    }

    getScienceSettings(): ScienceSetting[] {
        return this.currentScienceSettings;
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
        const setting = this.currentScienceSettings.find((setting) => {
            return setting.key === 'activity';
        });
        return setting?.active ?? true;
    }
}
