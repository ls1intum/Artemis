import { Service, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, map, tap, throwError } from 'rxjs';

import { Organization } from 'app/admin/organization-management/organization.model';
import { Course } from 'app/course/shared/entities/course.model';
import { User } from 'app/account/user/user.model';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { PageableResult, SearchTermPageableSearch, toPageableResult } from 'app/foundation/pagination/pageable-table';
import { UserSearchResult } from 'app/shared-ui/user-registration-modal/user-for-registration.model';

import { AdminOrganizationApi } from 'app/openapi/api/admin-organization-api';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { OrganizationRequest } from 'app/openapi/model/organization-request';

@Service()
export class OrganizationManagementService {
    private readonly adminOrganizationApi = inject(AdminOrganizationApi);
    private http = inject(HttpClient);
    private entityTitleService = inject(EntityTitleService);

    public resourceUrl = 'api/account/organizations';

    /**
     * Send GET request to retrieve a paginated and filtered list of organizations
     * @param params     the search and pagination parameters including search term, page, and page size
     * @param withCounts whether to include aggregated user/course counts (default: false);
     *                   pass true to include user/course count data
     * @returns an observable emitting a pageable result containing the matching organizations and total element count
     */
    getOrganizations(params: SearchTermPageableSearch, withCounts = false): Observable<PageableResult<Organization>> {
        return this.adminOrganizationApi.getOrganizations(params.pageSize, params.searchTerm, params.page, params.sortingOrder, withCounts, params.sortedColumn).pipe(
            tap((res) => res.body?.forEach((org) => this.sendTitlesToEntityTitleService(org))),
            map(toPageableResult),
        );
    }

    /**
     * Send GET request to retrieve an organization by its Id
     * @param organizationId
     */
    getOrganizationById(organizationId: number): Observable<Organization> {
        return this.adminOrganizationApi.getOrganizationById(organizationId).pipe(tap((org) => this.sendTitlesToEntityTitleService(org)));
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
        return this.adminOrganizationApi.getAllOrganizationsByUser(userId).pipe(tap((orgs) => orgs?.forEach((org) => this.sendTitlesToEntityTitleService(org))));
    }

    /**
     * Send PUT request to update an organization
     * @param organization the organization to update
     */
    update(organization: Organization): Observable<HttpResponse<Organization>> {
        const request = this.organizationRequest(organization);
        if (!request || organization.id === undefined) {
            return throwError(() => new Error('Updating an organization requires its ID, name, short name and email pattern.'));
        }
        return this.adminOrganizationApi.updateOrganization(organization.id, request);
    }

    /**
     * Send POST request to add a new organization
     * @param organization the organization to add
     */
    add(organization: Organization): Observable<HttpResponse<Organization>> {
        const request = this.organizationRequest(organization);
        if (!request) {
            return throwError(() => new Error('Creating an organization requires a name, short name and email pattern.'));
        }
        return this.adminOrganizationApi.addOrganization(request);
    }

    /**
     * Send a DELETE request to remove an organization
     * @param organizationId the id of the organization to remove
     */
    deleteOrganization(organizationId: number): Observable<HttpResponse<void>> {
        return this.adminOrganizationApi.deleteOrganization(organizationId);
    }

    /**
     * Send a DELETE request to remove a user from an organization
     * @param organizationId the id of the organization to remove the user from
     * @param userLogin the user to remove
     */
    removeUserFromOrganization(organizationId: number, userLogin: string): Observable<HttpResponse<void>> {
        return this.adminOrganizationApi.removeUserFromOrganization(userLogin, organizationId);
    }

    /**
     * Send a POST request to add a user to an organization
     * @param organizationId the id of the organization to add the user to
     * @param userLogin the user to add
     */
    addUserToOrganization(organizationId: number, userLogin: string): Observable<HttpResponse<void>> {
        return this.adminOrganizationApi.addUserToOrganization(userLogin, organizationId);
    }

    /**
     * Send a POST request to add a course to an organization
     * @param organizationId the id of the organization to add the course to
     * @param courseId the id of the course to add
     */
    addCourseToOrganization(organizationId: number, courseId: number): Observable<HttpResponse<void>> {
        return this.adminOrganizationApi.addCourseToOrganization(courseId, organizationId);
    }

    /**
     * Send a DELETE request to remove a course from an organization
     * @param organizationId the id of the organization to remove the course from
     * @param courseId the id of the course to remove
     */
    removeCourseFromOrganization(organizationId: number, courseId: number): Observable<HttpResponse<void>> {
        return this.adminOrganizationApi.removeCourseFromOrganization(courseId, organizationId);
    }

    /**
     * Send GET request to retrieve a paginated list of users belonging to an organization
     * @param organizationId the id of the organization
     * @param params         the search and pagination parameters
     * @returns an observable emitting a pageable result containing the matching users and total element count
     */
    getOrganizationUsers(organizationId: number, params: SearchTermPageableSearch): Observable<PageableResult<User>> {
        return this.adminOrganizationApi
            .getOrganizationUsers(organizationId, params.pageSize, params.searchTerm, params.page, params.sortingOrder, params.sortedColumn)
            .pipe(map((response) => toPageableResult(response.clone({ body: response.body?.map((member) => hydrate(new User(), member)) ?? null }))));
    }

    /**
     * Send GET request to retrieve a paginated list of courses belonging to an organization
     * @param organizationId the id of the organization
     * @param params         the search and pagination parameters
     * @returns an observable emitting a pageable result containing the matching courses and total element count
     */
    getOrganizationCourses(organizationId: number, params: SearchTermPageableSearch): Observable<PageableResult<Course>> {
        return this.adminOrganizationApi
            .getOrganizationCourses(organizationId, params.pageSize, params.searchTerm, params.page, params.sortingOrder, params.sortedColumn)
            .pipe(map(toPageableResult));
    }

    /**
     * Send a single POST request to bulk-add multiple users to an organization
     *
     * @param organizationId the id of the organization
     * @param logins         the logins of the users to add
     */
    addUsersToOrganization(organizationId: number, logins: string[]): Observable<HttpResponse<void>> {
        return this.adminOrganizationApi.addUsersToOrganization(organizationId, { logins });
    }

    /**
     * Search for users to register to the given organization, matching the given login, name, email, or registration number term.
     * Results include already-registered users with {@code isRegistered: true}.
     *
     * @param organizationId the id of the organization
     * @param loginOrName    the search term
     * @param pageIndex      zero-based page index
     * @param pageSize       number of results per page
     * @returns an observable emitting a search result containing matching users and total count
     */
    searchUsersForOrganizationRegistration(organizationId: number, loginOrName: string, pageIndex: number, pageSize: number): Observable<UserSearchResult> {
        return this.adminOrganizationApi.searchUsersForOrganizationRegistration(organizationId, loginOrName, pageIndex, pageSize).pipe(map(toPageableResult));
    }

    private organizationRequest({ id, name, shortName, emailPattern, url, description, logoUrl }: Organization): OrganizationRequest | undefined {
        if (name === undefined || shortName === undefined || emailPattern === undefined) {
            return undefined;
        }
        return { id, name, shortName, emailPattern, url, description, logoUrl };
    }

    private sendTitlesToEntityTitleService(org: Organization | undefined | null) {
        this.entityTitleService.setTitle(EntityType.ORGANIZATION, [org?.id], org?.name);
    }
}
