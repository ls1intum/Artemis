import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map, tap } from 'rxjs';

import { Organization } from 'app/core/shared/entities/organization.model';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';

export interface PagedResponse<T> {
    data: T[];
    total: number;
}

@Injectable({ providedIn: 'root' })
export class OrganizationManagementService {
    private http = inject(HttpClient);
    private entityTitleService = inject(EntityTitleService);

    public resourceUrl = 'api/core/organizations';
    public adminResourceUrl = 'api/core/admin/organizations';

    /**
     * Send GET request to retrieve all organizations
     */
    getAllOrganizations(): Observable<Organization[]> {
        return this.http.get<Organization[]>(this.adminResourceUrl).pipe(tap((orgs) => orgs?.forEach(this.sendTitlesToEntityTitleService.bind(this))));
    }

    getOrganizations(params: {
        page?: number;
        pageSize?: number;
        sort?: string; // e.g. 'name,asc'
    }): Observable<PagedResponse<Organization>> {
        return this.http
            .get<Organization[]>(this.adminResourceUrl, {
                params: {
                    page: params.page ?? 0,
                    pageSize: params.pageSize ?? 20,
                    ...(params.sort ? { sort: params.sort } : {}),
                },
                observe: 'response',
            })
            .pipe(
                tap((res) => res.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))),
                map((res) => ({
                    data: res.body ?? [],
                    total: Number(res.headers.get('X-Total-Count') ?? 0),
                })),
            );
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
    removeUserFromOrganization(organizationId: number, userLogin: string): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.adminResourceUrl}/${organizationId}/users/${userLogin}`, { observe: 'response' });
    }

    /**
     * Send a POST request to add a user to an organization
     * @param organizationId the id of the organization to add the user to
     * @param userLogin the user to add
     */
    addUserToOrganization(organizationId: number, userLogin: string): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.adminResourceUrl}/${organizationId}/users/${userLogin}`, {}, { observe: 'response' });
    }

    private sendTitlesToEntityTitleService(org: Organization | undefined | null) {
        this.entityTitleService.setTitle(EntityType.ORGANIZATION, [org?.id], org?.name);
    }
}
