/**
 * Vitest tests for OrganizationManagementService.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';

import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { Organization } from 'app/admin/organization-management/organization.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { firstValueFrom } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { UserForRegistration } from 'app/shared-ui/user-registration-modal/user-for-registration.model';
import { AdminOrganizationApi } from 'app/openapi/api/admin-organization-api';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { deepClone } from 'app/foundation/util/deep-clone.util';

describe('Organization Service', () => {
    let service: OrganizationManagementService;
    let httpMock: HttpTestingController;
    let elemDefault: Organization;
    const resourceUrl = '/api/admin/organizations';
    const paging = { page: 0, pageSize: 10, searchTerm: 'a+b', sortedColumn: 'name', sortingOrder: SortingOrder.ASCENDING };

    afterEach(() => httpMock.verify());

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: Router, useValue: { navigate: () => {} } },
                LocalStorageService,
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
            ],
        });
        service = TestBed.inject(OrganizationManagementService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new Organization();
        elemDefault.id = 0;
        elemDefault.name = 'test';
        elemDefault.shortName = 'test';
        elemDefault.url = 'https://example.org';
        elemDefault.description = 'Test organization';
        elemDefault.emailPattern = '.*@test';
    });

    it('should return an Organization', async () => {
        const setTitle = vi.spyOn(TestBed.inject(EntityTitleService), 'setTitle');
        const resultPromise = firstValueFrom(service.getOrganizationById(0));

        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/0` });
        req.flush(elemDefault);

        const result = await resultPromise;
        expect(result).toMatchObject(elemDefault);
        expect(result.numberOfUsers).toBeUndefined();
        expect(result.numberOfCourses).toBeUndefined();
        expect(setTitle).toHaveBeenCalledWith(EntityType.ORGANIZATION, [0], elemDefault.name);
    });

    it('should return all Organizations a course is assigned to', async () => {
        const returnElement = createTestReturnElement();
        const resultPromise = firstValueFrom(service.getOrganizationsByCourse(1));

        // Asserting the URL and not only the method: this endpoint lives on the account resource, and a
        // method-only expectation let a stale legacy prefix through unnoticed.
        const req = httpMock.expectOne({ method: 'GET', url: 'api/account/organizations/courses/1' });
        req.flush(returnElement);

        const result = await resultPromise;
        expect(result).toEqual(returnElement);
    });

    it('should return all Organizations a user is assigned to', async () => {
        const returnElement = createTestReturnElement();
        const setTitle = vi.spyOn(TestBed.inject(EntityTitleService), 'setTitle');
        const resultPromise = firstValueFrom(service.getOrganizationsByUser(1));

        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/users/1` });
        req.flush(returnElement);

        const result = await resultPromise;
        expect(result).toEqual(returnElement);
        expect(setTitle.mock.calls).toEqual(returnElement.map((org) => [EntityType.ORGANIZATION, [org.id], org.name]));
    });

    it('should add a new Organization', async () => {
        const organization = deepClone(elemDefault);
        delete organization.id;
        const resultPromise = firstValueFrom(service.add(organization));

        const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
        expect(JSON.parse(req.request.serializeBody() as string)).toEqual(organization);
        req.flush(elemDefault);

        const result = await resultPromise;
        expect(result.body).toMatchObject(elemDefault);
    });

    it('should update an Organization', async () => {
        const updatedElem = deepClone(elemDefault);
        updatedElem.name = 'updated';
        const resultPromise = firstValueFrom(service.update(updatedElem));

        const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/0` });
        expect(JSON.parse(req.request.serializeBody() as string)).toEqual({
            id: 0,
            name: 'updated',
            shortName: 'test',
            url: 'https://example.org',
            description: 'Test organization',
            emailPattern: '.*@test',
        });
        req.flush(updatedElem);

        const result = await resultPromise;
        expect(result.body).toMatchObject(updatedElem);
    });

    it('should delete an Organization', async () => {
        const resultPromise = firstValueFrom(service.deleteOrganization(elemDefault.id!));

        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/0` });
        req.flush(null, { status: 200, statusText: 'OK' });

        const result = await resultPromise;
        expect(result.status).toBe(200);
        expect(result.body).toBeNull();
    });

    it('should add a user to an Organization', async () => {
        const login = 'first.last@example.org';
        const resultPromise = firstValueFrom(service.addUserToOrganization(elemDefault.id!, login));

        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/0/users/first.last%40example.org` });
        expect(req.request.body).toBeNull();
        req.flush(null, { status: 200, statusText: 'OK', headers: { 'X-artemisApp-params': login } });

        const result = await resultPromise;
        expect(result.status).toBe(200);
        expect(result.body).toBeNull();
        expect(result.headers.get('X-artemisApp-params')).toBe(login);
    });

    it('should delete a user from an Organization', async () => {
        const login = 'first.last@example.org';
        const resultPromise = firstValueFrom(service.removeUserFromOrganization(elemDefault.id!, login));

        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/0/users/first.last%40example.org` });
        req.flush(null, { status: 200, statusText: 'OK' });

        const result = await resultPromise;
        expect(result.status).toBe(200);
        expect(result.body).toBeNull();
    });

    it('should bulk-add users to an organization in one request', async () => {
        const resultPromise = firstValueFrom(service.addUsersToOrganization(elemDefault.id!, ['user1', 'user2']));

        const req = httpMock.expectOne((r) => r.url.endsWith(`${elemDefault.id}/users`) && r.method === 'POST');
        expect(req.request.body).toEqual({ logins: ['user1', 'user2'] });
        req.flush(null, { status: 200, statusText: 'OK' });

        const result = await resultPromise;
        expect(result.status).toBe(200);
        expect(result.body).toBeNull();
    });

    it('should search users for organization registration and map isRegistered flag', async () => {
        const orgId = 42;
        const mockUsers: UserForRegistration[] = [
            { id: 1, login: 'member', name: 'Member User', isRegistered: true },
            { id: 2, login: 'nonmember', name: 'Non Member', isRegistered: false },
        ];

        const resultPromise = firstValueFrom(service.searchUsersForOrganizationRegistration(orgId, 'member', 0, 10));

        const req = httpMock.expectOne((r) => pathname(r.urlWithParams) === `${resourceUrl}/${orgId}/users/search` && r.method === 'GET');
        expect(Object.fromEntries(new URL(req.request.urlWithParams, 'https://artemis.example').searchParams)).toEqual({
            loginOrName: 'member',
            pageIndex: '0',
            pageSize: '10',
        });
        req.flush(mockUsers, { headers: { 'X-Total-Count': '2' } });

        const result = await resultPromise;
        expect(result.content).toHaveLength(2);
        expect(result.totalElements).toBe(2);
        expect(result.content[0].isRegistered).toBe(true);
        expect(result.content[1].isRegistered).toBe(false);
    });

    it('should let the server apply registration search defaults', async () => {
        const resultPromise = firstValueFrom(TestBed.inject(AdminOrganizationApi).searchUsersForOrganizationRegistration(42, 'a+b'));
        const req = httpMock.expectOne((r) => pathname(r.urlWithParams) === `${resourceUrl}/42/users/search` && r.method === 'GET');
        expect(Object.fromEntries(new URL(req.request.urlWithParams, 'https://artemis.example').searchParams)).toEqual({ loginOrName: 'a+b' });
        req.flush([], { headers: { 'X-Total-Count': '0' } });
        expect((await resultPromise).body).toEqual([]);
    });

    it('should add a course to an Organization', async () => {
        const resultPromise = firstValueFrom(service.addCourseToOrganization(7, 42));

        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/7/courses/42` });
        expect(req.request.body).toBeNull();
        req.flush(null, { status: 200, statusText: 'OK' });

        const result = await resultPromise;
        expect(result.status).toBe(200);
        expect(result.body).toBeNull();
    });

    it('should remove a course from an Organization', async () => {
        const resultPromise = firstValueFrom(service.removeCourseFromOrganization(7, 42));

        const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/7/courses/42` });
        req.flush(null, { status: 200, statusText: 'OK' });

        const result = await resultPromise;
        expect(result.status).toBe(200);
        expect(result.body).toBeNull();
    });

    it.each([false, true])('should page organizations with withCounts=%s and cache titles', async (withCounts) => {
        const setTitle = vi.spyOn(TestBed.inject(EntityTitleService), 'setTitle');
        const resultPromise = firstValueFrom(service.getOrganizations(paging, withCounts));
        const req = httpMock.expectOne((r) => pathname(r.urlWithParams) === resourceUrl && r.method === 'GET');
        expect(Object.fromEntries(new URL(req.request.urlWithParams, 'https://artemis.example').searchParams)).toEqual({
            page: '0',
            pageSize: '10',
            searchTerm: 'a+b',
            sortedColumn: 'name',
            sortingOrder: 'ASCENDING',
            withCounts: String(withCounts),
        });
        const organization = withCounts ? { id: 3, name: 'Organization', numberOfUsers: 0, numberOfCourses: 2 } : { id: 3, name: 'Organization' };
        req.flush([organization], { headers: { 'X-Total-Count': '12', Link: '</api/admin/organizations?page=1>; rel="next"' } });
        expect(await resultPromise).toEqual({ content: [organization], totalElements: 12 });
        expect(setTitle).toHaveBeenCalledWith(EntityType.ORGANIZATION, [3], 'Organization');
    });

    it('should page member projections', async () => {
        const resultPromise = firstValueFrom(service.getOrganizationUsers(7, paging));
        const req = httpMock.expectOne((r) => pathname(r.urlWithParams) === `${resourceUrl}/7/users` && r.method === 'GET');
        expect(new URL(req.request.urlWithParams, 'https://artemis.example').searchParams.get('sortedColumn')).toBe('name');
        const member = { id: 1, login: 'testuser', name: 'Test User', email: 'test@example.org' };
        req.flush([member], { headers: { 'X-Total-Count': '12' } });
        const result = await resultPromise;
        expect(result.content).toMatchObject([member]);
        expect(result.totalElements).toBe(12);
    });

    it('should page course projections and preserve an empty result', async () => {
        const resultPromise = firstValueFrom(service.getOrganizationCourses(7, paging));
        const req = httpMock.expectOne((r) => pathname(r.urlWithParams) === `${resourceUrl}/7/courses` && r.method === 'GET');
        expect(new URL(req.request.urlWithParams, 'https://artemis.example').searchParams.get('searchTerm')).toBe('a+b');
        req.flush([]);
        expect(await resultPromise).toEqual({ content: [], totalElements: 0 });
    });

    it('should read the admin title as raw text', async () => {
        const resultPromise = firstValueFrom(TestBed.inject(AdminOrganizationApi).getOrganizationTitle(7));
        const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/7/title` });
        expect(req.request.responseType).toBe('text');
        req.flush('Research & Teaching', { headers: { 'Content-Type': 'text/plain' } });
        expect(await resultPromise).toBe('Research & Teaching');
    });

    function pathname(url: string): string {
        return new URL(url, 'https://artemis.example').pathname;
    }

    function createTestReturnElement() {
        const elem2 = new Organization();
        elem2.id = 1;
        elem2.name = 'test2';
        elem2.shortName = 'test2';
        return [elemDefault, elem2];
    }
});
