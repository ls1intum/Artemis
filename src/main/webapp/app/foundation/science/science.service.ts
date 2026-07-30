import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ScienceEventDTO, ScienceEventType } from 'app/foundation/science/science.model';
import { AccountService } from 'app/core/auth/account.service';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ScienceSettingsService } from 'app/account/user/settings/science-settings/science-settings.service';
import { User } from 'app/account/user/user.model';
import { Router } from '@angular/router';

@Injectable({ providedIn: 'root' })
export class ScienceService {
    private httpClient = inject(HttpClient);
    private featureToggleService = inject(FeatureToggleService);
    private scienceSettingsService = inject(ScienceSettingsService);
    private accountService = inject(AccountService);
    private router = inject(Router);

    private resourceURL = 'api/atlas';

    private featureToggleActive = false;

    constructor() {
        this.scienceSettingsService.getScienceSettingsUpdates();
        this.accountService.getAuthenticationState().subscribe((user) => this.onUserIdentityChange(user));
        this.featureToggleService.getFeatureToggleActive(FeatureToggle.Science).subscribe((active) => {
            this.featureToggleActive = active;
        });
    }

    private onUserIdentityChange(user: User | undefined): void {
        if (user) {
            this.scienceSettingsService.refreshScienceSettings();
        }
    }

    eventLoggingActive(courseId?: number) {
        return this.featureToggleActive && this.scienceSettingsService.eventLoggingAllowed(courseId);
    }

    logEvent(type: ScienceEventType, resourceId?: number): void {
        const courseId = this.inferCourseId();
        if (!this.eventLoggingActive(courseId)) {
            return;
        }
        const event = new ScienceEventDTO();
        event.type = type;
        event.courseId = courseId;
        if (resourceId) {
            event.resourceId = resourceId;
        }
        this.httpClient.put<void>(`${this.resourceURL}/science`, event, { observe: 'response' }).subscribe();
    }

    private inferCourseId(): number | undefined {
        const match = this.router.url.match(/\/(?:courses|course-management)\/(\d+)/);
        if (!match?.[1]) {
            return undefined;
        }
        return Number(match[1]);
    }
}
