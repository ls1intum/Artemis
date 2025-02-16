import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ScienceEventDTO, ScienceEventType } from 'app/shared/science/science.model';
import { ScienceSettingsService } from 'app/shared/user-settings/science-settings/science-settings.service';
import { AccountService } from 'app/core/auth/account.service';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

@Injectable({ providedIn: 'root' })
export class ScienceService {
    private httpClient = inject(HttpClient);
    private featureToggleService = inject(FeatureToggleService);
    private scienceSettingsService = inject(ScienceSettingsService);
    private accountService = inject(AccountService);

    private resourceURL = 'api';

    private featureToggleActive = false;

    constructor() {
        this.scienceSettingsService.getScienceSettingsUpdates();
        this.accountService.getAuthenticationState().subscribe((user) => this.onUserIdentityChange(user));
        this.featureToggleService.getFeatureToggleActive(FeatureToggle.Science).subscribe((active) => {
            this.featureToggleActive = active;
        });
    }

    private onUserIdentityChange(user: any): void {
        if (user) {
            this.scienceSettingsService.refreshScienceSettings();
        }
    }

    eventLoggingActive() {
        return this.featureToggleActive && this.scienceSettingsService.eventLoggingAllowed();
    }

    logEvent(type: ScienceEventType, resourceId?: number): void {
        if (!this.eventLoggingActive()) {
            return;
        }
        const event = new ScienceEventDTO();
        event.type = type;
        if (resourceId) {
            event.resourceId = resourceId;
        }
        this.httpClient.put<void>(`${this.resourceURL}/science`, event, { observe: 'response' }).subscribe();
    }
}
