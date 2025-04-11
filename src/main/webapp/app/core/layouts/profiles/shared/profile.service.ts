import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ProfileInfo } from '../profile-info.model';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ProfileService {
    private http = inject(HttpClient);
    private featureToggleService = inject(FeatureToggleService);
    private browserFingerprintService = inject(BrowserFingerprintService);

    /** Expose read-only signal to components which want to use the information */
    public profileInfo: ProfileInfo;

    public isProfileActive(profile: string) {
        return this.profileInfo.activeProfiles?.includes(profile);
    }

    public isModuleFeatureActive(feature: string) {
        return this.profileInfo.activeModuleFeatures?.includes(feature) ?? false;
    }

    async loadProfileInfo(): Promise<void> {
        const info = await firstValueFrom(this.http.get<ProfileInfo>('management/info'));
        this.profileInfo = info;
        this.featureToggleService.initializeFeatureToggles(info.features);
        this.browserFingerprintService.initialize(info.studentExamStoreSessionData);
    }

    public isDevelopment(): boolean {
        return this.isProfileActive('dev') ?? false;
    }

    public isProduction(): boolean {
        return this.isProfileActive('prod') ?? false;
    }

    public isTestServer(): boolean {
        return this.profileInfo.testServer ?? false;
    }
}
