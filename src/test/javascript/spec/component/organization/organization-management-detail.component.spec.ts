import { ComponentFixture, TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { OrganizationManagementDetailComponent } from 'app/admin/organization-management/organization-management-detail.component';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { Organization } from 'app/entities/organization.model';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { UserService } from 'app/core/user/user.service';

describe('OrganizationManagementDetailComponent', () => {
    let component: OrganizationManagementDetailComponent;
    let fixture: ComponentFixture<OrganizationManagementDetailComponent>;
    let organizationService: OrganizationManagementService;
    let userService: UserService;
    const organization1 = new Organization();
    organization1.id = 5;
    const route = ({
        data: of({ organization: organization1 }),
    } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [OrganizationManagementDetailComponent],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
            ],
        })
            .overrideTemplate(OrganizationManagementDetailComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(OrganizationManagementDetailComponent);
        component = fixture.componentInstance;
        organizationService = TestBed.inject(OrganizationManagementService);
        userService = TestBed.inject(UserService);
    });

    afterEach(async () => {
        jest.clearAllMocks();
    });

    it('should initialize and load organization from route', fakeAsync(() => {
        const user = new User();
        user.id = 5;
        user.login = 'user5';
        const course = new Course();
        course.id = 5;
        course.title = 'course5';

        organization1.name = 'orgOne';
        organization1.shortName = 'oO1';
        organization1.emailPattern = '.*1';
        organization1.users = [user];
        organization1.courses = [course];

        spyOn(organizationService, 'getOrganizationByIdWithUsersAndCourses').and.returnValue(Observable.of(organization1));

        component.ngOnInit();
        tick();

        expect(component.organization.id).toEqual(organization1.id);
        expect(component.organization.users?.length).toEqual(1);
        expect(component.organization.courses?.length).toEqual(1);
    }));

    it('should track id', fakeAsync(() => {
        const user = new User();
        user.id = 5;

        expect(component.trackIdentity(0, user)).toEqual(5);
    }));

    it('should remove user from organization', fakeAsync(() => {
        const user1 = new User();
        user1.id = 11;
        user1.login = 'userOne';
        const user2 = new User();
        user2.id = 12;
        const user3 = new User();
        user3.id = 13;
        organization1.users = [user1, user2, user3];
        component.organization = organization1;
        spyOn(organizationService, 'removeUserFromOrganization').and.returnValue(of(new HttpResponse<void>()));

        component.removeFromOrganization(user1);
        tick();
        expect(component.organization.users?.length).toEqual(2);
    }));

    it('should not remove user from organization if error occurred', fakeAsync(() => {
        const user1 = new User();
        user1.id = 11;
        user1.login = 'userOne';
        const user2 = new User();
        user2.id = 12;
        const user3 = new User();
        user3.id = 13;
        organization1.users = [user1, user2, user3];
        component.organization = organization1;
        spyOn(organizationService, 'removeUserFromOrganization').and.returnValue(throwError(new HttpErrorResponse({ status: 404 })));

        component.removeFromOrganization(user1);
        tick();
        expect(component.organization.users?.length).toEqual(3);
    }));

    it('should load all current organization users', fakeAsync(() => {
        const user1 = new User();
        user1.id = 11;
        const user2 = new User();
        user2.id = 12;
        const course1 = new Course();
        course1.id = 21;
        organization1.users = [user1, user2];
        organization1.courses = [course1];

        spyOn(organizationService, 'getOrganizationByIdWithUsersAndCourses').and.returnValue(Observable.of(organization1));

        component.loadAll();
        expect(component.organization.users?.length).toEqual(2);
        expect(component.organization.courses?.length).toEqual(1);
    }));
});
