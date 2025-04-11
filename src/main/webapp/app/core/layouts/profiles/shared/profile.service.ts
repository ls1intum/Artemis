import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ProfileInfo } from '../profile-info.model';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { BrowserFingerprintService } from 'app/core/account/fingerprint/browser-fingerprint.service';

@Injectable({ providedIn: 'root' })
export class ProfileService {
    private http = inject(HttpClient);
    private featureToggleService = inject(FeatureToggleService);
    private browserFingerprintService = inject(BrowserFingerprintService);

    private readonly profileInfoSignal = signal<ProfileInfo>({} as ProfileInfo);

    /** Expose read-only signal to components which want to use the information */
    public readonly profileInfo = this.profileInfoSignal();

    constructor() {
        this.loadProfileInfo(); // no condition needed — the app always needs it
    }

    /**
     * Check if the given profile is active.
     * @param profile The profile to check.
     */
    private readonly isProfileActiveSignal = computed(() => {
        return (profile: string) => this.profileInfo.activeProfiles?.includes(profile) ?? false;
    });

    public isProfileActive(profile: string) {
        return this.isProfileActiveSignal()(profile);
    }

    public isModuleFeatureActive(feature: string) {
        return this.profileInfo.activeModuleFeatures?.includes(feature) ?? false;
    }

    // TODO: convert ProfileInfo to exactly reflect the server API response so that no cumbersome mapping is needed
    //  this involves changing 'test-server' in the server to 'testServer', etc.
    /** Trigger data loading once */
    private loadProfileInfo(): void {
        this.http.get<ProfileInfo>('management/info').subscribe((info) => {
            this.profileInfoSignal.set(info);
            this.featureToggleService.initializeFeatureToggles(info.features);
            this.browserFingerprintService.initialize(info.studentExamStoreSessionData);
        });
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
