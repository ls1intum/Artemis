import { Injectable } from '@angular/core';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { posthog } from 'posthog-js';

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
    /**
     * Initialize Analytics with profile information.
     * @param profileInfo
     */
    public async initAnalytics(profileInfo: ProfileInfo): Promise<void> {
        if (!profileInfo || !profileInfo.postHog) {
            return;
        }

        posthog.init(profileInfo.postHog.token, {
            api_host: profileInfo.postHog.host,
        });
    }
}
