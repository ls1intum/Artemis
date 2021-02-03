import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { Organization } from 'app/entities/organization.model';

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
     * Send PUT request to update an organization
     * @param organization the organization to update
     */
    update(organization: Organization): Observable<HttpResponse<Organization>> {
        return this.http.put<Organization>(`${this.resourceUrl}/update`, organization, { observe: 'response' });
    }

    /**
     * Send POST request to add a new organization
     * @param organization the organization to add
     */
    add(organization: Organization): Observable<HttpResponse<Organization>> {
        return this.http.post<Organization>(`${this.resourceUrl}/add`, organization, { observe: 'response' });
    }

    /**
     * Send a DELETE request to remove an organization
     * @param organizationId the id of the organization to remove
     */
    deleteOrganization(organizationId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/delete/${organizationId}`, { observe: 'response' });
    }

    /**
     * Send a DELETE request to remove a user from an oranization
     * @param organizationId the id of the organization to remove the user from
     * @param userLogin the user to remove
     */
    removeUserFromOrganization(organizationId: number, userLogin: String): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/user/${userLogin}/organization/${organizationId}`, { observe: 'response' });
    }

    /**
     * Send a POST request to add a user to an organization
     * @param organizationId the id of the organization to add the user to
     * @param userLogin the user to add
     */
    addUserToOrganization(organizationId: number, userLogin: String): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.resourceUrl}/user/${userLogin}/organization/${organizationId}`, {}, { observe: 'response' });
    }
}
