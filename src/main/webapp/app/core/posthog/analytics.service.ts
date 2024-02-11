import { Injectable } from '@angular/core';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import posthog from 'posthog-js';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
    private environment: string;

    /**
     * Initialize PostHog with profile information.
     * @param profileInfo
     */
    public async initPostHog(profileInfo: ProfileInfo): Promise<void> {
        if (!profileInfo || !profileInfo.postHog) {
            return;
        }

        if (profileInfo.postHog) {
            posthog.init(profileInfo.postHog?.token, {
                api_host: profileInfo.postHog.host,
            });
        }
    }
}
