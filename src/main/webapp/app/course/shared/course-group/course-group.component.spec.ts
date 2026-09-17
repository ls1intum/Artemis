import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/account/user/user.model';
import { Course, CourseRoleSlug } from 'app/course/shared/entities/course.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { Subject, firstValueFrom, of, throwError } from 'rxjs';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared-ui/export/export-constants';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseGroupComponent } from 'app/course/shared/course-group/course-group.component';
import * as csvUtils from 'app/shared-ui/user-import/util/write-users-to-csv';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { MockProvider } from 'ng-mocks';
import { TableLazyLoadEvent } from 'primeng/table';
import { TableViewComponent } from 'app/shared-ui/table-view/table-view';
import { CourseRoleMember } from 'app/course/shared/course-group/course-role-member.model';
import { UserForRegistration } from 'app/shared-ui/user-registration-modal/user-for-registration.model';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { AlertService } from 'app/foundation/service/alert.service';

describe('CourseGroupComponent', () => {
    let comp: CourseGroupComponent;
    let fixture: ComponentFixture<CourseGroupComponent>;
    let courseManagementService: CourseManagementService;
    let mockAlertService: { error: ReturnType<typeof vi.fn> };

    const courseGroup = CourseRoleSlug.STUDENTS;
    const course: Course = {
        id: 123,
        title: 'Course Title',
        isAtLeastInstructor: true,
        endDate: dayjs().subtract(5, 'minutes'),
        courseArchivePath: 'some-path',
    };
    const parentRoute = {
        data: of({ course }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute, params: of({ courseGroup }) } as any as ActivatedRoute;

    const user1 = new User(1, 'user1');
    const user2 = new User(2, 'user2');

    const mockLazyEvent: TableLazyLoadEvent = { first: 0, rows: 50 };

    // Uses the real TableViewComponent (only its internal `dt` PrimeNG viewChild is mocked, matching TableViewComponent's
    // own spec) instead of a plain-object stub, so reactive reads inside methods like reset() are part of the test.
    function createRealTableView(): TableViewComponent<CourseRoleMember> {
        const tableViewFixture = TestBed.createComponent(TableViewComponent<CourseRoleMember>);
        const tableView = tableViewFixture.componentInstance;
        tableViewFixture.componentRef.setInput('cols', []);
        tableViewFixture.componentRef.setInput('vals', []);
        const mockTable = { first: 0, filters: {}, sortField: undefined, sortOrder: undefined };
        vi.spyOn(tableView, 'dt').mockReturnValue(mockTable as any);
        tableViewFixture.detectChanges();
        (comp as any).tableViewRef = () => tableView;
        return tableView;
    }

    beforeEach(async () => {
        mockAlertService = { error: vi.fn() };
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(CourseManagementService),
                { provide: AlertService, useValue: mockAlertService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).overrideTemplate(CourseGroupComponent, '');
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseGroupComponent);
        comp = fixture.componentInstance;
        courseManagementService = TestBed.inject(CourseManagementService);

        // Set required signal inputs
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('courseRoleSlug', courseGroup);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    describe('columns', () => {
        it('should define columns for login, registration number, name, email, and profile picture', () => {
            fixture.detectChanges();
            const cols = comp.columns();
            expect(cols).toHaveLength(5);
            // Profile picture column (no field, no sort)
            expect(cols[0].field).toBeUndefined();
            // Login column
            expect(cols[1].field).toBe('login');
            expect(cols[1].sort).toBe(true);
            // Registration number column
            expect(cols[2].field).toBe('visibleRegistrationNumber');
            expect(cols[2].sort).toBe(true);
            // Name column
            expect(cols[3].field).toBe('name');
            expect(cols[3].sort).toBe(true);
            // Email column
            expect(cols[4].field).toBe('email');
            expect(cols[4].sort).toBe(true);
        });
    });

    describe('tableOptions', () => {
        it('should configure table as scrollable with flex height', () => {
            expect(comp.tableOptions.scrollable).toBe(true);
            expect(comp.tableOptions.scrollHeight).toBe('flex');
        });
    });

    describe('onLazyLoad', () => {
        it('should call getPagedUsersInCourseRole and update rows and totalRows', () => {
            const mockResult = { content: [user1, user2], totalElements: 2 };
            vi.spyOn(courseManagementService, 'getPagedUsersInCourseRole').mockReturnValue(of(mockResult));

            comp.onLazyLoad(mockLazyEvent);

            expect(courseManagementService.getPagedUsersInCourseRole).toHaveBeenCalledWith(123, courseGroup, expect.any(Object));
            expect(comp.rows()).toEqual([user1, user2]);
            expect(comp.totalRows()).toBe(2);
            expect(comp.isLoading()).toBe(false);
        });

        it('should set isLoading to false on error', () => {
            vi.spyOn(courseManagementService, 'getPagedUsersInCourseRole').mockReturnValue(throwError(() => new Error('Network error')));

            comp.onLazyLoad(mockLazyEvent);

            expect(comp.isLoading()).toBe(false);
        });

        it('should clear stale rows and totalRows on error, so a failed load after a role switch does not keep showing the previous role under the new heading', () => {
            const getPagedSpy = vi.spyOn(courseManagementService, 'getPagedUsersInCourseRole');
            getPagedSpy.mockReturnValueOnce(of({ content: [user1, user2], totalElements: 2 }));
            comp.onLazyLoad(mockLazyEvent);
            expect(comp.rows()).toEqual([user1, user2]);

            getPagedSpy.mockReturnValueOnce(throwError(() => new Error('Network error')));
            comp.onLazyLoad(mockLazyEvent);

            expect(comp.rows()).toEqual([]);
            expect(comp.totalRows()).toBe(0);
        });

        it('should not call API when course id is missing', () => {
            const courseWithoutId = deepClone(course);
            courseWithoutId.id = undefined;
            fixture.componentRef.setInput('course', courseWithoutId);
            const getSpy = vi.spyOn(courseManagementService, 'getPagedUsersInCourseRole');

            comp.onLazyLoad(mockLazyEvent);

            expect(getSpy).not.toHaveBeenCalled();
        });
    });

    describe('role change (route reused across role tabs)', () => {
        it('should not reset the table on the initial render', () => {
            const tableView = createRealTableView();
            const resetSpy = vi.spyOn(tableView, 'reset');

            fixture.detectChanges();

            expect(resetSpy).not.toHaveBeenCalled();
        });

        it('should reset the table and refetch data for the new role when courseRoleSlug changes', () => {
            const mockResult = { content: [user1], totalElements: 1 };
            const getPagedSpy = vi.spyOn(courseManagementService, 'getPagedUsersInCourseRole').mockReturnValue(of(mockResult));
            const tableView = createRealTableView();
            // mirrors the template's (onLazyLoad) binding
            tableView.onLazyLoad.subscribe((event) => comp.onLazyLoad(event));

            fixture.detectChanges();
            expect(getPagedSpy).not.toHaveBeenCalled();

            fixture.componentRef.setInput('courseRoleSlug', CourseRoleSlug.TUTORS);
            fixture.detectChanges();

            expect(getPagedSpy).toHaveBeenCalledExactlyOnceWith(123, CourseRoleSlug.TUTORS, expect.any(Object));
        });

        it('should not reset the table again when only the page size changes after a role switch', () => {
            const tableView = createRealTableView();
            tableView.onLazyLoad.subscribe((event) => comp.onLazyLoad(event));

            const mockResult = { content: [user1], totalElements: 1 };
            const getPagedSpy = vi.spyOn(courseManagementService, 'getPagedUsersInCourseRole').mockReturnValue(of(mockResult));

            fixture.detectChanges();
            fixture.componentRef.setInput('courseRoleSlug', CourseRoleSlug.TUTORS);
            fixture.detectChanges(); // role-change effect runs once, calling the real reset()
            getPagedSpy.mockClear();

            // picking a page size must not re-trigger the role-change effect
            tableView.pageChange({ first: 0, rows: 10 });
            fixture.detectChanges();

            expect(getPagedSpy).not.toHaveBeenCalled();
        });
    });

    describe('removeFromGroup', () => {
        it('should call removeUserFromGroup and reset table on success', () => {
            const removeFn = vi.fn().mockReturnValue(of(new HttpResponse<void>()));
            fixture.componentRef.setInput('removeUserFromGroup', removeFn);
            fixture.detectChanges();

            comp.removeFromGroup(user1);

            expect(removeFn).toHaveBeenCalledWith(user1.login);
        });

        it('should not call removeUserFromGroup when user has no login', () => {
            const removeFn = vi.fn();
            fixture.componentRef.setInput('removeUserFromGroup', removeFn);

            const userWithoutLogin = deepClone(user1);
            delete userWithoutLogin.login;
            comp.removeFromGroup(userWithoutLogin);

            expect(removeFn).not.toHaveBeenCalled();
        });

        it('should emit dialog error message on error', () => {
            const errorMessage = 'Remove failed';
            const removeFn = vi.fn().mockReturnValue(throwError(() => new HttpErrorResponse({ error: errorMessage, status: 500 })));
            fixture.componentRef.setInput('removeUserFromGroup', removeFn);

            const dialogErrors: string[] = [];
            comp.dialogError$.subscribe((err) => dialogErrors.push(err));

            comp.removeFromGroup(user1);

            expect(dialogErrors).toHaveLength(1);
        });

        it('should reload the table via reloadAfterRemoval, so a page that is now empty steps back automatically', () => {
            const tableView = createRealTableView();
            const reloadAfterRemovalSpy = vi.spyOn(tableView, 'reloadAfterRemoval');
            const removeFn = vi.fn().mockReturnValue(of(new HttpResponse<void>()));
            fixture.componentRef.setInput('removeUserFromGroup', removeFn);

            comp.removeFromGroup(user1);

            expect(reloadAfterRemovalSpy).toHaveBeenCalledOnce();
        });
    });

    describe('onMembersAdded', () => {
        it('should reload the table at the current page', () => {
            const tableView = createRealTableView();
            const reloadSpy = vi.spyOn(tableView, 'reload');
            fixture.detectChanges();

            comp.onMembersAdded();

            expect(reloadSpy).toHaveBeenCalledOnce();
        });
    });

    describe('exportUserInformation', () => {
        it('should call getAllUsersInCourseRole and export CSV with results', () => {
            const userWithDetails = deepClone(user1);
            userWithDetails.name = 'User One';
            userWithDetails.email = 'user1@example.com';
            userWithDetails.visibleRegistrationNumber = '123456';
            vi.spyOn(courseManagementService, 'getAllUsersInCourseRole').mockReturnValue(of(new HttpResponse({ body: [userWithDetails] })));
            const exportSpy = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            expect(courseManagementService.getAllUsersInCourseRole).toHaveBeenCalledWith(123, courseGroup);
            expect(exportSpy).toHaveBeenCalledOnce();
            const [rows, keys, filename] = exportSpy.mock.calls[0];
            expect(rows[0][NAME_KEY]).toBe('User One');
            expect(rows[0][USERNAME_KEY]).toBe('user1');
            expect(rows[0][EMAIL_KEY]).toBe('user1@example.com');
            expect(rows[0][REGISTRATION_NUMBER_KEY]).toBe('123456');
            expect(keys).toEqual([NAME_KEY, USERNAME_KEY, EMAIL_KEY, REGISTRATION_NUMBER_KEY]);
            expect(filename).toBe('Students Course Title');
        });

        it('should label the CSV with the role active when the export was requested, not the role navigated to while it was in flight', () => {
            const userWithDetails = deepClone(user1);
            const responseSubject = new Subject<HttpResponse<User[]>>();
            vi.spyOn(courseManagementService, 'getAllUsersInCourseRole').mockReturnValue(responseSubject.asObservable());
            const exportSpy = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();
            fixture.componentRef.setInput('courseRoleSlug', CourseRoleSlug.TUTORS);
            responseSubject.next(new HttpResponse({ body: [userWithDetails] }));

            const [, , filename] = exportSpy.mock.calls[0];
            expect(filename).toBe('Students Course Title');
        });

        it('should not export CSV when no users returned', () => {
            vi.spyOn(courseManagementService, 'getAllUsersInCourseRole').mockReturnValue(of(new HttpResponse({ body: [] })));
            const exportSpy = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            expect(exportSpy).not.toHaveBeenCalled();
        });

        it('should not call API when course id is missing', () => {
            const courseWithoutId = deepClone(course);
            courseWithoutId.id = undefined;
            fixture.componentRef.setInput('course', courseWithoutId);
            const getSpy = vi.spyOn(courseManagementService, 'getAllUsersInCourseRole');

            comp.exportUserInformation();

            expect(getSpy).not.toHaveBeenCalled();
        });

        it('should trim whitespace from exported values', () => {
            const userWithSpaces = deepClone(user1);
            userWithSpaces.name = '  John Doe  ';
            userWithSpaces.email = '  john@example.com  ';
            userWithSpaces.visibleRegistrationNumber = '  REG001  ';
            vi.spyOn(courseManagementService, 'getAllUsersInCourseRole').mockReturnValue(of(new HttpResponse({ body: [userWithSpaces] })));
            const exportSpy = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            const [rows] = exportSpy.mock.calls[0];
            expect(rows[0][NAME_KEY]).toBe('John Doe');
            expect(rows[0][EMAIL_KEY]).toBe('john@example.com');
            expect(rows[0][REGISTRATION_NUMBER_KEY]).toBe('REG001');
        });

        it('should default to empty string for undefined user properties', () => {
            const userWithUndefinedProps = new User(3, 'user3');
            vi.spyOn(courseManagementService, 'getAllUsersInCourseRole').mockReturnValue(of(new HttpResponse({ body: [userWithUndefinedProps] })));
            const exportSpy = vi.spyOn(csvUtils, 'exportUserInformationAsCsv').mockImplementation(() => {});

            comp.exportUserInformation();

            const [rows] = exportSpy.mock.calls[0];
            expect(rows[0][NAME_KEY]).toBe('');
            expect(rows[0][EMAIL_KEY]).toBe('');
            expect(rows[0][REGISTRATION_NUMBER_KEY]).toBe('');
        });
    });

    describe('registerUsersFn', () => {
        const userToRegister: UserForRegistration = { id: 1, login: 'user1', name: 'User One', isRegistered: false };

        it('should complete successfully when every user is registered (empty not-found body)', async () => {
            vi.spyOn(courseManagementService, 'addUsersToCourseRole').mockReturnValue(of(new HttpResponse<StudentDTO[]>({ body: [] })));
            fixture.detectChanges();

            await expect(firstValueFrom(comp.registerUsersFn()([userToRegister]))).resolves.toBeUndefined();

            expect(courseManagementService.addUsersToCourseRole).toHaveBeenCalledWith(
                123,
                [{ login: 'user1', firstName: '', lastName: '', registrationNumber: '', email: '' }],
                courseGroup,
            );
            expect(mockAlertService.error).not.toHaveBeenCalled();
        });

        it('should complete successfully but alert with the login when nobody was registered', async () => {
            const notFound: StudentDTO = { login: 'user1', firstName: '', lastName: '', registrationNumber: '', email: '' };
            vi.spyOn(courseManagementService, 'addUsersToCourseRole').mockReturnValue(of(new HttpResponse<StudentDTO[]>({ body: [notFound] })));
            fixture.detectChanges();

            await expect(firstValueFrom(comp.registerUsersFn()([userToRegister]))).resolves.toBeUndefined();

            expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.course.courseGroup.notFoundUsers', { logins: 'user1' });
        });

        it('should complete successfully but alert with the logins that were not found on a partial failure', async () => {
            const secondUser: UserForRegistration = { id: 2, login: 'user2', name: 'User Two', isRegistered: false };
            const notFound: StudentDTO = { login: 'user2', firstName: '', lastName: '', registrationNumber: '', email: '' };
            vi.spyOn(courseManagementService, 'addUsersToCourseRole').mockReturnValue(of(new HttpResponse<StudentDTO[]>({ body: [notFound] })));
            fixture.detectChanges();

            await expect(firstValueFrom(comp.registerUsersFn()([userToRegister, secondUser]))).resolves.toBeUndefined();

            expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.course.courseGroup.notFoundUsers', { logins: 'user2' });
        });

        it('should short-circuit without calling the API when course id is missing', async () => {
            const courseWithoutId = deepClone(course);
            courseWithoutId.id = undefined;
            fixture.componentRef.setInput('course', courseWithoutId);
            const registerSpy = vi.spyOn(courseManagementService, 'addUsersToCourseRole');

            await expect(firstValueFrom(comp.registerUsersFn()([userToRegister]))).resolves.toBeUndefined();

            expect(registerSpy).not.toHaveBeenCalled();
        });
    });
});
