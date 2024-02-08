import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ScienceEventDTO, ScienceEventType } from 'app/shared/science/science.model';
import { ScienceSettingsService } from 'app/shared/user-settings/science-settings/science-settings.service';
import { AccountService } from 'app/core/auth/account.service';

@Injectable({ providedIn: 'root' })
export class ScienceService {
    private resourceURL = 'api';

    constructor(
        private httpClient: HttpClient,
        private scienceSettingsService: ScienceSettingsService,
        private accountService: AccountService,
    ) {
        this.scienceSettingsService.getScienceSettingsUpdates();
        this.accountService.getAuthenticationState().subscribe((user) => this.onUserIdentityChange(user));
    }

    private onUserIdentityChange(user: any): void {
        if (user) {
            this.scienceSettingsService.refreshScienceSettings();
        }
    }

    eventLoggingActive() {
        return this.scienceSettingsService.eventLoggingAllowed();
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
