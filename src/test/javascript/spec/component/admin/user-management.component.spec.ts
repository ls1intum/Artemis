import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { AuthorityFilter, OriginFilter, StatusFilter, UserManagementComponent, UserStorageKey } from 'app/admin/user-management/user-management.component';
import { UserService } from 'app/core/user/user.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { of, Subscription } from 'rxjs';
import { AbstractControl, ReactiveFormsModule } from '@angular/forms';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { ArtemisTestModule } from '../../test.module';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { EventManager } from 'app/core/util/event-manager.service';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { MockCourseManagementService } from '../../helpers/mocks/service/mock-course-management.service';
import { Course } from 'app/entities/course.model';

describe('UserManagementComponent', () => {
    let comp: UserManagementComponent;
    let fixture: ComponentFixture<UserManagementComponent>;
    let userService: UserService;
    let accountService: AccountService;
    let eventManager: EventManager;
    let courseManagementService: CourseManagementService;
    let localStorageService: LocalStorageService;
    let httpMock: HttpTestingController;

    const course1 = new Course();
    course1.id = 1;
    course1.title = 'a';
    const course2 = new Course();
    course2.id = 2;
    course2.title = 'b';
    const courses = [course2, course1];

    const route = {
        params: of({ courseId: 123, sort: 'id,desc' }),
        children: [],
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(ReactiveFormsModule), MockModule(NgbModule), HttpClientTestingModule],
            declarations: [
                UserManagementComponent,
                MockRouterLinkDirective,
                MockComponent(ItemCountComponent),
                MockPipe(ArtemisDatePipe),
                MockDirective(DeleteButtonDirective),
                MockDirective(SortDirective),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: route,
                },
                { provide: AccountService, useClass: MockAccountService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: Router, useClass: MockRouter },
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
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(UserManagementComponent);
                comp = fixture.componentInstance;
                userService = TestBed.inject(UserService);
                accountService = TestBed.inject(AccountService);
                eventManager = TestBed.inject(EventManager);
                courseManagementService = TestBed.inject(CourseManagementService);
                localStorageService = TestBed.inject(LocalStorageService);
                httpMock = TestBed.inject(HttpTestingController);
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

        comp.ngOnInit();
        // 1 sec of pause, because of the debounce time
        tick(1000);

        expect(comp.users).toHaveLength(1);
        expect(comp.users[0].id).toBe(1);
        expect(comp.totalItems).toBe(1);
        expect(comp.loadingSearchResult).toBeFalse();
    }));

    describe('setActive', () => {
        it('Should update user and call load all', inject(
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
                comp.ngOnInit();

                jest.spyOn(userService, 'update').mockReturnValue(of(new HttpResponse<User>({ status: 200 })));
                // WHEN
                comp.setActive(user, true);
                tick(1000); // simulate async

                // THEN
                expect(userService.update).toHaveBeenCalledWith({ ...user, activated: true });
                expect(userService.query).toHaveBeenCalledOnce();
                expect(comp.users && comp.users[0]).toEqual(expect.objectContaining({ id: 123 }));
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
        comp.ngOnInit();
        tick(1000);

        expect(identitySpy).toHaveBeenCalledOnce();
        expect(comp.currentAccount).toEqual({ id: 99 });

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
        const reqD = httpMock.expectOne(SERVER_API_URL + 'api/users/test');
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

    it('should validate user search correctly', () => {
        expect(comp.validateUserSearch({ value: [] } as AbstractControl)).toBe(null);
        expect(comp.validateUserSearch({ value: [0] } as AbstractControl)).toEqual({ searchControl: true });
        expect(comp.validateUserSearch({ value: [0, 0] } as AbstractControl)).toEqual({ searchControl: true });
        expect(comp.validateUserSearch({ value: [0, 0, 0] } as AbstractControl)).toBe(null);
    });

    it('should sort courses', () => {
        const spy = jest.spyOn(courseManagementService, 'getAll').mockReturnValue(
            of(
                new HttpResponse({
                    body: courses,
                }),
            ),
        );

        comp.ngOnInit();

        expect(spy).toHaveBeenCalledOnce();
        expect(comp.courses).toEqual(courses.sort((c1, c2) => c1.title!.localeCompare(c2.title!)));
    });

    it('should call initFilters', () => {
        const spy = jest.spyOn(comp, 'initFilters');

        comp.ngOnInit();

        expect(spy).toHaveBeenCalledOnce();
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

    it('should toggle course filters', () => {
        comp.courses = courses;

        // Course
        comp.toggleFilter(comp.filters.courseFilter, course1.id!);
        expect(comp.filters.courseFilter).toEqual(new Set([course1.id!]));

        comp.toggleFilter(comp.filters.courseFilter, course1.id!);
        expect(comp.filters.courseFilter).toEqual(new Set([]));
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

    it('should return correct filter values', () => {
        comp.courses = courses;
        comp.initFilters();

        expect(comp.authorityFilters).toEqual(Object.keys(AuthorityFilter).map((key) => AuthorityFilter[key]));
        expect(comp.originFilters).toEqual(Object.keys(OriginFilter).map((key) => OriginFilter[key]));
        expect(comp.statusFilters).toEqual(Object.keys(StatusFilter).map((key) => StatusFilter[key]));
        expect(comp.courseFilters).toEqual(courses);
    });

    it('should select and deselect all courses', () => {
        comp.courses = courses;
        comp.initFilters();

        comp.deselectAllCourses();
        expect(comp.filters.courseFilter).toEqual(new Set());

        comp.selectAllCourses();
        expect(comp.filters.courseFilter).toEqual(new Set(courses.map((course) => course.id!)));
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
        expect(deleteSpy).toHaveBeenCalledTimes(1);
        expect(deleteSpy).toHaveBeenCalledWith([users[0].login, users[1].login]);
    });

    it('should add and remove user from selected users', () => {
        // user
        const user = new User();
        user.login = '1';

        expect(comp.selectedUsers).toEqual([]);
        comp.selectUser(user);
        expect(comp.selectedUsers).toEqual([user]);
        comp.selectUser(user);
        expect(comp.selectedUsers).toEqual([]);
    });
});
