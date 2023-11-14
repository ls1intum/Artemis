import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LtiPlatformConfiguration } from 'app/admin/lti-configuration/lti-configuration.model';

@Injectable({ providedIn: 'root' })
export class LtiConfigurationService {
    constructor(private http: HttpClient) {}

    /**
     * Sends a GET request to retrieve all lti platform configurations
     */
    findAll(): Observable<LtiPlatformConfiguration[]> {
        return this.http.get<LtiPlatformConfiguration[]>('api/admin/ltiplatforms');
    }
}
