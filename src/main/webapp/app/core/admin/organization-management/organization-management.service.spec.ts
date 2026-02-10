/**
 * Vitest tests for OrganizationManagementService.
 */
import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';

import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Organization } from 'app/core/shared/entities/organization.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { User } from 'app/core/user/user.model';
import { OrganizationCountDto } from 'app/core/admin/organization-management/organization-count-dto.model';
import { firstValueFrom } from 'rxjs';

describe('Organization Service', () => {
    setupTestBed({ zoneless: true });

    let service: OrganizationManagementService;
    let httpMock: HttpTestingController;
    let elemDefault: Organization;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: Router, useValue: { navigate: () => {} } }, LocalStorageService, SessionStorageService],
        });
        service = TestBed.inject(OrganizationManagementService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new Organization();
        elemDefault.id = 0;
        elemDefault.name = 'test';
        elemDefault.shortName = 'test';
        elemDefault.emailPattern = '.*@test';
    });

    it('should return an Organization', async () => {
        const resultPromise = firstValueFrom(service.getOrganizationById(0));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(elemDefault);

        const result = await resultPromise;
        expect(result).toMatchObject(elemDefault);
    });

    it('should return an Organization with Users and Courses', async () => {
        const resultPromise = firstValueFrom(service.getOrganizationByIdWithUsersAndCourses(0));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(elemDefault);

        const result = await resultPromise;
        expect(result).toEqual(elemDefault);
    });

    it('should return all organizations', async () => {
        const returnElement = createTestReturnElement();
        const resultPromise = firstValueFrom(service.getOrganizations());

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnElement);

        const result = await resultPromise;
        expect(result).toEqual(returnElement);
    });

    it('should return all Organizations a course is assigned to', async () => {
        const returnElement = createTestReturnElement();
        const resultPromise = firstValueFrom(service.getOrganizationsByCourse(1));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnElement);

        const result = await resultPromise;
        expect(result).toEqual(returnElement);
    });

    it('should return all Organizations a user is assigned to', async () => {
        const returnElement = createTestReturnElement();
        const resultPromise = firstValueFrom(service.getOrganizationsByUser(1));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnElement);

        const result = await resultPromise;
        expect(result).toEqual(returnElement);
    });

    it('should add a new Organization', async () => {
        const resultPromise = firstValueFrom(service.add(elemDefault));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(elemDefault);

        const result = await resultPromise;
        expect(result.body).toMatchObject(elemDefault);
    });

    it('should update an Organization', async () => {
        const updatedElem = Object.assign(
            {
                name: 'updated',
            },
            elemDefault,
        );
        const resultPromise = firstValueFrom(service.update(updatedElem));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(updatedElem);

        const result = await resultPromise;
        expect(result.body).toMatchObject(updatedElem);
    });

    it('should delete an Organization', async () => {
        const resultPromise = firstValueFrom(service.deleteOrganization(elemDefault.id!));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });

        const result = await resultPromise;
        expect(result.ok).toBe(true);
    });

    it('should add a user to an Organization', async () => {
        const user1 = new User();
        user1.login = 'testUser';
        const resultPromise = firstValueFrom(service.addUserToOrganization(elemDefault.id!, user1.login));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(elemDefault);

        const result = await resultPromise;
        expect(result.ok).toBe(true);
    });

    it('should delete a user from an Organization', async () => {
        const user1 = new User();
        user1.login = 'testUser';
        const resultPromise = firstValueFrom(service.removeUserFromOrganization(elemDefault.id!, user1.login));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });

        const result = await resultPromise;
        expect(result.ok).toBe(true);
    });

    function createTestReturnElement() {
        const elem2 = new Organization();
        elem2.id = 1;
        elem2.name = 'test2';
        elem2.shortName = 'test2';
        return [elemDefault, elem2];
    }
});
