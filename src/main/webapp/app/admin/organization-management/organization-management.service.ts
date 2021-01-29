import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';

@Injectable({ providedIn: 'root' })
export class OrganizationManagementService {
    public resourceUrl = SERVER_API_URL + 'api/organizations';
    constructor(private http: HttpClient) {}

    /**
     * Send GET request to retrieve all organizations
     */
    getOrganizations(): Observable<any> {
        return this.http.get(this.resourceUrl + '/all');
    }

    /**
     * Send GET request to retrieve all organizations with their number of
     * users and courses
     */
    getNumberOfUsersAndCoursesOfOrganizations(): Observable<any> {
        return this.http.get(this.resourceUrl + '/allCount');
    }

    /**
     * Send GET request to retrieve an organization by its Id
     * @param organizationId
     */
    getOrganizationById(organizationId: number): Observable<any> {
        return this.http.get(`${this.resourceUrl}/${organizationId}`);
    }

    /**
     * Send GET request to retrieve an organization by Id with
     * its list of users and courses
     * @param organizationId
     */
    getOrganizationByIdWithUsersAndCourses(organizationId: number): Observable<any> {
        return this.http.get(`${this.resourceUrl}/${organizationId}/full`);
    }

    /**
     * Send a DELETE request to remove an organization
     * @param organizationId the id of the organization to remove
     */
    deleteOrganization(organizationId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/delete/${organizationId}`, { observe: 'response' });
    }
}
