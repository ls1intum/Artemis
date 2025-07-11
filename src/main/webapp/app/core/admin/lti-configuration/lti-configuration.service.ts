import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LtiPlatformConfiguration } from 'app/lti/shared/entities/lti-configuration.model';
import { createRequestOption } from 'app/shared/util/request.util';

@Injectable({ providedIn: 'root' })
export class LtiConfigurationService {
    private http = inject(HttpClient);

    /**
     * Sends a GET request to retrieve all lti platform configurations
     */
    query(req?: any): Observable<HttpResponse<LtiPlatformConfiguration[]>> {
        const params: HttpParams = createRequestOption(req);
        return this.http.get<LtiPlatformConfiguration[]>('api/lti/lti-platforms', {
            params,
            observe: 'response',
        });
    }

    /**
     * Adds an LTI platform configuration.
     * @param ltiPlatformConfiguration The configuration to add.
     * @return Observable of the HTTP response.
     */
    addLtiPlatformConfiguration(ltiPlatformConfiguration: LtiPlatformConfiguration): Observable<HttpResponse<any>> {
        return this.http.post<LtiPlatformConfiguration>(`api/lti/admin/lti-platform`, ltiPlatformConfiguration, { observe: 'response' });
    }

    /**
     * Updates an LTI platform configuration.
     * @param ltiPlatformConfiguration The configuration to update.
     * @return Observable of the HTTP response.
     */
    updateLtiPlatformConfiguration(ltiPlatformConfiguration: LtiPlatformConfiguration): Observable<HttpResponse<any>> {
        return this.http.put<LtiPlatformConfiguration>(`api/lti/admin/lti-platform`, ltiPlatformConfiguration, { observe: 'response' });
    }

    /**
     * Deletes an LTI platform on the server using a DELETE request.
     * @param platformId The platform id.
     */
    deleteLtiPlatform(platformId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`api/lti/admin/lti-platform/${platformId}`, { observe: 'response' });
    }

    getLtiPlatformById(platformId: number): Observable<LtiPlatformConfiguration> {
        return this.http.get<LtiPlatformConfiguration>(`api/lti/admin/lti-platform/${platformId}`);
    }
}
