/**
 * Vitest tests for OrganizationManagementDetailComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { MockProvider } from 'ng-mocks';

import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { AlertService } from 'app/shared/service/alert.service';
import { OrganizationManagementDetailComponent } from 'app/core/admin/organization-management/organization-management-detail.component';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Organization } from 'app/core/shared/entities/organization.model';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TableLazyLoadEvent } from 'primeng/table';

describe('OrganizationManagementDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let component: OrganizationManagementDetailComponent;
    let fixture: ComponentFixture<OrganizationManagementDetailComponent>;
    let organizationService: OrganizationManagementService;

    const organization1 = new Organization();
    organization1.id = 5;
    const route = {
        data: of({ organization: organization1 }),
    } as any as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [OrganizationManagementDetailComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: ActivatedRoute, useValue: route },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
            ],
        })
            .overrideTemplate(OrganizationManagementDetailComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(OrganizationManagementDetailComponent);
        component = fixture.componentInstance;
        organizationService = TestBed.inject(OrganizationManagementService);
    });

    beforeEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize and load organization metadata from route', () => {
        organization1.name = 'orgOne';
        organization1.shortName = 'oO1';
        organization1.emailPattern = '.*1';

        vi.spyOn(organizationService, 'getOrganizationById').mockReturnValue(of(organization1));

        component.ngOnInit();

        expect(organizationService.getOrganizationById).toHaveBeenCalledWith(organization1.id);
        expect(component.organization().id).toBe(organization1.id);
        expect(component.organizationId()).toBe(organization1.id);
    });

    it('should call getOrganizationUsers when loadUsers is triggered', () => {
        const user1 = new User();
        user1.id = 11;
        user1.login = 'user1';

        component.organizationId.set(5);
        vi.spyOn(organizationService, 'getOrganizationUsers').mockReturnValue(of({ content: [user1], totalElements: 1 }));

        const event: TableLazyLoadEvent = { first: 0, rows: 50 };
        component.loadUsers(event);

        expect(organizationService.getOrganizationUsers).toHaveBeenCalledOnce();
        expect(component.users()).toHaveLength(1);
        expect(component.usersTotal()).toBe(1);
        expect(component.usersLoading()).toBe(false);
    });

    it('should call getOrganizationCourses when loadCourses is triggered', () => {
        const course1 = new Course();
        course1.id = 21;
        course1.title = 'Course A';

        component.organizationId.set(5);
        vi.spyOn(organizationService, 'getOrganizationCourses').mockReturnValue(of({ content: [course1], totalElements: 1 }));

        const event: TableLazyLoadEvent = { first: 0, rows: 50 };
        component.loadCourses(event);

        expect(organizationService.getOrganizationCourses).toHaveBeenCalledOnce();
        expect(component.courses()).toHaveLength(1);
        expect(component.coursesTotal()).toBe(1);
        expect(component.coursesLoading()).toBe(false);
    });

    it('should handle error when loading users fails', () => {
        component.organizationId.set(5);
        vi.spyOn(organizationService, 'getOrganizationUsers').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        const event: TableLazyLoadEvent = { first: 0, rows: 50 };
        component.loadUsers(event);

        expect(component.users()).toHaveLength(0);
        expect(component.usersTotal()).toBe(0);
        expect(component.usersLoading()).toBe(false);
    });

    it('should handle error when loading courses fails', () => {
        component.organizationId.set(5);
        vi.spyOn(organizationService, 'getOrganizationCourses').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        const event: TableLazyLoadEvent = { first: 0, rows: 50 };
        component.loadCourses(event);

        expect(component.courses()).toHaveLength(0);
        expect(component.coursesTotal()).toBe(0);
        expect(component.coursesLoading()).toBe(false);
    });

    it('should remove user from organization and refresh the users list', () => {
        component.organizationId.set(5);

        const user1 = new User();
        user1.id = 11;
        user1.login = 'userOne';

        vi.spyOn(organizationService, 'removeUserFromOrganization').mockReturnValue(of({} as any));
        const loadUsersSpy = vi.spyOn(component, 'loadUsers');

        const event: TableLazyLoadEvent = { first: 0, rows: 50 };
        // Simulate a prior lazy load so lastUsersLoadEvent is set
        vi.spyOn(organizationService, 'getOrganizationUsers').mockReturnValue(of({ content: [user1], totalElements: 1 }));
        component.loadUsers(event);

        component.removeFromOrganization(user1);

        expect(organizationService.removeUserFromOrganization).toHaveBeenCalledWith(5, 'userOne');
        expect(loadUsersSpy).toHaveBeenCalledTimes(2);
    });

    it('should not remove user from organization if user has no login', () => {
        component.organizationId.set(5);
        const removeSpy = vi.spyOn(organizationService, 'removeUserFromOrganization');

        const userWithoutLogin = new User();
        userWithoutLogin.id = 1;
        component.removeFromOrganization(userWithoutLogin);

        expect(removeSpy).not.toHaveBeenCalled();
    });

    it('should not remove user if organizationId is not set', () => {
        const removeSpy = vi.spyOn(organizationService, 'removeUserFromOrganization');

        const user = new User();
        user.id = 1;
        user.login = 'testuser';
        component.removeFromOrganization(user);

        expect(removeSpy).not.toHaveBeenCalled();
    });

    it('should set dialogError on removal failure', () => {
        component.organizationId.set(5);
        vi.spyOn(organizationService, 'removeUserFromOrganization').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' })));

        const user = new User();
        user.id = 1;
        user.login = 'testuser';

        let errorEmitted = false;
        component.dialogError$.subscribe((err) => {
            if (err) {
                errorEmitted = true;
            }
        });

        component.removeFromOrganization(user);
        expect(errorEmitted).toBe(true);
    });

    it('should not load users when organizationId is not set', () => {
        const spy = vi.spyOn(organizationService, 'getOrganizationUsers');
        component.loadUsers({ first: 0, rows: 50 });
        expect(spy).not.toHaveBeenCalled();
    });

    it('should not load courses when organizationId is not set', () => {
        const spy = vi.spyOn(organizationService, 'getOrganizationCourses');
        component.loadCourses({ first: 0, rows: 50 });
        expect(spy).not.toHaveBeenCalled();
    });
});
