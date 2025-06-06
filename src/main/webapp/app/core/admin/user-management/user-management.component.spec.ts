import { ComponentFixture, TestBed, fakeAsync, inject, tick } from '@angular/core/testing';
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
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpHeaders, HttpParams, HttpResponse, provideHttpClient } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { Subscription, of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { EventManager } from 'app/shared/service/event-manager.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { MockLocalStorageService } from 'test/helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { AdminUserService } from 'app/core/user/shared/admin-user.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

describe('User Management Component', () => {
    let comp: UserManagementComponent;
    let fixture: ComponentFixture<UserManagementComponent>;
    let userService: AdminUserService;
    let accountService: AccountService;
    let eventManager: EventManager;
    let localStorageService: LocalStorageService;
    let httpMock: HttpTestingController;
    let profileService: ProfileService;

    const course1 = new Course();
    course1.id = 1;
    course1.title = 'a';
    const course2 = new Course();
    course2.id = 2;
    course2.title = 'b';

    const route = {
        params: of({ courseId: 123, sort: 'id,desc' }),
        children: [],
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: AccountService, useClass: MockAccountService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: Router, useClass: MockRouter },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: ProfileService, useClass: MockProfileService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: of({
                            defaultSort: 'name,asc',
                        }),
                        queryParamMap: of(
                            new Map([
                                ['page', '1'],
                                ['sort', 'id,asc'],
                            ]),
                        ),
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(EventManager),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UserManagementComponent);
                comp = fixture.componentInstance;
                userService = TestBed.inject(AdminUserService);
                accountService = TestBed.inject(AccountService);
                eventManager = TestBed.inject(EventManager);
                localStorageService = TestBed.inject(LocalStorageService);
                httpMock = TestBed.inject(HttpTestingController);
                profileService = TestBed.inject(ProfileService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        httpMock.verify();
    });

    it('should parse the user search result into the correct component state', fakeAsync(() => {
        const headers = new HttpHeaders().append('link', 'link;link').append('X-Total-Count', '1');
        jest.spyOn(userService, 'query').mockReturnValue(
            of(
                new HttpResponse({
                    body: [new User(1)],
                    headers,
                }),
            ),
        );
        jest.spyOn(profileService, 'isProfileActive').mockImplementation((profile: string) => {
            return profile === 'ldap';
        });

        comp.ngOnInit();
        // 1 sec of pause, because of the debounce time
        tick(1000);

        expect(comp.users).toHaveLength(1);
        expect(comp.users[0].id).toBe(1);
        expect(comp.totalItems).toBe(1);
        expect(comp.loadingSearchResult).toBeFalse();
    }));

    describe('setActive', () => {
        it('should update user and call load all', inject(
            [],
            fakeAsync(() => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                const user = new User(123);
                jest.spyOn(userService, 'query').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: [user],
                            headers,
                        }),
                    ),
                );

                jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(new ProfileInfo());

                comp.ngOnInit();

                const profileSpy = jest.spyOn(userService, 'activate').mockReturnValue(of(new HttpResponse<User>({ status: 200 })));
                // WHEN
                comp.setActive(user, true);
                tick(1000); // simulate async

                // THEN
                expect(userService.activate).toHaveBeenCalledWith(user.id);
                expect(userService.query).toHaveBeenCalledTimes(2);
                expect(comp.users && comp.users[0]).toEqual(expect.objectContaining({ id: 123 }));
                expect(profileSpy).toHaveBeenCalledOnce();
            }),
        ));
    });

    describe('setInactive', () => {
        it('should update user and call load all', inject(
            [],
            fakeAsync(() => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                const user = new User(123);
                jest.spyOn(userService, 'query').mockReturnValue(
                    of(
                        new HttpResponse({
                            body: [user],
                            headers,
                        }),
                    ),
                );

                jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(new ProfileInfo());

                comp.ngOnInit();

                const profileSpy = jest.spyOn(userService, 'deactivate').mockReturnValue(of(new HttpResponse<User>({ status: 200 })));
                // WHEN
                comp.setActive(user, false);
                tick(1000); // simulate async

                // THEN
                expect(userService.deactivate).toHaveBeenCalledWith(user.id);
                expect(userService.query).toHaveBeenCalledTimes(2);
                expect(comp.users && comp.users[0]).toEqual(expect.objectContaining({ id: 123 }));
                expect(profileSpy).toHaveBeenCalledOnce();
            }),
        ));
    });

    it('should searchForm + currentUser and handle navigation on init', fakeAsync(() => {
        const identitySpy = jest.spyOn(accountService, 'identity');
        const user = new User(123);
        const querySpy = jest.spyOn(userService, 'query').mockReturnValue(
            of(
                new HttpResponse({
                    body: [user],
                }),
            ),
        );

        const profileSpy = jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(new ProfileInfo());

        comp.ngOnInit();
        tick(1000);

        expect(identitySpy).toHaveBeenCalledOnce();
        expect(profileSpy).toHaveBeenCalledOnce();
        expect(comp.currentAccount).toEqual({ id: 99, login: 'admin' });

        expect(comp.page).toBe(1);
        expect(comp.predicate).toBe('id');
        expect(comp.ascending).toBeTrue();

        expect(querySpy).toHaveBeenCalledOnce();
    }));

    it('should destroy the user list subscription on destroy', () => {
        const object = {};
        comp.userListSubscription = object as Subscription;

        const destroySpy = jest.spyOn(eventManager, 'destroy').mockImplementation(jest.fn());
        comp.ngOnDestroy();
        expect(destroySpy).toHaveBeenCalledOnce();
        expect(destroySpy).toHaveBeenCalledWith(object);
    });

    it('should return the user id or -1 from trackIdentity', () => {
        expect(comp.trackIdentity(0, { id: 1 } as User)).toBe(1);
        expect(comp.trackIdentity(0, { id: undefined } as User)).toBe(-1);
    });

    it.each([
        { status: 200, statusText: '' },
        { status: 400, statusText: 'Delete Failure' },
    ])('should broadcast after user deletion, or show error', ({ status, statusText }) => {
        const deleteSpy = jest.spyOn(userService, 'deleteUser');
        const broadcastSpy = jest.spyOn(eventManager, 'broadcast');
        let errorText: string | undefined = undefined;
        comp.dialogError.subscribe((text) => (errorText = text));

        comp.deleteUser('test');
        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(deleteSpy).toHaveBeenCalledWith('test');
        const reqD = httpMock.expectOne('api/core/admin/users/test');
        reqD.flush(null, { status, statusText });

        if (status === 200) {
            expect(broadcastSpy).toHaveBeenCalledOnce();
            expect(broadcastSpy).toHaveBeenCalledWith({ name: 'userListModification', content: 'Deleted a user' });
        } else {
            expect(broadcastSpy).not.toHaveBeenCalled();
        }
        expect(errorText).toInclude(statusText);

        jest.restoreAllMocks();
    });

    it('should call initFilters', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        const user = new User(123);
        jest.spyOn(userService, 'query').mockReturnValue(
            of(
                new HttpResponse({
                    body: [user],
                    headers,
                }),
            ),
        );
        const initFiltersSpy = jest.spyOn(comp, 'initFilters');
        const profileSpy = jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(new ProfileInfo());

        comp.ngOnInit();

        expect(initFiltersSpy).toHaveBeenCalledOnce();
        expect(profileSpy).toHaveBeenCalledOnce();
        expect(userService.query).toHaveBeenCalledTimes(0);
    });

    it.each`
        input              | key
        ${AuthorityFilter} | ${UserStorageKey.AUTHORITY}
        ${OriginFilter}    | ${UserStorageKey.ORIGIN}
        ${StatusFilter}    | ${UserStorageKey.STATUS}
    `('should init filters', (param: { input: any; key: any }) => {
        const val = Object.keys(param.input).join(',');
        jest.spyOn(localStorageService, 'retrieve').mockReturnValue(val);

        const filter = comp.initFilter(param.key, param.input);
        expect(filter).toEqual(new Set(Object.keys(param.input).map((value) => param.input[value])));
    });

    it.each`
        input
        ${AuthorityFilter.ADMIN}
        ${AuthorityFilter.INSTRUCTOR}
        ${AuthorityFilter.EDITOR}
        ${AuthorityFilter.TA}
        ${AuthorityFilter.USER}
    `('should toggle authority filters', (param: { input: AuthorityFilter }) => {
        // Authority
        comp.toggleFilter(comp.filters.authorityFilter, param.input);
        expect(comp.filters.authorityFilter).toEqual(new Set([param.input]));

        comp.toggleFilter(comp.filters.authorityFilter, param.input);
        expect(comp.filters.authorityFilter).toEqual(new Set([]));
    });

    it.each`
        input
        ${OriginFilter.INTERNAL}
        ${OriginFilter.EXTERNAL}
    `('should toggle origin filters', (param: { input: OriginFilter }) => {
        // Authority
        comp.toggleFilter(comp.filters.originFilter, param.input);
        expect(comp.filters.originFilter).toEqual(new Set([param.input]));

        comp.toggleFilter(comp.filters.originFilter, param.input);
        expect(comp.filters.originFilter).toEqual(new Set([]));
    });

    it.each`
        input
        ${StatusFilter.ACTIVATED}
        ${StatusFilter.DEACTIVATED}
    `('should toggle status filters', (param: { input: StatusFilter }) => {
        // Authority
        comp.toggleFilter(comp.filters.statusFilter, param.input);
        expect(comp.filters.statusFilter).toEqual(new Set([param.input]));

        comp.toggleFilter(comp.filters.statusFilter, param.input);
        expect(comp.filters.statusFilter).toEqual(new Set([]));
    });

    it.each`
        input
        ${RegistrationNumberFilter.WITH_REG_NO}
        ${RegistrationNumberFilter.WITHOUT_REG_NO}
    `('should toggle registration number filters', (param: { input: RegistrationNumberFilter }) => {
        // Registration number
        comp.toggleFilter(comp.filters.registrationNumberFilter, param.input);
        expect(comp.filters.registrationNumberFilter).toEqual(new Set([param.input]));

        comp.toggleFilter(comp.filters.registrationNumberFilter, param.input);
        expect(comp.filters.registrationNumberFilter).toEqual(new Set([]));
    });

    it('should return correct filter values', () => {
        comp.initFilters();

        expect(comp.authorityFilters).toEqual(Object.values(AuthorityFilter));
        expect(comp.originFilters).toEqual(Object.values(OriginFilter));
        expect(comp.statusFilters).toEqual(Object.values(StatusFilter));
    });

    it('should select and deselect all roles', () => {
        const val = Object.keys(AuthorityFilter).join(',');
        jest.spyOn(localStorageService, 'retrieve').mockReturnValue(val);

        comp.filters.authorityFilter = new Set(comp.initFilter(UserStorageKey.AUTHORITY, AuthorityFilter)) as Set<AuthorityFilter>;

        comp.deselectAllRoles();
        expect(comp.filters.authorityFilter).toEqual(new Set());

        comp.selectAllRoles();
        expect(comp.filters.authorityFilter).toEqual(new Set(comp.authorityFilters));
    });

    it('should delete all selected users', () => {
        const deleteSpy = jest.spyOn(userService, 'deleteUsers').mockReturnValue(of());

        // users
        const users = [1, 2, 3].map((id) => {
            const user = new User();
            user.login = id.toString();
            return user;
        });

        comp.selectedUsers = [users[0], users[1]];

        comp.deleteAllSelectedUsers();
        expect(deleteSpy).toHaveBeenCalledOnce();
        expect(deleteSpy).toHaveBeenCalledWith([users[0].login, users[1].login]);
    });

    it('should add and remove user from selected users', () => {
        // user
        const user = new User();
        user.login = '1';

        expect(comp.selectedUsers).toEqual([]);
        comp.toggleUser(user);
        expect(comp.selectedUsers).toEqual([user]);
        comp.toggleUser(user);
        expect(comp.selectedUsers).toEqual([]);
    });

    it('should return number of applied filters', () => {
        comp.filters = new UserFilter();
        expect(comp.filters.numberOfAppliedFilters).toBe(0);

        comp.filters.noAuthority = true;
        expect(comp.filters.numberOfAppliedFilters).toBe(1);

        comp.filters.registrationNumberFilter.add(RegistrationNumberFilter.WITH_REG_NO);
        expect(comp.filters.numberOfAppliedFilters).toBe(2);

        comp.filters.authorityFilter.add(AuthorityFilter.ADMIN);
        expect(comp.filters.numberOfAppliedFilters).toBe(3);

        comp.filters.authorityFilter.delete(AuthorityFilter.ADMIN);
        expect(comp.filters.numberOfAppliedFilters).toBe(2);
    });

    it('should toggle authority filter', () => {
        const spy = jest.spyOn(localStorageService, 'store');

        comp.filters = new UserFilter();
        comp.filters.noAuthority = true;

        comp.toggleAuthorityFilter(comp.filters.authorityFilter, AuthorityFilter.ADMIN);

        expect(comp.filters.authorityFilter).toEqual(new Set<AuthorityFilter>([AuthorityFilter.ADMIN]));
        expect(comp.filters.noAuthority).toBeFalse();
        expect(spy).toHaveBeenCalledTimes(2);
        expect(spy).toHaveBeenCalledWith(UserStorageKey.NO_AUTHORITY, false);
        expect(spy).toHaveBeenCalledWith(UserStorageKey.AUTHORITY, 'ADMIN');

        comp.toggleAuthorityFilter(comp.filters.authorityFilter, AuthorityFilter.ADMIN);
        expect(spy).toHaveBeenCalledTimes(4);
        expect(spy).toHaveBeenCalledWith(UserStorageKey.NO_AUTHORITY, false);
        expect(spy).toHaveBeenCalledWith(UserStorageKey.AUTHORITY, '');
        expect(comp.filters.authorityFilter).toEqual(new Set<AuthorityFilter>());
    });

    it('should toggle origin filter', () => {
        const spy = jest.spyOn(localStorageService, 'store');

        comp.filters = new UserFilter();

        comp.toggleOriginFilter(OriginFilter.EXTERNAL);

        expect(comp.filters.originFilter).toEqual(new Set<OriginFilter>([OriginFilter.EXTERNAL]));
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(UserStorageKey.ORIGIN, 'EXTERNAL');

        comp.toggleOriginFilter(OriginFilter.EXTERNAL);
        expect(spy).toHaveBeenCalledWith(UserStorageKey.ORIGIN, '');
        expect(comp.filters.authorityFilter).toEqual(new Set<OriginFilter>());
    });

    it('should toggle registration number filter', () => {
        const spy = jest.spyOn(localStorageService, 'store');

        comp.filters = new UserFilter();

        comp.toggleRegistrationNumberFilter(RegistrationNumberFilter.WITHOUT_REG_NO);

        expect(comp.filters.registrationNumberFilter).toEqual(new Set<RegistrationNumberFilter>([RegistrationNumberFilter.WITHOUT_REG_NO]));
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(UserStorageKey.REGISTRATION_NUMBER, 'WITHOUT_REG_NO');

        comp.toggleRegistrationNumberFilter(RegistrationNumberFilter.WITHOUT_REG_NO);
        expect(spy).toHaveBeenCalledWith(UserStorageKey.REGISTRATION_NUMBER, '');
        expect(comp.filters.authorityFilter).toEqual(new Set<RegistrationNumberFilter>());
    });

    it('should toggle status filter', () => {
        const spy = jest.spyOn(localStorageService, 'store');

        comp.filters = new UserFilter();

        comp.toggleStatusFilter(StatusFilter.DEACTIVATED);

        expect(comp.filters.statusFilter).toEqual(new Set<StatusFilter>([StatusFilter.DEACTIVATED]));
        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(UserStorageKey.STATUS, 'DEACTIVATED');

        comp.toggleStatusFilter(StatusFilter.DEACTIVATED);
        expect(spy).toHaveBeenCalledWith(UserStorageKey.STATUS, '');
        expect(comp.filters.authorityFilter).toEqual(new Set<StatusFilter>());
    });

    it('should deselect filter', () => {
        comp.filters = new UserFilter();

        comp.filters.statusFilter.add(StatusFilter.DEACTIVATED);
        comp.filters.originFilter.add(OriginFilter.INTERNAL);

        comp.deselectFilter<StatusFilter>(comp.filters.statusFilter, comp.statusKey);
        expect(comp.filters.statusFilter).toEqual(new Set());

        comp.deselectFilter<OriginFilter>(comp.filters.originFilter, comp.originKey);
        expect(comp.filters.originFilter).toEqual(new Set());
    });

    it('should select empty roles', () => {
        comp.filters = new UserFilter();

        comp.filters.authorityFilter.add(AuthorityFilter.ADMIN);
        comp.filters.noAuthority = false;

        comp.selectEmptyRoles();
        expect(comp.filters.authorityFilter).toEqual(new Set());
        expect(comp.filters.noAuthority).toBeTrue();
    });

    it('should get users without current user', () => {
        comp.filters = new UserFilter();

        comp.currentAccount = new User();
        comp.currentAccount.login = '1';

        const users = ['1', '2', '3', '4', '5', '6'].map((login) => {
            const user = new User();
            user.login = login;
            return user;
        });
        comp.users = [...users];

        expect(comp.usersWithoutCurrentUser).toEqual(users.filter((user) => user.login !== '1'));
    });

    it('should toggle all users selection', () => {
        comp.filters = new UserFilter();

        comp.currentAccount = new User();
        comp.currentAccount.login = '1';

        const users = ['1', '2', '3', '4', '5', '6'].map((login) => {
            const user = new User();
            user.login = login;
            return user;
        });

        comp.users = [...users];

        comp.toggleAllUserSelection();
        expect(comp.selectedUsers).toEqual(users.filter((user) => user.login !== '1'));

        comp.toggleAllUserSelection();
        expect(comp.selectedUsers).toEqual([]);
    });

    it('should adjust options', () => {
        let httpParams = new HttpParams();
        comp.filters = new UserFilter();

        httpParams = httpParams.append('authorities', 'NO_AUTHORITY').append('origins', '').append('registrationNumbers', '').append('status', '');
        comp.filters.noAuthority = true;

        expect(comp.filters.adjustOptions(new HttpParams())).toEqual(httpParams);

        comp.filters.noAuthority = false;
        httpParams = new HttpParams().append('authorities', '').append('origins', '').append('registrationNumbers', '').append('status', '');
        expect(comp.filters.adjustOptions(new HttpParams())).toEqual(httpParams);

        httpParams = new HttpParams().append('authorities', '').append('origins', '').append('registrationNumbers', '').append('status', '');
        expect(comp.filters.adjustOptions(new HttpParams())).toEqual(httpParams);

        comp.filters.registrationNumberFilter.add(RegistrationNumberFilter.WITH_REG_NO);
        httpParams = new HttpParams().append('authorities', '').append('origins', '').append('registrationNumbers', 'WITH_REG_NO').append('status', '');
        expect(comp.filters.adjustOptions(new HttpParams())).toEqual(httpParams);

        comp.filters.originFilter.add(OriginFilter.INTERNAL);
        comp.filters.authorityFilter.add(AuthorityFilter.ADMIN);
        comp.filters.statusFilter.add(StatusFilter.ACTIVATED);
        httpParams = new HttpParams().append('authorities', 'ADMIN').append('origins', 'INTERNAL').append('registrationNumbers', 'WITH_REG_NO').append('status', 'ACTIVATED');
        expect(comp.filters.adjustOptions(new HttpParams())).toEqual(httpParams);
    });
});
