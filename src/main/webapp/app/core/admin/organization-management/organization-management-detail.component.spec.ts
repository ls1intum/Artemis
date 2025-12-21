/**
 * Vitest tests for OrganizationManagementDetailComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { OrganizationManagementDetailComponent } from 'app/core/admin/organization-management/organization-management-detail.component';
import { OrganizationManagementService } from 'app/core/admin/organization-management/organization-management.service';
import { Organization } from 'app/core/shared/entities/organization.model';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { UserService } from 'app/core/user/shared/user.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';

describe('OrganizationManagementDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let component: OrganizationManagementDetailComponent;
    let fixture: ComponentFixture<OrganizationManagementDetailComponent>;
    let organizationService: OrganizationManagementService;
    let userService: UserService;
    let mockDataTable: DataTableComponent;

    const organization1 = new Organization();
    organization1.id = 5;
    const route = {
        data: of({ organization: organization1 }),
    } as any as ActivatedRoute;

    beforeEach(async () => {
        mockDataTable = { typeaheadButtons: [] } as any as DataTableComponent;

        await TestBed.configureTestingModule({
            imports: [OrganizationManagementDetailComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: ActivatedRoute, useValue: route },
                { provide: DataTableComponent, useValue: mockDataTable },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(OrganizationManagementDetailComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(OrganizationManagementDetailComponent);
        component = fixture.componentInstance;
        organizationService = TestBed.inject(OrganizationManagementService);
        userService = TestBed.inject(UserService);
    });

    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should initialize and load organization from route', () => {
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

        vi.spyOn(organizationService, 'getOrganizationByIdWithUsersAndCourses').mockReturnValue(of(organization1));

        component.ngOnInit();

        expect(component.organization().id).toBe(organization1.id);
        expect(component.organization().users).toHaveLength(1);
        expect(component.organization().courses).toHaveLength(1);
    });

    it('should track id', () => {
        const user = new User();
        user.id = 5;

        expect(component.trackIdentity(0, user)).toBe(5);
    });

    it('should remove user from organization', () => {
        organization1.users = createTestUsers();
        component.organization.set(organization1);
        vi.spyOn(organizationService, 'removeUserFromOrganization').mockReturnValue(of(new HttpResponse<void>()));

        component.removeFromOrganization(organization1.users[0]);
        expect(component.organization().users).toHaveLength(2);
    });

    it('should not remove user from organization if error occurred', () => {
        organization1.users = createTestUsers();
        component.organization.set(organization1);
        vi.spyOn(organizationService, 'removeUserFromOrganization').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

        component.removeFromOrganization(organization1.users[0]);
        expect(component.organization().users).toHaveLength(3);
    });

    it('should load all current organization users', () => {
        const user1 = new User();
        user1.id = 11;
        const user2 = new User();
        user2.id = 12;
        const course1 = new Course();
        course1.id = 21;
        organization1.users = [user1, user2];
        organization1.courses = [course1];

        vi.spyOn(organizationService, 'getOrganizationByIdWithUsersAndCourses').mockReturnValue(of(organization1));

        component.loadAll();
        expect(component.organization().users).toHaveLength(2);
        expect(component.organization().courses).toHaveLength(1);
    });

    it('should search users in the used DataTable component and return them', () => {
        const user1 = { id: 11, login: 'user1' } as User;
        const user2 = { id: 12, login: 'user2' } as User;
        const user3 = { id: 13, login: 'user3' } as User;

        vi.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [user1, user2, user3] })));

        // Mock the viewChild signal to return a mock data table
        Object.defineProperty(component, 'dataTable', { value: () => ({ typeaheadButtons: [] }) });

        component.organization.set({ users: undefined } as any);

        const result = component.searchAllUsers(of({ text: 'user', entities: [] }));

        result.subscribe((a) => {
            expect(a).toStrictEqual([
                { id: user1.id, login: user1.login },
                { id: user2.id, login: user2.login },
                { id: user3.id, login: user3.login },
            ]);
            expect(component.searchNoResults()).toBe(false);
            expect(component.searchFailed()).toBe(false);
        });

        expect(userService.search).toHaveBeenCalledOnce();
    });

    it('should return zero users if search term is less then 3 chars', () => {
        const user1 = { id: 11, login: 'user1' } as User;
        const user2 = { id: 12, login: 'user2' } as User;

        vi.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [user1, user2] })));

        // Mock the viewChild signal to return a mock data table
        Object.defineProperty(component, 'dataTable', { value: () => mockDataTable });

        const result = component.searchAllUsers(of({ text: 'us', entities: [] }));

        result.subscribe((a) => {
            expect(a).toStrictEqual([]);
            expect(component.searchNoResults()).toBe(false);
            expect(component.searchFailed()).toBe(false);
        });

        expect(userService.search).not.toHaveBeenCalled();
    });

    it('should set the no results flag is no users were found during search', () => {
        vi.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [] })));

        // Mock the viewChild signal to return a mock data table
        Object.defineProperty(component, 'dataTable', { value: () => mockDataTable });

        const result = component.searchAllUsers(of({ text: 'user', entities: [] }));

        result.subscribe((a) => {
            expect(a).toStrictEqual([]);
            expect(component.searchNoResults()).toBe(true);
            expect(component.searchFailed()).toBe(false);
        });

        expect(userService.search).toHaveBeenCalledOnce();
    });

    it('should set the search failed flag if search failed', () => {
        vi.spyOn(userService, 'search').mockReturnValue(throwError(() => new Error()));

        // Mock the viewChild signal to return a mock data table
        Object.defineProperty(component, 'dataTable', { value: () => mockDataTable });

        const result = component.searchAllUsers(of({ text: 'user', entities: [] }));

        result.subscribe((a) => {
            expect(a).toStrictEqual([]);
            expect(component.searchNoResults()).toBe(false);
            expect(component.searchFailed()).toBe(true);
        });

        expect(userService.search).toHaveBeenCalledOnce();
    });

    it('should add the user to organization on autocomplete select', () => {
        component.organization.set({ id: 7, users: [{ id: 1 } as User] } as any);
        const addUserSpy = vi.spyOn(organizationService, 'addUserToOrganization').mockReturnValue(of(new HttpResponse<void>()));
        const flashSpy = vi.spyOn(component, 'flashRowClass');

        const callback = vi.fn();
        const newUser = { id: 2, login: 'test' } as User;
        component.onAutocompleteSelect(newUser, callback);
        expect(addUserSpy).toHaveBeenCalledOnce();
        expect(addUserSpy).toHaveBeenCalledWith(7, 'test');
        expect(component.organization().users).toContain(newUser);
        expect(callback).toHaveBeenCalledOnce();
        expect(callback).toHaveBeenCalledWith(newUser);
        expect(flashSpy).toHaveBeenCalledOnce();
        expect(flashSpy).toHaveBeenCalledWith('newly-added-member');

        const existingUser = { id: 1 } as User;
        component.onAutocompleteSelect(existingUser, callback);
        expect(callback).toHaveBeenCalledTimes(2);
        expect(callback).toHaveBeenCalledWith(existingUser);
        expect(addUserSpy).toHaveBeenCalledOnce();
    });

    it('should handle error when adding user to organization fails', () => {
        component.organization.set({ id: 7, users: [{ id: 1 } as User] } as any);
        vi.spyOn(organizationService, 'addUserToOrganization').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        const callback = vi.fn();
        const newUser = { id: 2, login: 'test' } as User;
        component.onAutocompleteSelect(newUser, callback);

        expect(component.isTransitioning()).toBe(false);
        expect(callback).not.toHaveBeenCalled();
    });

    it('should not add user without login', () => {
        component.organization.set({ id: 7, users: [{ id: 1 } as User] } as any);
        const addUserSpy = vi.spyOn(organizationService, 'addUserToOrganization');

        const callback = vi.fn();
        const newUser = { id: 2 } as User; // No login
        component.onAutocompleteSelect(newUser, callback);

        expect(addUserSpy).not.toHaveBeenCalled();
        expect(callback).toHaveBeenCalledWith(newUser);
    });

    it('should not remove user without login', () => {
        organization1.users = [{ id: 1 } as User]; // No login
        component.organization.set(organization1);
        const removeSpy = vi.spyOn(organizationService, 'removeUserFromOrganization');

        component.removeFromOrganization(organization1.users[0]);

        expect(removeSpy).not.toHaveBeenCalled();
    });

    it('should track identity with undefined id', () => {
        const user = new User();
        expect(component.trackIdentity(0, user)).toBe(-1);
    });

    it('should return login from searchTextFromUser', () => {
        const user = { login: 'testuser' } as User;
        expect(component.searchTextFromUser(user)).toBe('testuser');
    });

    it('should return empty string from searchTextFromUser when no login', () => {
        const user = {} as User;
        expect(component.searchTextFromUser(user)).toBe('');
    });

    it('should return row class from dataTableRowClass', () => {
        component.rowClass.set('test-class');
        expect(component.dataTableRowClass()).toBe('test-class');
    });

    it('should update filtered users size', () => {
        component.handleUsersSizeChange(42);
        expect(component.filteredUsersSize()).toBe(42);
    });

    it('should format search result with name and login', () => {
        const user = { name: 'Test User', login: 'testuser' } as User;
        expect(component.searchResultFormatter(user)).toBe('Test User (testuser)');
    });

    it('should flash row class temporarily', () => {
        vi.useFakeTimers();

        component.flashRowClass('highlight');
        expect(component.rowClass()).toBe('highlight');

        vi.advanceTimersByTime(0);
        expect(component.rowClass()).toBe('');

        vi.useRealTimers();
    });

    function createTestUsers() {
        const user1 = new User();
        user1.id = 11;
        user1.login = 'userOne';
        const user2 = new User();
        user2.id = 12;
        const user3 = new User();
        user3.id = 13;
        return [user1, user2, user3];
    }
});
