import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { Organization } from 'app/entities/organization.model';
import { OrganizationCountDto } from 'app/admin/organization-management/organization-count-dto.model';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';

@Injectable({ providedIn: 'root' })
export class OrganizationManagementService {
    public resourceUrl = SERVER_API_URL + 'api/organizations';
    public adminResourceUrl = SERVER_API_URL + 'api/admin/organizations';

    constructor(private http: HttpClient, private entityTitleService: EntityTitleService) {}

    /**
     * Send GET request to retrieve all organizations
     */
    getOrganizations(): Observable<Organization[]> {
        return this.http.get<Organization[]>(this.adminResourceUrl).pipe(tap((orgs) => orgs?.forEach(this.sendTitlesToEntityTitleService.bind(this))));
    }

    /**
     * Send GET request to retrieve the number of users and courses of
     * all organizations
     */
    getNumberOfUsersAndCoursesOfOrganizations(): Observable<OrganizationCountDto[]> {
        return this.http.get<OrganizationCountDto[]>(this.adminResourceUrl + '/count-all');
    }

    /**
     * Send GET request to retrieve an organization by its Id
     * @param organizationId
     */
    getOrganizationById(organizationId: number): Observable<Organization> {
        return this.http.get(`${this.adminResourceUrl}/${organizationId}`).pipe(tap((org) => this.sendTitlesToEntityTitleService(org)));
    }

    /**
     * Send GET request to retrieve an organization by Id with
     * its list of users and courses
     * @param organizationId
     */
    getOrganizationByIdWithUsersAndCourses(organizationId: number): Observable<Organization> {
        return this.http.get(`${this.adminResourceUrl}/${organizationId}/full`).pipe(tap((org) => this.sendTitlesToEntityTitleService(org)));
    }

    /**
     * Send GET request to retrieve all organizations of a given course
     * @param courseId the id of the course to retrieve the organizations from
     */
    getOrganizationsByCourse(courseId: number): Observable<Organization[]> {
        return this.http.get<Organization[]>(`${this.resourceUrl}/courses/${courseId}`).pipe(tap((orgs) => orgs?.forEach(this.sendTitlesToEntityTitleService.bind(this))));
    }

    /**
     * Send GET request to retrieve all organizations of a given user
     * @param userId the id of the user to retrieve the organizations from
     */
    getOrganizationsByUser(userId: number): Observable<Organization[]> {
        return this.http.get<Organization[]>(`${this.adminResourceUrl}/users/${userId}`).pipe(tap((orgs) => orgs?.forEach(this.sendTitlesToEntityTitleService.bind(this))));
    }

    /**
     * Send PUT request to update an organization
     * @param organization the organization to update
     */
    update(organization: Organization): Observable<HttpResponse<Organization>> {
        return this.http.put<Organization>(`${this.adminResourceUrl}/${organization.id}`, organization, { observe: 'response' });
    }

    /**
     * Send POST request to add a new organization
     * @param organization the organization to add
     */
    add(organization: Organization): Observable<HttpResponse<Organization>> {
        return this.http.post<Organization>(`${this.adminResourceUrl}`, organization, { observe: 'response' });
    }

    /**
     * Send a DELETE request to remove an organization
     * @param organizationId the id of the organization to remove
     */
    deleteOrganization(organizationId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.adminResourceUrl}/${organizationId}`, { observe: 'response' });
    }

    /**
     * Send a DELETE request to remove a user from an organization
     * @param organizationId the id of the organization to remove the user from
     * @param userLogin the user to remove
     */
    removeUserFromOrganization(organizationId: number, userLogin: String): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.adminResourceUrl}/${organizationId}/users/${userLogin}`, { observe: 'response' });
    }

    /**
     * Send a POST request to add a user to an organization
     * @param organizationId the id of the organization to add the user to
     * @param userLogin the user to add
     */
    addUserToOrganization(organizationId: number, userLogin: String): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.adminResourceUrl}/${organizationId}/users/${userLogin}`, {}, { observe: 'response' });
    }

    private sendTitlesToEntityTitleService(org: Organization | undefined | null) {
        this.entityTitleService.setTitle(EntityType.ORGANIZATION, [org?.id], org?.name);
    }
}
