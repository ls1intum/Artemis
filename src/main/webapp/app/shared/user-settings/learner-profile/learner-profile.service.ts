import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, ReplaySubject, map } from 'rxjs';
import { UserSettingsService } from '../user-settings.service';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';

export interface FeedbackLearnerProfile {
    id?: number;
    practicalVsTheoretical: number;
    creativeVsFocused: number;
    followUpVsSummary: number;
    briefVsDetailed: number;
}

@Injectable({ providedIn: 'root' })
export class LearnerProfileService {
    private http = inject(HttpClient);
    private resourceUrl = 'api/feedback-learner-profile';
    private userSettingsService = inject(UserSettingsService);

    private currentLearnerProfileSubject = new ReplaySubject<FeedbackLearnerProfile>(1);

    constructor() {
        this.userSettingsService.userSettingsChangeEvent.subscribe(() => {
            this.loadProfile();
        });
    }

    private loadProfile() {
        this.getProfile().subscribe((profile) => {
            this.currentLearnerProfileSubject.next(profile);
        });
    }
    /**
     * Get updates to the learner profile settings
     */
    getLearnerProfileUpdates(): Observable<FeedbackLearnerProfile> {
        return this.currentLearnerProfileSubject.asObservable();
    }

    /**
     * Initialize the learner profile by loading it from the server
     */
    initialize() {
        this.loadProfile();
    }

    /**
     * Refresh the learner profile settings from the server
     */
    refreshLearnerProfile(): void {
        this.userSettingsService.loadSettings(UserSettingsCategory.LEARNER_PROFILE).subscribe({
            next: (res) => {
                const currentLearnerSettings = this.userSettingsService.loadSettingsSuccessAsIndividualSettings(res.body!, UserSettingsCategory.LEARNER_PROFILE);

                // Convert settings to FeedbackLearnerProfile format
                const profile: FeedbackLearnerProfile = {
                    practicalVsTheoretical: this.getSettingValue(currentLearnerSettings, 'practicalVsTheoretical'),
                    creativeVsFocused: this.getSettingValue(currentLearnerSettings, 'creativeVsFocused'),
                    followUpVsSummary: this.getSettingValue(currentLearnerSettings, 'followUpVsSummary'),
                    briefVsDetailed: this.getSettingValue(currentLearnerSettings, 'briefVsDetailed'),
                };

                this.currentLearnerProfileSubject.next(profile);
            },
        });
    }

    /**
     * Helper method to get setting value from array
     */
    private getSettingValue(settings: any[], key: string): number {
        const setting = settings.find((s) => s.key === key);
        return setting ? setting.value : 0;
    }

    /**
     * Get the current user's feedback learner profile from the server
     * @returns Observable of the user's FeedbackLearnerProfile
     */
    getProfile(): Observable<FeedbackLearnerProfile> {
        return this.userSettingsService.loadSettings(UserSettingsCategory.LEARNER_PROFILE).pipe(
            map((res) => {
                const settings = this.userSettingsService.loadSettingsSuccessAsIndividualSettings(res.body!, UserSettingsCategory.LEARNER_PROFILE);

                return {
                    practicalVsTheoretical: this.getSettingValue(settings, 'practicalVsTheoretical'),
                    creativeVsFocused: this.getSettingValue(settings, 'creativeVsFocused'),
                    followUpVsSummary: this.getSettingValue(settings, 'followUpVsSummary'),
                    briefVsDetailed: this.getSettingValue(settings, 'briefVsDetailed'),
                };
            }),
        );
    }

    /**
     * Update the current user's feedback learner profile
     */
    updateProfile(profile: FeedbackLearnerProfile): Observable<FeedbackLearnerProfile> {
        return this.http.put<FeedbackLearnerProfile>(this.resourceUrl, profile);
    }
}
