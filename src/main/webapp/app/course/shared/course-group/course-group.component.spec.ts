import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/account/user/user.model';
import { Course, CourseRoleSlug } from 'app/course/shared/entities/course.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import dayjs from 'dayjs/esm';
import { of, throwError } from 'rxjs';
import { EMAIL_KEY, NAME_KEY, REGISTRATION_NUMBER_KEY, USERNAME_KEY } from 'app/shared-ui/export/export-constants';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseGroupComponent } from 'app/course/shared/course-group/course-group.component';
import * as csvUtils from 'app/shared-ui/user-import/util/write-users-to-csv';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { MockProvider } from 'ng-mocks';
import { TableLazyLoadEvent } from 'primeng/table';

describe('CourseGroupComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: CourseGroupComponent;
    let fixture: ComponentFixture<CourseGroupComponent>;
    let courseManagementService: CourseManagementService;

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

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(CourseManagementService),
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

    describe('exportFileName', () => {
        it('should derive the export filename from the role slug and course title', () => {
            fixture.detectChanges();
            expect(comp.exportFileName()).toBe('Students Course Title');
        });

        it('should return empty string when course has no title', () => {
            const courseWithoutTitle = deepClone(course);
            courseWithoutTitle.title = undefined;
            fixture.componentRef.setInput('course', courseWithoutTitle);
            fixture.detectChanges();
            expect(comp.exportFileName()).toBe('');
        });
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

        it('should not call API when course id is missing', () => {
            const courseWithoutId = deepClone(course);
            courseWithoutId.id = undefined;
            fixture.componentRef.setInput('course', courseWithoutId);
            const getSpy = vi.spyOn(courseManagementService, 'getPagedUsersInCourseRole');

            comp.onLazyLoad(mockLazyEvent);

            expect(getSpy).not.toHaveBeenCalled();
        });

        it('should update totalMembers only when no search term is active', () => {
            const mockResult = { content: [user1], totalElements: 42 };
            vi.spyOn(courseManagementService, 'getPagedUsersInCourseRole').mockReturnValue(of(mockResult));

            comp.onLazyLoad(mockLazyEvent);

            expect(comp.totalMembers()).toBe(42);
        });

        it('should not update totalMembers when a search term is active', () => {
            const mockResult = { content: [user1], totalElements: 1 };
            vi.spyOn(courseManagementService, 'getPagedUsersInCourseRole').mockReturnValue(of(mockResult));
            const eventWithSearch: TableLazyLoadEvent = { ...mockLazyEvent, globalFilter: 'alice' };

            comp.onLazyLoad(eventWithSearch);

            expect(comp.totalMembers()).toBe(0); // unchanged from initial
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
});
