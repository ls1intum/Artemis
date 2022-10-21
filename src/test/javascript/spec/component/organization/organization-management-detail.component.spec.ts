import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { TranslateService } from '@ngx-translate/core';

import { OrganizationManagementDetailComponent } from 'app/admin/organization-management/organization-management-detail.component';
import { OrganizationManagementService } from 'app/admin/organization-management/organization-management.service';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/user.service';
import { Course } from 'app/entities/course.model';
import { Organization } from 'app/entities/organization.model';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { iconsAsHTML } from 'app/utils/icons.utils';
import { MockComponent } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

describe('OrganizationManagementDetailComponent', () => {
    let component: OrganizationManagementDetailComponent;
    let fixture: ComponentFixture<OrganizationManagementDetailComponent>;
    let organizationService: OrganizationManagementService;
    let userService: UserService;
    let dataTable: DataTableComponent;

    const organization1 = new Organization();
    organization1.id = 5;
    const route = {
        data: of({ organization: organization1 }),
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgxDatatableModule],
            declarations: [OrganizationManagementDetailComponent, MockComponent(DataTableComponent)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: DataTableComponent, useClass: DataTableComponent },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(OrganizationManagementDetailComponent);
        component = fixture.componentInstance;
        organizationService = TestBed.inject(OrganizationManagementService);
        dataTable = TestBed.inject(DataTableComponent);
        userService = TestBed.inject(UserService);
    });

    beforeEach(() => {
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

        jest.spyOn(organizationService, 'getOrganizationByIdWithUsersAndCourses').mockReturnValue(of(organization1));

        component.ngOnInit();
        tick();

        expect(component.organization.id).toBe(organization1.id);
        expect(component.organization.users).toHaveLength(1);
        expect(component.organization.courses).toHaveLength(1);
    }));

    it('should track id', fakeAsync(() => {
        const user = new User();
        user.id = 5;

        expect(component.trackIdentity(0, user)).toBe(5);
    }));

    it('should remove user from organization', fakeAsync(() => {
        organization1.users = createTestUsers();
        component.organization = organization1;
        jest.spyOn(organizationService, 'removeUserFromOrganization').mockReturnValue(of(new HttpResponse<void>()));

        component.removeFromOrganization(organization1.users[0]);
        tick();
        expect(component.organization.users).toHaveLength(2);
    }));

    it('should not remove user from organization if error occurred', fakeAsync(() => {
        organization1.users = createTestUsers();
        component.organization = organization1;
        jest.spyOn(organizationService, 'removeUserFromOrganization').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 })));

        component.removeFromOrganization(organization1.users[0]);
        tick();
        expect(component.organization.users).toHaveLength(3);
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

        jest.spyOn(organizationService, 'getOrganizationByIdWithUsersAndCourses').mockReturnValue(of(organization1));

        component.loadAll();
        expect(component.organization.users).toHaveLength(2);
        expect(component.organization.courses).toHaveLength(1);
    }));

    it('should search users in the used DataTable component and return them and add organization icons', fakeAsync(() => {
        const user1 = { id: 11, login: 'user1' } as User;
        const user2 = { id: 12, login: 'user2' } as User;

        let typeAheadButtons = [
            { insertAdjacentHTML: jest.fn(), classList: { add: jest.fn() } },
            { insertAdjacentHTML: jest.fn(), classList: { add: jest.fn() } },
        ];

        jest.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [user1, user2] })));
        component.dataTable = { typeaheadButtons: typeAheadButtons } as any as DataTableComponent;
        component.organization = { users: undefined };

        const result = component.searchAllUsers(of({ text: 'user', entities: [] }));

        result.subscribe((a) => {
            expect(a).toStrictEqual([
                { id: user1.id, login: user1.login },
                { id: user2.id, login: user2.login },
            ]);
            expect(component.searchNoResults).toBeFalse();
            expect(component.searchFailed).toBeFalse();
        });

        tick();
        expect(userService.search).toHaveBeenCalledOnce();

        typeAheadButtons.forEach((button) => {
            expect(button.insertAdjacentHTML).toHaveBeenCalledOnce();
            expect(button.insertAdjacentHTML).toHaveBeenCalledWith('beforeend', iconsAsHTML['users-plus']);
        });

        component.organization = { users: [user1] };
        typeAheadButtons = [
            { insertAdjacentHTML: jest.fn(), classList: { add: jest.fn() } },
            { insertAdjacentHTML: jest.fn(), classList: { add: jest.fn() } },
        ];
        component.dataTable = { typeaheadButtons: typeAheadButtons } as any as DataTableComponent;
        component.searchAllUsers(of({ text: 'user', entities: [] })).subscribe();
        tick();

        expect(typeAheadButtons[0].insertAdjacentHTML).toHaveBeenCalledOnce();
        expect(typeAheadButtons[0].insertAdjacentHTML).toHaveBeenCalledWith('beforeend', iconsAsHTML['users']);
        expect(typeAheadButtons[0].classList.add).toHaveBeenCalledOnce();
        expect(typeAheadButtons[0].classList.add).toHaveBeenCalledWith('already-member');
        expect(typeAheadButtons[1].insertAdjacentHTML).toHaveBeenCalledOnce();
        expect(typeAheadButtons[1].insertAdjacentHTML).toHaveBeenCalledWith('beforeend', iconsAsHTML['users-plus']);
        expect(typeAheadButtons[1].classList.add).not.toHaveBeenCalled();
    }));

    it('should return zero users if search term is less then 3 chars', fakeAsync(() => {
        const user1 = { id: 11, login: 'user1' } as User;
        const user2 = { id: 12, login: 'user2' } as User;

        jest.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [user1, user2] })));
        component.dataTable = dataTable;

        const result = component.searchAllUsers(of({ text: 'us', entities: [] }));

        result.subscribe((a) => {
            expect(a).toStrictEqual([]);
            expect(component.searchNoResults).toBeFalse();
            expect(component.searchFailed).toBeFalse();
        });

        tick();
        expect(userService.search).not.toHaveBeenCalled();
    }));

    it('should set the no results flag is no users were found during search', fakeAsync(() => {
        jest.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [] })));
        component.dataTable = dataTable;

        const result = component.searchAllUsers(of({ text: 'user', entities: [] }));

        result.subscribe((a) => {
            expect(a).toStrictEqual([]);
            expect(component.searchNoResults).toBeTrue();
            expect(component.searchFailed).toBeFalse();
        });

        tick();
        expect(userService.search).toHaveBeenCalledOnce();
    }));

    it('should set the search failed flag if search failed', fakeAsync(() => {
        jest.spyOn(userService, 'search').mockReturnValue(throwError(() => new Error()));
        component.dataTable = dataTable;

        const result = component.searchAllUsers(of({ text: 'user', entities: [] }));

        result.subscribe((a) => {
            expect(a).toStrictEqual([]);
            expect(component.searchNoResults).toBeFalse();
            expect(component.searchFailed).toBeTrue();
        });

        tick();
        expect(userService.search).toHaveBeenCalledOnce();
    }));

    it('should add the user to organization on autocomplete select', fakeAsync(() => {
        component.organization = { id: 7, users: [{ id: 1 } as User] };
        const addUserSpy = jest.spyOn(organizationService, 'addUserToOrganization').mockReturnValue(of(new HttpResponse<void>()));
        const flashSpy = jest.spyOn(component, 'flashRowClass');

        const callback = jest.fn();
        const newUser = { id: 2, login: 'test' } as User;
        component.onAutocompleteSelect(newUser, callback);
        tick();
        expect(addUserSpy).toHaveBeenCalledOnce();
        expect(addUserSpy).toHaveBeenCalledWith(7, 'test');
        expect(component.organization.users).toContain(newUser);
        expect(callback).toHaveBeenCalledOnce();
        expect(callback).toHaveBeenCalledWith(newUser);
        expect(flashSpy).toHaveBeenCalledOnce();
        expect(flashSpy).toHaveBeenCalledWith('newly-added-member');

        const existingUser = { id: 1 } as User;
        component.onAutocompleteSelect(existingUser, callback);
        tick();
        expect(callback).toHaveBeenCalledTimes(2);
        expect(callback).toHaveBeenCalledWith(existingUser);
        expect(addUserSpy).toHaveBeenCalledOnce();
    }));

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
