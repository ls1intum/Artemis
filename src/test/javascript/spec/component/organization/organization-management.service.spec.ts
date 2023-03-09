import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { OrganizationCountDto } from 'app/admin/organization-management/organization-count-dto.model';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { User } from 'app/core/user/user.model';
import { Organization } from 'app/entities/organization.model';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

describe('Organization Service', () => {
    let service: OrganizationManagementService;
    let httpMock: HttpTestingController;
    let elemDefault: Organization;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
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
        service.getOrganizationById(0).subscribe((data) => expect(data).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify(elemDefault));
    });

    it('should return an Organization with Users and Courses', fakeAsync(() => {
        service.getOrganizationByIdWithUsersAndCourses(0).subscribe((data) => expect(data).toEqual(elemDefault));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(elemDefault);
        tick();
    }));

    it('should return all organizations', async () => {
        const returnElement = createTestReturnElement();
        service.getOrganizations().subscribe((data) => expect(data).toMatchObject({ body: returnElement }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify(returnElement));
    });

    it('should return number of users and courses of organization', fakeAsync(() => {
        const returnElement = new OrganizationCountDto();
        returnElement.numberOfCourses = 2;
        returnElement.numberOfUsers = 17;
        service.getNumberOfUsersAndCoursesOfOrganizations().subscribe((data) => expect(data).toEqual(returnElement));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnElement);
        tick();
    }));

    it('should return all Organizations a course is assigned to', async () => {
        const returnElement = createTestReturnElement();
        service.getOrganizationsByCourse(1).subscribe((data) => expect(data).toMatchObject({ body: returnElement }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify(elemDefault));
    });

    it('should return all Organizations a user is assigned to', async () => {
        const returnElement = createTestReturnElement();
        service.getOrganizationsByUser(1).subscribe((data) => expect(data).toMatchObject({ body: returnElement }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify(elemDefault));
    });

    it('should add a new Organization', async () => {
        service.add(elemDefault).subscribe((data) => expect(data).toMatchObject({ body: elemDefault }));
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(JSON.stringify(elemDefault));
    });

    it('should update an Organization', async () => {
        const updatedElem = Object.assign(
            {
                name: 'updated',
            },
            elemDefault,
        );
        service.update(updatedElem).subscribe((data) => expect(data).toMatchObject({ body: updatedElem }));
        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(JSON.stringify(elemDefault));
    });

    it('should delete an Organization', async () => {
        service.deleteOrganization(elemDefault.id!).subscribe((resp) => expect(resp.ok).toBeTrue());
        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
    });

    it('should add a user to an Organization', async () => {
        const user1 = new User();
        user1.login = 'testUser';
        service.addUserToOrganization(elemDefault.id!, user1.login).subscribe((resp) => expect(resp.ok).toBeTrue());
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(JSON.stringify(elemDefault));
    });

    it('should delete a user from an Organization', async () => {
        const user1 = new User();
        user1.login = 'testUser';
        service.removeUserFromOrganization(elemDefault.id!, user1.login).subscribe((resp) => expect(resp.ok).toBeTrue());
        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });
    });

    function createTestReturnElement() {
        const elem2 = new Organization();
        elem2.id = 1;
        elem2.name = 'test2';
        elem2.shortName = 'test2';
        return [elemDefault, elem2];
    }
});
