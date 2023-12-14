import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ScienceEventDTO, ScienceEventType } from 'app/shared/science/science.model';
import { ScienceSettingsService } from 'app/shared/user-settings/science-settings/science-settings.service';

@Injectable({ providedIn: 'root' })
export class ScienceService {
    private resourceURL = 'api';

    constructor(
        private httpClient: HttpClient,
        private scienceSettingsService: ScienceSettingsService,
    ) {
        this.scienceSettingsService.getScienceSettingsUpdates();
    }

    eventLoggingActive() {
        return this.scienceSettingsService.eventLoggingAllowed();
    }

    logEvent(type: ScienceEventType, resourceId?: number): Observable<HttpResponse<void>> {
        const event = new ScienceEventDTO();
        event.type = type;
        if (resourceId) {
            event.resourceId = resourceId;
        }
        return this.httpClient.put<void>(`${this.resourceURL}/science`, event, { observe: 'response' });
    }
}
