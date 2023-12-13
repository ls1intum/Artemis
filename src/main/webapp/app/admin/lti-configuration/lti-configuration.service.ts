import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LtiPlatformConfiguration } from 'app/admin/lti-configuration/lti-configuration.model';

@Injectable({ providedIn: 'root' })
export class LtiConfigurationService {
    constructor(private http: HttpClient) {}

    /**
     * Sends a GET request to retrieve all lti platform configurations
     */
    findAll(): Observable<LtiPlatformConfiguration[]> {
        return this.http.get<LtiPlatformConfiguration[]>('api/lti-platforms');
    }

    /**
     * updates the online course configuration of a course using a PUT request
     * @param courseId - the id of the course to be updated
     * @param onlineCourseConfiguration - the updates to the online course configuration
     */
    updateLtiPlatformConfiguration(ltiPlatformConfiguration: LtiPlatformConfiguration): Observable<HttpResponse<any>> {
        return this.http.put<LtiPlatformConfiguration>(`api/admin/lti-platform`, ltiPlatformConfiguration, { observe: 'response' });
    }

    /**
     * Delete a lti platform on the server using a DELETE request.
     * @param platformId The platform id.
     */
    deleteLtiPlatform(platformId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`api/admin/lti-platform/${platformId}`, { observe: 'response' });
    }
}
