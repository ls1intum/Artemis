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

    private readonly profileInfo = signal<ProfileInfo | undefined>(undefined);

    /** Expose read-only signal to components which want to use the information */
    readonly profileInfoSignal = this.profileInfo;

    constructor() {
        this.loadProfileInfo(); // no condition needed — the app always needs it
    }

    /**
     * Check if the given profile is active.
     * @param profile The profile to check.
     */
    readonly isProfileActive = computed(() => {
        const info = this.profileInfo();
        return (profile: string) => info?.activeProfiles?.includes(profile) ?? false;
    });

    // TODO: convert ProfileInfo to exactly reflect the server API response so that no cumbersome mapping is needed
    //  this involves changing 'test-server' in the server to 'testServer', etc.
    /** Trigger data loading once */
    private loadProfileInfo(): void {
        this.http.get<ProfileInfo>('management/info').subscribe((info) => {
            this.profileInfo.set(info);
            this.featureToggleService.initializeFeatureToggles(info.features);
            this.browserFingerprintService.initialize(info.studentExamStoreSessionData);
        });
    }
}
