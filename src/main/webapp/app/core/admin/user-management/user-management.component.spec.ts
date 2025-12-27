/**
 * Vitest tests for UserManagementComponent.
 * Tests the main user management list view with filtering, sorting, and CRUD operations.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Subscription, of } from 'rxjs';
import { HttpHeaders, HttpParams, HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

import {
    AuthorityFilter,
    OriginFilter,
    RegistrationNumberFilter,
    StatusFilter,
    UserFilter,
    UserManagementComponent,
    UserStorageKey,
} from 'app/core/admin/user-management/user-management.component';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/core/user/user.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { EventManager } from 'app/shared/service/event-manager.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { AdminUserService } from 'app/core/user/shared/admin-user.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

describe('UserManagementComponent', () => {
    setupTestBed({ zoneless: true });

    let component: UserManagementComponent;
    let fixture: ComponentFixture<UserManagementComponent>;
    let userService: AdminUserService;
    let accountService: AccountService;
    let eventManager: EventManager;
    let localStorageService: LocalStorageService;
    let httpMock: HttpTestingController;
    let profileService: ProfileService;

    /** Test course data for filtering */
    const testCourse1 = new Course();
    testCourse1.id = 1;
    testCourse1.title = 'a';
    const testCourse2 = new Course();
    testCourse2.id = 2;
    testCourse2.title = 'b';

    /** Mock activated route with query parameters */
    const mockRoute = {
        params: of({ courseId: 123, sort: 'id,desc' }),
        children: [],
        data: of({ defaultSort: 'name,asc' }),
        queryParamMap: of(
            new Map([
                ['page', '1'],
                ['sort', 'id,asc'],
            ]),
        ),
    } as unknown as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserManagementComponent],
            providers: [
                { provide: ActivatedRoute, useValue: mockRoute },
                { provide: AccountService, useClass: MockAccountService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(EventManager),
            ],
        })
            .overrideTemplate(UserManagementComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(UserManagementComponent);
        component = fixture.componentInstance;
        userService = TestBed.inject(AdminUserService);
        accountService = TestBed.inject(AccountService);
        eventManager = TestBed.inject(EventManager);
        localStorageService = TestBed.inject(LocalStorageService);
        httpMock = TestBed.inject(HttpTestingController);
        profileService = TestBed.inject(ProfileService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        httpMock.verify();
    });

    it('should parse user search result into component state', async () => {
        vi.useFakeTimers();

        const headers = new HttpHeaders().append('link', 'link;link').append('X-Total-Count', '1');
        vi.spyOn(userService, 'query').mockReturnValue(
            of(
                new HttpResponse({
                    body: [new User(1)],
                    headers,
                }),
            ),
        );
        vi.spyOn(profileService, 'isProfileActive').mockImplementation((profile: string) => profile === 'ldap');

        component.ngOnInit();
        // Advance timers to account for debounce time
        await vi.advanceTimersByTimeAsync(1000);

        expect(component.users()).toHaveLength(1);
        expect(component.users()[0].id).toBe(1);
        expect(component.totalItems()).toBe(1);
        expect(component.loadingSearchResult()).toBe(false);

        vi.useRealTimers();
    });

    describe('setActive', () => {
        it('should activate user and reload list', async () => {
            vi.useFakeTimers();

            const headers = new HttpHeaders().append('link', 'link;link');
            const testUser = new User(123);
            vi.spyOn(userService, 'query').mockReturnValue(
                of(
                    new HttpResponse({
                        body: [testUser],
                        headers,
                    }),
                ),
            );
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(new ProfileInfo());

            // Trigger initialization
            fixture.detectChanges();
            await vi.advanceTimersByTimeAsync(1000);

            const activateSpy = vi.spyOn(userService, 'activate').mockReturnValue(of(new HttpResponse<User>({ status: 200 })));
            component.setActive(testUser, true);
            await vi.advanceTimersByTimeAsync(1000);

            expect(userService.activate).toHaveBeenCalledWith(testUser.id);
            // Query is called multiple times due to initialization and reload
            expect(userService.query).toHaveBeenCalled();
            expect(component.users()[0]).toEqual(expect.objectContaining({ id: 123 }));
            expect(activateSpy).toHaveBeenCalledOnce();

            vi.useRealTimers();
        });
    });

    describe('setInactive', () => {
        it('should deactivate user and reload list', async () => {
            vi.useFakeTimers();

            const headers = new HttpHeaders().append('link', 'link;link');
            const testUser = new User(123);
            vi.spyOn(userService, 'query').mockReturnValue(
                of(
                    new HttpResponse({
                        body: [testUser],
                        headers,
                    }),
                ),
            );
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(new ProfileInfo());

            // Trigger initialization
            fixture.detectChanges();
            await vi.advanceTimersByTimeAsync(1000);

            const deactivateSpy = vi.spyOn(userService, 'deactivate').mockReturnValue(of(new HttpResponse<User>({ status: 200 })));
            component.setActive(testUser, false);
            await vi.advanceTimersByTimeAsync(1000);

            expect(userService.deactivate).toHaveBeenCalledWith(testUser.id);
            // Query is called multiple times due to initialization and reload
            expect(userService.query).toHaveBeenCalled();
            expect(component.users()[0]).toEqual(expect.objectContaining({ id: 123 }));
            expect(deactivateSpy).toHaveBeenCalledOnce();

            vi.useRealTimers();
        });
    });

    it('should set up search form, current user and navigation on init', async () => {
        vi.useFakeTimers();

        const identitySpy = vi.spyOn(accountService, 'identity');
        const testUser = new User(123);
        const querySpy = vi.spyOn(userService, 'query').mockReturnValue(
            of(
                new HttpResponse({
                    body: [testUser],
                }),
            ),
        );
        const profileSpy = vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(new ProfileInfo());

        // Trigger change detection to run ngOnInit
        fixture.detectChanges();
        await vi.advanceTimersByTimeAsync(1000);

        // Identity and profile may be called more than once due to Angular lifecycle
        expect(identitySpy).toHaveBeenCalled();
        expect(profileSpy).toHaveBeenCalled();
        expect(component.currentAccount()).toEqual({ id: 99, login: 'admin' });

        expect(component.page()).toBe(1);
        expect(component.predicate()).toBe('id');
        expect(component.ascending()).toBe(true);

        expect(querySpy).toHaveBeenCalled();

        vi.useRealTimers();
    });

    it('should destroy user list subscription on component destroy', () => {
        const subscriptionMock = {} as Subscription;
        // Access private property for testing
        (component as any).userListSubscription = subscriptionMock;

        const destroySpy = vi.spyOn(eventManager, 'destroy').mockImplementation(vi.fn());
        component.ngOnDestroy();
        expect(destroySpy).toHaveBeenCalledOnce();
        expect(destroySpy).toHaveBeenCalledWith(subscriptionMock);
    });

    it('should return user id or -1 from trackIdentity', () => {
        expect(component.trackIdentity(0, { id: 1 } as User)).toBe(1);
        expect(component.trackIdentity(0, { id: undefined } as User)).toBe(-1);
    });

    it.each([
        { status: 200, statusText: '' },
        { status: 400, statusText: 'Delete Failure' },
    ])('should broadcast after user deletion (status: $status) or show error', ({ status, statusText }) => {
        const deleteSpy = vi.spyOn(userService, 'deleteUser');
        const broadcastSpy = vi.spyOn(eventManager, 'broadcast');
        let errorText: string | undefined;
        component.dialogError.subscribe((text) => (errorText = text));

        component.deleteUser('test');
        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(deleteSpy).toHaveBeenCalledWith('test');

        const request = httpMock.expectOne('api/core/admin/users/test');
        request.flush(null, { status, statusText });

        if (status === 200) {
            expect(broadcastSpy).toHaveBeenCalledOnce();
            expect(broadcastSpy).toHaveBeenCalledWith({ name: 'userListModification', content: 'Deleted a user' });
        } else {
            expect(broadcastSpy).not.toHaveBeenCalled();
        }
        expect(errorText).toContain(statusText);
    });

    it('should call initFilters on initialization', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        const testUser = new User(123);
        vi.spyOn(userService, 'query').mockReturnValue(
            of(
                new HttpResponse({
                    body: [testUser],
                    headers,
                }),
            ),
        );
        const initFiltersSpy = vi.spyOn(component, 'initFilters');
        const profileSpy = vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(new ProfileInfo());

        component.ngOnInit();

        expect(initFiltersSpy).toHaveBeenCalledOnce();
        expect(profileSpy).toHaveBeenCalledOnce();
        expect(userService.query).toHaveBeenCalledTimes(0);
    });

    it.each`
        input              | key
        ${AuthorityFilter} | ${UserStorageKey.AUTHORITY}
        ${OriginFilter}    | ${UserStorageKey.ORIGIN}
        ${StatusFilter}    | ${UserStorageKey.STATUS}
    `('should init filters for $key', (param: { input: typeof AuthorityFilter | typeof OriginFilter | typeof StatusFilter; key: UserStorageKey }) => {
        const val = Object.keys(param.input).join(',');
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue(val);

        const filter = component.initFilter(param.key, param.input);
        expect(filter).toEqual(new Set(Object.keys(param.input).map((value) => param.input[value as keyof typeof param.input])));
    });

    it.each`
        input
        ${AuthorityFilter.ADMIN}
        ${AuthorityFilter.INSTRUCTOR}
        ${AuthorityFilter.EDITOR}
        ${AuthorityFilter.TA}
        ${AuthorityFilter.USER}
    `('should toggle authority filter: $input', (param: { input: AuthorityFilter }) => {
        component.toggleFilter(component.filters.authorityFilter, param.input);
        expect(component.filters.authorityFilter).toEqual(new Set([param.input]));

        component.toggleFilter(component.filters.authorityFilter, param.input);
        expect(component.filters.authorityFilter).toEqual(new Set([]));
    });

    it.each`
        input
        ${OriginFilter.INTERNAL}
        ${OriginFilter.EXTERNAL}
    `('should toggle origin filter: $input', (param: { input: OriginFilter }) => {
        component.toggleFilter(component.filters.originFilter, param.input);
        expect(component.filters.originFilter).toEqual(new Set([param.input]));

        component.toggleFilter(component.filters.originFilter, param.input);
        expect(component.filters.originFilter).toEqual(new Set([]));
    });

    it.each`
        input
        ${StatusFilter.ACTIVATED}
        ${StatusFilter.DEACTIVATED}
    `('should toggle status filter: $input', (param: { input: StatusFilter }) => {
        component.toggleFilter(component.filters.statusFilter, param.input);
        expect(component.filters.statusFilter).toEqual(new Set([param.input]));

        component.toggleFilter(component.filters.statusFilter, param.input);
        expect(component.filters.statusFilter).toEqual(new Set([]));
    });

    it.each`
        input
        ${RegistrationNumberFilter.WITH_REG_NO}
        ${RegistrationNumberFilter.WITHOUT_REG_NO}
    `('should toggle registration number filter: $input', (param: { input: RegistrationNumberFilter }) => {
        component.toggleFilter(component.filters.registrationNumberFilter, param.input);
        expect(component.filters.registrationNumberFilter).toEqual(new Set([param.input]));

        component.toggleFilter(component.filters.registrationNumberFilter, param.input);
        expect(component.filters.registrationNumberFilter).toEqual(new Set([]));
    });

    it('should return correct filter values', () => {
        component.initFilters();

        expect(component.authorityFilters).toEqual(Object.values(AuthorityFilter));
        expect(component.originFilters).toEqual(Object.values(OriginFilter));
        expect(component.statusFilters).toEqual(Object.values(StatusFilter));
    });

    it('should select and deselect all roles', () => {
        const val = Object.keys(AuthorityFilter).join(',');
        vi.spyOn(localStorageService, 'retrieve').mockReturnValue(val);

        component.filters.authorityFilter = new Set(component.initFilter(UserStorageKey.AUTHORITY, AuthorityFilter)) as Set<AuthorityFilter>;

        component.deselectAllRoles();
        expect(component.filters.authorityFilter).toEqual(new Set());

        component.selectAllRoles();
        expect(component.filters.authorityFilter).toEqual(new Set(component.authorityFilters));
    });

    it('should delete all selected users', () => {
        const deleteSpy = vi.spyOn(userService, 'deleteUsers').mockReturnValue(of());

        const users = [1, 2, 3].map((id) => {
            const user = new User();
            user.login = id.toString();
            return user;
        });

        component.selectedUsers.set([users[0], users[1]]);

        component.deleteAllSelectedUsers();
        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(deleteSpy).toHaveBeenCalledWith([users[0].login, users[1].login]);
    });

    it('should add and remove user from selected users', () => {
        const testUser = new User();
        testUser.login = '1';

        expect(component.selectedUsers()).toEqual([]);
        component.toggleUser(testUser);
        expect(component.selectedUsers()).toEqual([testUser]);
        component.toggleUser(testUser);
        expect(component.selectedUsers()).toEqual([]);
    });

    it('should return number of applied filters', () => {
        component.filters = new UserFilter();
        expect(component.filters.numberOfAppliedFilters).toBe(0);

        component.filters.noAuthority = true;
        expect(component.filters.numberOfAppliedFilters).toBe(1);

        component.filters.registrationNumberFilter.add(RegistrationNumberFilter.WITH_REG_NO);
        expect(component.filters.numberOfAppliedFilters).toBe(2);

        component.filters.authorityFilter.add(AuthorityFilter.ADMIN);
        expect(component.filters.numberOfAppliedFilters).toBe(3);

        component.filters.authorityFilter.delete(AuthorityFilter.ADMIN);
        expect(component.filters.numberOfAppliedFilters).toBe(2);
    });

    it('should toggle authority filter and store in local storage', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');

        component.filters = new UserFilter();
        component.filters.noAuthority = true;

        component.toggleAuthorityFilter(component.filters.authorityFilter, AuthorityFilter.ADMIN);

        expect(component.filters.authorityFilter).toEqual(new Set<AuthorityFilter>([AuthorityFilter.ADMIN]));
        expect(component.filters.noAuthority).toBe(false);
        expect(storeSpy).toHaveBeenCalledTimes(2);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.NO_AUTHORITY, false);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.AUTHORITY, 'ADMIN');

        component.toggleAuthorityFilter(component.filters.authorityFilter, AuthorityFilter.ADMIN);
        expect(storeSpy).toHaveBeenCalledTimes(4);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.NO_AUTHORITY, false);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.AUTHORITY, '');
        expect(component.filters.authorityFilter).toEqual(new Set<AuthorityFilter>());
    });

    it('should toggle origin filter and store in local storage', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');

        component.filters = new UserFilter();

        component.toggleOriginFilter(OriginFilter.EXTERNAL);

        expect(component.filters.originFilter).toEqual(new Set<OriginFilter>([OriginFilter.EXTERNAL]));
        expect(storeSpy).toHaveBeenCalledOnce();
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.ORIGIN, 'EXTERNAL');

        component.toggleOriginFilter(OriginFilter.EXTERNAL);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.ORIGIN, '');
        expect(component.filters.authorityFilter).toEqual(new Set<OriginFilter>());
    });

    it('should toggle registration number filter and store in local storage', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');

        component.filters = new UserFilter();

        component.toggleRegistrationNumberFilter(RegistrationNumberFilter.WITHOUT_REG_NO);

        expect(component.filters.registrationNumberFilter).toEqual(new Set<RegistrationNumberFilter>([RegistrationNumberFilter.WITHOUT_REG_NO]));
        expect(storeSpy).toHaveBeenCalledOnce();
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.REGISTRATION_NUMBER, 'WITHOUT_REG_NO');

        component.toggleRegistrationNumberFilter(RegistrationNumberFilter.WITHOUT_REG_NO);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.REGISTRATION_NUMBER, '');
        expect(component.filters.authorityFilter).toEqual(new Set<RegistrationNumberFilter>());
    });

    it('should toggle status filter and store in local storage', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');

        component.filters = new UserFilter();

        component.toggleStatusFilter(StatusFilter.DEACTIVATED);

        expect(component.filters.statusFilter).toEqual(new Set<StatusFilter>([StatusFilter.DEACTIVATED]));
        expect(storeSpy).toHaveBeenCalledOnce();
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.STATUS, 'DEACTIVATED');

        component.toggleStatusFilter(StatusFilter.DEACTIVATED);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.STATUS, '');
        expect(component.filters.authorityFilter).toEqual(new Set<StatusFilter>());
    });

    it('should deselect filter', () => {
        component.filters = new UserFilter();

        component.filters.statusFilter.add(StatusFilter.DEACTIVATED);
        component.filters.originFilter.add(OriginFilter.INTERNAL);

        component.deselectFilter<StatusFilter>(component.filters.statusFilter, UserStorageKey.STATUS);
        expect(component.filters.statusFilter).toEqual(new Set());

        component.deselectFilter<OriginFilter>(component.filters.originFilter, UserStorageKey.ORIGIN);
        expect(component.filters.originFilter).toEqual(new Set());
    });

    it('should select empty roles filter', () => {
        component.filters = new UserFilter();

        component.filters.authorityFilter.add(AuthorityFilter.ADMIN);
        component.filters.noAuthority = false;

        component.selectEmptyRoles();
        expect(component.filters.authorityFilter).toEqual(new Set());
        expect(component.filters.noAuthority).toBe(true);
    });

    it('should get users without current user', () => {
        component.filters = new UserFilter();

        const currentUser = new User();
        currentUser.login = '1';
        component.currentAccount.set(currentUser);

        const users = ['1', '2', '3', '4', '5', '6'].map((login) => {
            const user = new User();
            user.login = login;
            return user;
        });
        component.users.set([...users]);

        expect(component.usersWithoutCurrentUser).toEqual(users.filter((user) => user.login !== '1'));
    });

    it('should toggle all users selection', () => {
        component.filters = new UserFilter();

        const currentUser = new User();
        currentUser.login = '1';
        component.currentAccount.set(currentUser);

        const users = ['1', '2', '3', '4', '5', '6'].map((login) => {
            const user = new User();
            user.login = login;
            return user;
        });

        component.users.set([...users]);

        component.toggleAllUserSelection();
        expect(component.selectedUsers()).toEqual(users.filter((user) => user.login !== '1'));

        component.toggleAllUserSelection();
        expect(component.selectedUsers()).toEqual([]);
    });

    it('should adjust options with filters', () => {
        let httpParams = new HttpParams();
        component.filters = new UserFilter();

        httpParams = httpParams.append('authorities', 'NO_AUTHORITY').append('origins', '').append('registrationNumbers', '').append('status', '');
        component.filters.noAuthority = true;

        expect(component.filters.adjustOptions(new HttpParams())).toEqual(httpParams);

        component.filters.noAuthority = false;
        httpParams = new HttpParams().append('authorities', '').append('origins', '').append('registrationNumbers', '').append('status', '');
        expect(component.filters.adjustOptions(new HttpParams())).toEqual(httpParams);

        httpParams = new HttpParams().append('authorities', '').append('origins', '').append('registrationNumbers', '').append('status', '');
        expect(component.filters.adjustOptions(new HttpParams())).toEqual(httpParams);

        component.filters.registrationNumberFilter.add(RegistrationNumberFilter.WITH_REG_NO);
        httpParams = new HttpParams().append('authorities', '').append('origins', '').append('registrationNumbers', 'WITH_REG_NO').append('status', '');
        expect(component.filters.adjustOptions(new HttpParams())).toEqual(httpParams);

        component.filters.originFilter.add(OriginFilter.INTERNAL);
        component.filters.authorityFilter.add(AuthorityFilter.ADMIN);
        component.filters.statusFilter.add(StatusFilter.ACTIVATED);
        httpParams = new HttpParams().append('authorities', 'ADMIN').append('origins', 'INTERNAL').append('registrationNumbers', 'WITH_REG_NO').append('status', 'ACTIVATED');
        expect(component.filters.adjustOptions(new HttpParams())).toEqual(httpParams);
    });
});
