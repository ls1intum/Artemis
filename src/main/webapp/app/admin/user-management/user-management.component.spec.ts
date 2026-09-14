/**
 * Vitest tests for UserManagementComponent.
 * Tests the main user management list view with filtering, sorting, and CRUD operations.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CredentialRevocationConfirmationService } from 'app/account/shared/credential-revocation-confirmation.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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
} from 'app/admin/user-management/user-management.component';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/account/user/user.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { AdminUserService } from 'app/account/user/shared/admin-user.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

describe('UserManagementComponent', () => {
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
                { provide: CredentialRevocationConfirmationService, useValue: { confirm: () => Promise.resolve(true) } },
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

    describe('onPageChange (tum-ui paginator)', () => {
        it('converts the 0-indexed paginator page to the 1-indexed page', () => {
            component.onPageChange(2);
            expect(component.page()).toBe(3);
        });
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
        vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => feature === 'ldap');

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
            await component.setActive(testUser, true);
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
        it('should not deactivate a user when the credential-revocation confirmation is dismissed', async () => {
            // Deactivating revokes every credential of the account, so a dismissal has to leave the account alone entirely.
            const confirmation = TestBed.inject(CredentialRevocationConfirmationService);
            vi.spyOn(confirmation, 'confirm').mockResolvedValue(false);
            const deactivateSpy = vi.spyOn(userService, 'deactivate');
            const user = { id: 7, activated: true } as User;

            await component.setActive(user, false);

            expect(deactivateSpy).not.toHaveBeenCalled();
            expect(user.activated).toBe(true);
        });

        it('should not ask for confirmation when activating a user', async () => {
            // Activating deletes nothing.
            const confirmation = TestBed.inject(CredentialRevocationConfirmationService);
            const confirmSpy = vi.spyOn(confirmation, 'confirm');
            vi.spyOn(userService, 'activate').mockReturnValue(of(new HttpResponse<User>({ body: { id: 7, activated: true } as User })));
            // setActive reloads the list once the request answers, and the reload reads the search form. This component
            // was never initialised here, so without stubbing the reload it throws after the test body has finished -
            // which surfaces as an unhandled error rather than a failing assertion, and fails the whole vitest run.
            const loadAllSpy = vi.spyOn(component, 'loadAll').mockImplementation(() => undefined);

            await component.setActive({ id: 7, activated: false } as User, true);

            expect(confirmSpy).not.toHaveBeenCalled();
            expect(loadAllSpy).toHaveBeenCalledOnce();
        });

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
            await component.setActive(testUser, false);
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

    it('should load the current deletion impact before allowing permanent deletion', () => {
        component.deleteUser('test');

        const request = httpMock.expectOne('api/account/admin/users/deletion-impact');
        expect(request.request.method).toBe('POST');
        expect(request.request.body).toEqual({ logins: ['test'] });
        request.flush({
            users: [
                {
                    userId: 42,
                    login: 'test',
                    automaticEligible: true,
                    legacyDeleted: false,
                    retentionOverrideRequired: false,
                    totalAffectedObjects: 2,
                    impactFingerprint: 'fingerprint',
                    categories: [
                        { category: 'ACCOUNT', action: 'DELETE', count: 1 },
                        { category: 'ACCOUNT', action: 'REMOVE_MEMBERSHIP', count: 1 },
                    ],
                },
            ],
            totalAffectedObjects: 2,
            categories: [
                { category: 'ACCOUNT', action: 'DELETE', count: 1 },
                { category: 'ACCOUNT', action: 'REMOVE_MEMBERSHIP', count: 1 },
            ],
        });

        expect(component.deletionImpact()?.users).toHaveLength(1);
        expect(component.deletionImpact()?.users[0].login).toBe('test');
        expect(component.deletionImpact()?.totalAffectedObjects).toBe(2);
    });

    it('should name every account in the deletion dialog and report the rest as a count', () => {
        const impacted = (index: number) => ({
            userId: index,
            login: `student${index}`,
            automaticEligible: true,
            legacyDeleted: false,
            retentionOverrideRequired: index === 0,
            totalAffectedObjects: index,
            impactFingerprint: `fingerprint${index}`,
            categories: [],
        });
        component.deletionImpact.set({ users: Array.from({ length: 12 }, (_, index) => impacted(index)), totalAffectedObjects: 66, categories: [] });

        // An administrator has to be able to see who is about to be deleted, so the dialog names them rather than
        // only counting them. A very long selection would push the confirmation off the screen, so it is cut off.
        expect(component.listedDeletionAccounts()).toHaveLength(10);
        expect(component.listedDeletionAccounts()[0].login).toBe('student0');
        expect(component.listedDeletionAccounts()[9].login).toBe('student9');
        expect(component.unlistedDeletionAccountCount()).toBe(2);
    });

    it('should name a short selection in full', () => {
        component.deletionImpact.set({
            users: [
                {
                    userId: 1,
                    login: 'only',
                    automaticEligible: true,
                    legacyDeleted: false,
                    retentionOverrideRequired: false,
                    totalAffectedObjects: 3,
                    impactFingerprint: 'a',
                    categories: [],
                },
            ],
            totalAffectedObjects: 3,
            categories: [],
        });

        expect(component.listedDeletionAccounts().map((account) => account.login)).toEqual(['only']);
        expect(component.unlistedDeletionAccountCount()).toBe(0);
    });

    it('should clear the dialog state when the deletion dialog is closed', () => {
        component.deletionDialogVisible.set(true);
        component.deletionImpact.set({ users: [], totalAffectedObjects: 0, categories: [] });
        component.deletionConfirmation.set('typed');

        component.closePermanentDeletionDialog();

        expect(component.deletionDialogVisible()).toBeFalsy();
        expect(component.deletionImpact()).toBeUndefined();
        expect(component.deletionConfirmation()).toBe('');
    });

    it('should deactivate every previewed user instead of deleting them', () => {
        const broadcastSpy = vi.spyOn(eventManager, 'broadcast');
        component.deletionDialogVisible.set(true);
        component.deletionImpact.set({
            users: [
                {
                    userId: 1,
                    login: 'first',
                    automaticEligible: true,
                    legacyDeleted: false,
                    retentionOverrideRequired: false,
                    totalAffectedObjects: 0,
                    impactFingerprint: 'a',
                    categories: [],
                },
                {
                    userId: 2,
                    login: 'second',
                    automaticEligible: true,
                    legacyDeleted: false,
                    retentionOverrideRequired: false,
                    totalAffectedObjects: 0,
                    impactFingerprint: 'b',
                    categories: [],
                },
            ],
            totalAffectedObjects: 0,
            categories: [],
        });

        component.deactivateInstead();

        // One request per previewed user, and the dialog only closes once all of them answered.
        httpMock.expectOne({ method: 'PATCH', url: 'api/account/admin/users/1/deactivate' }).flush({});
        httpMock.expectOne({ method: 'PATCH', url: 'api/account/admin/users/2/deactivate' }).flush({});

        expect(component.deletionLoading()).toBeFalsy();
        expect(component.deletionDialogVisible()).toBeFalsy();
        expect(component.selectedUsers()).toEqual([]);
        expect(broadcastSpy).toHaveBeenCalledWith({ name: 'userListModification', content: 'Deactivated users' });
    });

    it('should do nothing when deactivating without a previewed user', () => {
        component.deletionImpact.set(undefined);

        component.deactivateInstead();

        expect(component.deletionLoading()).toBeFalsy();
        httpMock.expectNone({ method: 'PATCH', url: 'api/account/admin/users/1/deactivate' });
    });

    it('should re-preview only users whose deletion plan changed', () => {
        const broadcastSpy = vi.spyOn(eventManager, 'broadcast');
        const firstUser = new User();
        firstUser.login = 'first';
        const secondUser = new User();
        secondUser.login = 'second';
        const thirdUser = new User();
        thirdUser.login = 'third';
        component.selectedUsers.set([firstUser, secondUser, thirdUser]);

        component.deleteAllSelectedUsers();
        const initialImpactRequest = httpMock.expectOne('api/account/admin/users/deletion-impact');
        initialImpactRequest.flush({
            users: [
                { userId: 41, login: 'first', impactFingerprint: 'first-fingerprint', totalAffectedObjects: 2, categories: [] },
                { userId: 42, login: 'second', impactFingerprint: 'second-fingerprint', totalAffectedObjects: 2, categories: [] },
                { userId: 43, login: 'third', impactFingerprint: 'third-fingerprint', totalAffectedObjects: 2, categories: [] },
            ],
            totalAffectedObjects: 6,
            categories: [],
        });
        component.deletionConfirmation.set('3');

        component.confirmPermanentDeletion();
        const deletionRequest = httpMock.expectOne('api/account/admin/users');
        expect(deletionRequest.request.method).toBe('DELETE');
        expect(deletionRequest.request.body).toEqual({
            users: [
                { login: 'first', impactFingerprint: 'first-fingerprint' },
                { login: 'second', impactFingerprint: 'second-fingerprint' },
                { login: 'third', impactFingerprint: 'third-fingerprint' },
            ],
        });
        deletionRequest.flush([
            { userId: 41, login: 'first', status: 'DELETED' },
            { userId: 42, login: 'second', status: 'PLAN_CHANGED' },
            { userId: 43, login: 'third', status: 'FAILED' },
        ]);

        const refreshedImpactRequest = httpMock.expectOne('api/account/admin/users/deletion-impact');
        expect(refreshedImpactRequest.request.method).toBe('POST');
        expect(refreshedImpactRequest.request.body).toEqual({ logins: ['second'] });
        refreshedImpactRequest.flush({
            users: [{ userId: 42, login: 'second', impactFingerprint: 'new-fingerprint', totalAffectedObjects: 3, categories: [] }],
            totalAffectedObjects: 3,
            categories: [],
        });

        expect(component.deletionImpact()?.users.map((user) => user.login)).toEqual(['second']);
        expect(component.permanentDeletionConfirmationExpected()).toBe('second');
        expect(component.selectedUsers().map((user) => user.login)).toEqual(['second']);
        expect(broadcastSpy).toHaveBeenCalledWith({ name: 'userListModification', content: 'Deleted users' });
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
        component.toggleFilter(component.filters().authorityFilter, param.input);
        expect(component.filters().authorityFilter).toEqual(new Set([param.input]));

        component.toggleFilter(component.filters().authorityFilter, param.input);
        expect(component.filters().authorityFilter).toEqual(new Set([]));
    });

    it.each`
        input
        ${OriginFilter.INTERNAL}
        ${OriginFilter.EXTERNAL}
    `('should toggle origin filter: $input', (param: { input: OriginFilter }) => {
        component.toggleFilter(component.filters().originFilter, param.input);
        expect(component.filters().originFilter).toEqual(new Set([param.input]));

        component.toggleFilter(component.filters().originFilter, param.input);
        expect(component.filters().originFilter).toEqual(new Set([]));
    });

    it.each`
        input
        ${StatusFilter.ACTIVATED}
        ${StatusFilter.DEACTIVATED}
    `('should toggle status filter: $input', (param: { input: StatusFilter }) => {
        component.toggleFilter(component.filters().statusFilter, param.input);
        expect(component.filters().statusFilter).toEqual(new Set([param.input]));

        component.toggleFilter(component.filters().statusFilter, param.input);
        expect(component.filters().statusFilter).toEqual(new Set([]));
    });

    it.each`
        input
        ${RegistrationNumberFilter.WITH_REG_NO}
        ${RegistrationNumberFilter.WITHOUT_REG_NO}
    `('should toggle registration number filter: $input', (param: { input: RegistrationNumberFilter }) => {
        component.toggleFilter(component.filters().registrationNumberFilter, param.input);
        expect(component.filters().registrationNumberFilter).toEqual(new Set([param.input]));

        component.toggleFilter(component.filters().registrationNumberFilter, param.input);
        expect(component.filters().registrationNumberFilter).toEqual(new Set([]));
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

        component.filters().authorityFilter = new Set(component.initFilter(UserStorageKey.AUTHORITY, AuthorityFilter)) as Set<AuthorityFilter>;

        component.deselectAllRoles();
        expect(component.filters().authorityFilter).toEqual(new Set());

        component.selectAllRoles();
        expect(component.filters().authorityFilter).toEqual(new Set(component.authorityFilters));
    });

    it('should load one aggregated deletion impact for all selected users', () => {
        const users = [1, 2, 3].map((id) => {
            const user = new User();
            user.login = id.toString();
            return user;
        });

        component.selectedUsers.set([users[0], users[1]]);

        component.deleteAllSelectedUsers();

        const request = httpMock.expectOne('api/account/admin/users/deletion-impact');
        expect(request.request.method).toBe('POST');
        expect(request.request.body).toEqual({ logins: ['1', '2'] });
        request.flush({ users: [], totalAffectedObjects: 0, categories: [] });
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
        component.filters.set(new UserFilter());
        expect(component.filters().numberOfAppliedFilters).toBe(0);

        component.filters().noAuthority = true;
        expect(component.filters().numberOfAppliedFilters).toBe(1);

        component.filters().registrationNumberFilter.add(RegistrationNumberFilter.WITH_REG_NO);
        expect(component.filters().numberOfAppliedFilters).toBe(2);

        component.filters().authorityFilter.add(AuthorityFilter.ADMIN);
        expect(component.filters().numberOfAppliedFilters).toBe(3);

        component.filters().authorityFilter.delete(AuthorityFilter.ADMIN);
        expect(component.filters().numberOfAppliedFilters).toBe(2);
    });

    it('should toggle authority filter and store in local storage', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');

        component.filters.set(new UserFilter());
        component.filters().noAuthority = true;

        component.toggleAuthorityFilter(component.filters().authorityFilter, AuthorityFilter.ADMIN);

        expect(component.filters().authorityFilter).toEqual(new Set<AuthorityFilter>([AuthorityFilter.ADMIN]));
        expect(component.filters().noAuthority).toBe(false);
        expect(storeSpy).toHaveBeenCalledTimes(2);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.NO_AUTHORITY, false);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.AUTHORITY, 'ADMIN');

        component.toggleAuthorityFilter(component.filters().authorityFilter, AuthorityFilter.ADMIN);
        expect(storeSpy).toHaveBeenCalledTimes(4);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.NO_AUTHORITY, false);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.AUTHORITY, '');
        expect(component.filters().authorityFilter).toEqual(new Set<AuthorityFilter>());
    });

    it('should toggle origin filter and store in local storage', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');

        component.filters.set(new UserFilter());

        component.toggleOriginFilter(OriginFilter.EXTERNAL);

        expect(component.filters().originFilter).toEqual(new Set<OriginFilter>([OriginFilter.EXTERNAL]));
        expect(storeSpy).toHaveBeenCalledOnce();
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.ORIGIN, 'EXTERNAL');

        component.toggleOriginFilter(OriginFilter.EXTERNAL);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.ORIGIN, '');
        expect(component.filters().authorityFilter).toEqual(new Set<OriginFilter>());
    });

    it('should toggle registration number filter and store in local storage', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');

        component.filters.set(new UserFilter());

        component.toggleRegistrationNumberFilter(RegistrationNumberFilter.WITHOUT_REG_NO);

        expect(component.filters().registrationNumberFilter).toEqual(new Set<RegistrationNumberFilter>([RegistrationNumberFilter.WITHOUT_REG_NO]));
        expect(storeSpy).toHaveBeenCalledOnce();
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.REGISTRATION_NUMBER, 'WITHOUT_REG_NO');

        component.toggleRegistrationNumberFilter(RegistrationNumberFilter.WITHOUT_REG_NO);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.REGISTRATION_NUMBER, '');
        expect(component.filters().authorityFilter).toEqual(new Set<RegistrationNumberFilter>());
    });

    it('should toggle status filter and store in local storage', () => {
        const storeSpy = vi.spyOn(localStorageService, 'store');

        component.filters.set(new UserFilter());

        component.toggleStatusFilter(StatusFilter.DEACTIVATED);

        expect(component.filters().statusFilter).toEqual(new Set<StatusFilter>([StatusFilter.DEACTIVATED]));
        expect(storeSpy).toHaveBeenCalledOnce();
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.STATUS, 'DEACTIVATED');

        component.toggleStatusFilter(StatusFilter.DEACTIVATED);
        expect(storeSpy).toHaveBeenCalledWith(UserStorageKey.STATUS, '');
        expect(component.filters().authorityFilter).toEqual(new Set<StatusFilter>());
    });

    it('should deselect filter', () => {
        component.filters.set(new UserFilter());

        component.filters().statusFilter.add(StatusFilter.DEACTIVATED);
        component.filters().originFilter.add(OriginFilter.INTERNAL);

        component.deselectFilter<StatusFilter>(component.filters().statusFilter, UserStorageKey.STATUS);
        expect(component.filters().statusFilter).toEqual(new Set());

        component.deselectFilter<OriginFilter>(component.filters().originFilter, UserStorageKey.ORIGIN);
        expect(component.filters().originFilter).toEqual(new Set());
    });

    it('should select empty roles filter', () => {
        component.filters.set(new UserFilter());

        component.filters().authorityFilter.add(AuthorityFilter.ADMIN);
        component.filters().noAuthority = false;

        component.selectEmptyRoles();
        expect(component.filters().authorityFilter).toEqual(new Set());
        expect(component.filters().noAuthority).toBe(true);
    });

    it('should get users without current user', () => {
        component.filters.set(new UserFilter());

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
        component.filters.set(new UserFilter());

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
        component.filters.set(new UserFilter());

        httpParams = httpParams.append('authorities', 'NO_AUTHORITY').append('origins', '').append('registrationNumbers', '').append('status', '');
        component.filters().noAuthority = true;

        expect(component.filters().adjustOptions(new HttpParams())).toEqual(httpParams);

        component.filters().noAuthority = false;
        httpParams = new HttpParams().append('authorities', '').append('origins', '').append('registrationNumbers', '').append('status', '');
        expect(component.filters().adjustOptions(new HttpParams())).toEqual(httpParams);

        httpParams = new HttpParams().append('authorities', '').append('origins', '').append('registrationNumbers', '').append('status', '');
        expect(component.filters().adjustOptions(new HttpParams())).toEqual(httpParams);

        component.filters().registrationNumberFilter.add(RegistrationNumberFilter.WITH_REG_NO);
        httpParams = new HttpParams().append('authorities', '').append('origins', '').append('registrationNumbers', 'WITH_REG_NO').append('status', '');
        expect(component.filters().adjustOptions(new HttpParams())).toEqual(httpParams);

        component.filters().originFilter.add(OriginFilter.INTERNAL);
        component.filters().authorityFilter.add(AuthorityFilter.ADMIN);
        component.filters().statusFilter.add(StatusFilter.ACTIVATED);
        httpParams = new HttpParams().append('authorities', 'ADMIN').append('origins', 'INTERNAL').append('registrationNumbers', 'WITH_REG_NO').append('status', 'ACTIVATED');
        expect(component.filters().adjustOptions(new HttpParams())).toEqual(httpParams);
    });
});
