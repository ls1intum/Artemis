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

    /** Internal mutable reference */
    private profileInfo: ProfileInfo;

    // Should only be called once by the app initializer
    public async loadProfileInfo(): Promise<void> {
        this.profileInfo = await firstValueFrom(this.http.get<ProfileInfo>('management/info'));
        this.featureToggleService.initializeFeatureToggles(this.profileInfo.features);
        this.browserFingerprintService.initialize(this.profileInfo.studentExamStoreSessionData);
    }

    /** Public readonly getter */
    public getProfileInfo(): ProfileInfo {
        return this.profileInfo;
    }

    public isProfileActive(profile: string) {
        return this.profileInfo.activeProfiles?.includes(profile);
    }

    public isModuleFeatureActive(feature: string) {
        return this.profileInfo.activeModuleFeatures?.includes(feature) ?? false;
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
