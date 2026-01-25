import { HttpResponse } from '@angular/common/http';
import { Component, input, model, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRoute, Router } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { UserService } from 'app/core/user/shared/user.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course, CourseGroup } from 'app/core/course/shared/entities/course.model';
import dayjs from 'dayjs/esm';
import { MockDirective, MockProvider } from 'ng-mocks';
import { Observable, of } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseGroupMembershipComponent } from 'app/core/course/manage/course-group-membership/course-group-membership.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { HttpResponse as HttpResponseType } from '@angular/common/http';
import * as Sentry from '@sentry/angular';

// Manual mock component to avoid ng-mocks issues with signal queries
@Component({
    selector: 'jhi-course-group',
    template: '<ng-content />',
})
class MockCourseGroupComponent {
    readonly allGroupUsers = model<User[]>([]);
    readonly isLoadingAllGroupUsers = input(false);
    readonly isAdmin = input(false);
    readonly course = input.required<Course>();
    readonly tutorialGroup = input<TutorialGroup | undefined>(undefined);
    readonly courseGroup = input.required<CourseGroup>();
    readonly exportFileName = input.required<string>();
    readonly userSearch = input<(loginOrName: string) => Observable<HttpResponseType<User[]>>>(() => of(new HttpResponse<User[]>({ body: [] })));
    readonly addUserToGroup = input<(login: string) => Observable<HttpResponseType<void>>>(() => of(new HttpResponse<void>()));
    readonly removeUserFromGroup = input<(login: string) => Observable<HttpResponseType<void>>>(() => of(new HttpResponse<void>()));
    readonly handleUsersSizeChange = input<(filteredUsersSize: number) => void>(() => {});
    readonly importFinish = output<void>();
}

describe('Course Group Membership Component', () => {
    setupTestBed({ zoneless: true });

    let comp: CourseGroupMembershipComponent;
    let fixture: ComponentFixture<CourseGroupMembershipComponent>;
    let courseService: CourseManagementService;
    const courseGroup = CourseGroup.STUDENTS;
    const course = { id: 123, title: 'Course Title', isAtLeastInstructor: true, endDate: dayjs().subtract(5, 'minutes'), courseArchivePath: 'some-path' };
    const parentRoute = {
        data: of({ course }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute, params: of({ courseGroup }) } as any as ActivatedRoute;
    const courseGroupUser = new User(1, 'user');

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [CourseGroupMembershipComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                MockProvider(CourseManagementService),
                MockProvider(UserService),
                { provide: AccountService, useClass: MockAccountService },
            ],
        }).overrideComponent(CourseGroupMembershipComponent, {
            set: {
                imports: [MockCourseGroupComponent, MockDirective(TranslateDirective)],
            },
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseGroupMembershipComponent);
        comp = fixture.componentInstance;
        courseService = TestBed.inject(CourseManagementService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        vi.spyOn(courseService, 'getAllUsersInCourseGroup').mockReturnValue(of(new HttpResponse({ body: [] })));
        fixture.detectChanges();
        expect(CourseGroupMembershipComponent).not.toBeNull();
    });

    describe('OnInit', () => {
        it('should load all course group users', () => {
            const getUsersStub = vi.spyOn(courseService, 'getAllUsersInCourseGroup').mockReturnValue(of(new HttpResponse({ body: [courseGroupUser] })));
            fixture.detectChanges();
            expect(comp.course()).toEqual(course);
            expect(comp.courseGroup()).toEqual(courseGroup);
            expect(getUsersStub).toHaveBeenCalledOnce();
        });
    });

    describe('courseGroupName', () => {
        it('should return courses studentGroupName if group is students', () => {
            comp.courseGroup.set(CourseGroup.STUDENTS);
            comp.course.set({ ...course, studentGroupName: 'testStudentGroupName' });
            expect(comp.courseGroupName()).toBe('testStudentGroupName');
        });

        it('should return courses teachingAssistantGroupName if group is tutors', () => {
            comp.courseGroup.set(CourseGroup.TUTORS);
            comp.course.set({ ...course, teachingAssistantGroupName: 'testTeachingAssistantGroupName' });
            expect(comp.courseGroupName()).toBe('testTeachingAssistantGroupName');
        });

        it('should return courses instructorGroupName if group is instructors', () => {
            comp.courseGroup.set(CourseGroup.INSTRUCTORS);
            comp.course.set({ ...course, instructorGroupName: 'testInstructorGroupName' });
            expect(comp.courseGroupName()).toBe('testInstructorGroupName');
        });

        it('should return courses editorGroupName if group is editors', () => {
            comp.courseGroup.set(CourseGroup.EDITORS);
            comp.course.set({ ...course, editorGroupName: 'testEditorGroupName' });
            expect(comp.courseGroupName()).toBe('testEditorGroupName');
        });

        it('should return undefined if course is undefined', () => {
            comp.courseGroup.set(CourseGroup.STUDENTS);
            comp.course.set(undefined);
            expect(comp.courseGroupName()).toBeUndefined();
        });

        it('should return undefined if courseGroup is undefined', () => {
            comp.course.set({ ...course, studentGroupName: 'testStudentGroupName' });
            comp.courseGroup.set(undefined);
            expect(comp.courseGroupName()).toBeUndefined();
        });

        it('should capture exception and return undefined for unknown course group', () => {
            const captureExceptionSpy = vi.spyOn(Sentry, 'captureException');
            comp.course.set({ ...course });
            // Force an unknown course group by casting
            comp.courseGroup.set('unknownGroup' as CourseGroup);
            expect(comp.courseGroupName()).toBeUndefined();
            expect(captureExceptionSpy).toHaveBeenCalledWith('Unknown course group: unknownGroup');
        });
    });

    describe('handleUsersSizeChange', () => {
        it('should change user size to given number', () => {
            const size = 5;
            comp.handleUsersSizeChange(size);
            expect(comp.filteredUsersSize()).toBe(size);
        });
    });

    describe('exportFileName', () => {
        it('should return export file name', () => {
            comp.courseGroup.set(CourseGroup.STUDENTS);
            const user1 = new User(1, 'user1');
            comp.allCourseGroupUsers.set([user1]);
            comp.course.set({ title: 'Example' });

            expect(comp.exportFilename()).toBe('Student Example');
            const user2 = new User(2, 'user2');
            comp.allCourseGroupUsers.set([user1, user2]);
            expect(comp.exportFilename()).toBe('Students Example');
        });

        it('should return empty string if courseGroupEntityName is empty', () => {
            comp.courseGroup.set(undefined);
            comp.course.set({ title: 'Example' });
            expect(comp.exportFilename()).toBe('');
        });

        it('should return empty string if course is undefined', () => {
            comp.courseGroup.set(CourseGroup.STUDENTS);
            comp.course.set(undefined);
            expect(comp.exportFilename()).toBe('');
        });
    });

    describe('courseGroupEntityName', () => {
        it('should return empty string if courseGroup is undefined', () => {
            comp.courseGroup.set(undefined);
            expect(comp.courseGroupEntityName()).toBe('');
        });

        it('should return singular form when exactly one user', () => {
            comp.courseGroup.set(CourseGroup.TUTORS);
            comp.allCourseGroupUsers.set([new User(1, 'user1')]);
            expect(comp.courseGroupEntityName()).toBe('tutor');
        });

        it('should return plural form when zero users', () => {
            comp.courseGroup.set(CourseGroup.TUTORS);
            comp.allCourseGroupUsers.set([]);
            expect(comp.courseGroupEntityName()).toBe('tutors');
        });

        it('should return plural form when multiple users', () => {
            comp.courseGroup.set(CourseGroup.EDITORS);
            comp.allCourseGroupUsers.set([new User(1, 'user1'), new User(2, 'user2')]);
            expect(comp.courseGroupEntityName()).toBe('editors');
        });
    });

    describe('userSearch', () => {
        it('should call userService.search with the login or name', () => {
            const userService = TestBed.inject(UserService);
            const searchSpy = vi.spyOn(userService, 'search').mockReturnValue(of(new HttpResponse({ body: [courseGroupUser] })));

            comp.userSearch('testUser');

            expect(searchSpy).toHaveBeenCalledWith('testUser');
        });
    });

    describe('addToGroup', () => {
        it('should call courseService.addUserToCourseGroup', () => {
            vi.spyOn(courseService, 'getAllUsersInCourseGroup').mockReturnValue(of(new HttpResponse({ body: [] })));
            fixture.detectChanges();

            const addUserSpy = vi.spyOn(courseService, 'addUserToCourseGroup').mockReturnValue(of(new HttpResponse<void>()));

            comp.addToGroup('testLogin');

            expect(addUserSpy).toHaveBeenCalledWith(123, CourseGroup.STUDENTS, 'testLogin');
        });
    });

    describe('removeFromGroup', () => {
        it('should call courseService.removeUserFromCourseGroup', () => {
            vi.spyOn(courseService, 'getAllUsersInCourseGroup').mockReturnValue(of(new HttpResponse({ body: [] })));
            fixture.detectChanges();

            const removeUserSpy = vi.spyOn(courseService, 'removeUserFromCourseGroup').mockReturnValue(of(new HttpResponse<void>()));

            comp.removeFromGroup('testLogin');

            expect(removeUserSpy).toHaveBeenCalledWith(123, CourseGroup.STUDENTS, 'testLogin');
        });
    });

    describe('loadAll with invalid course group', () => {
        it('should redirect to course-management when course group is not in predefined groups', async () => {
            const router = TestBed.inject(Router);
            const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

            // Create a new fixture with an invalid course group
            const invalidCourseGroup = 'invalid-group';
            const invalidParentRoute = {
                data: of({ course }),
            } as any as ActivatedRoute;
            const invalidRoute = { parent: invalidParentRoute, params: of({ courseGroup: invalidCourseGroup }) } as any as ActivatedRoute;

            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                imports: [CourseGroupMembershipComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: invalidRoute },
                    MockProvider(CourseManagementService),
                    MockProvider(UserService),
                    { provide: AccountService, useClass: MockAccountService },
                    MockProvider(Router),
                ],
            }).overrideComponent(CourseGroupMembershipComponent, {
                set: {
                    imports: [MockCourseGroupComponent, MockDirective(TranslateDirective)],
                },
            });

            await TestBed.compileComponents();
            const newFixture = TestBed.createComponent(CourseGroupMembershipComponent);
            const newRouter = TestBed.inject(Router);
            const newNavigateSpy = vi.spyOn(newRouter, 'navigate').mockResolvedValue(true);

            newFixture.detectChanges();

            expect(newNavigateSpy).toHaveBeenCalledWith(['/course-management']);
        });
    });

    describe('isAdmin', () => {
        it('should set isAdmin based on accountService', () => {
            vi.spyOn(courseService, 'getAllUsersInCourseGroup').mockReturnValue(of(new HttpResponse({ body: [] })));
            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'isAdmin').mockReturnValue(true);

            fixture.detectChanges();

            expect(comp.isAdmin()).toBe(true);
        });

        it('should set isAdmin to false when user is not admin', () => {
            vi.spyOn(courseService, 'getAllUsersInCourseGroup').mockReturnValue(of(new HttpResponse({ body: [] })));
            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'isAdmin').mockReturnValue(false);

            fixture.detectChanges();

            expect(comp.isAdmin()).toBe(false);
        });
    });

    describe('capitalize', () => {
        it('should expose capitalize function from lodash', () => {
            expect(comp.capitalize('hello')).toBe('Hello');
            expect(comp.capitalize('WORLD')).toBe('World');
        });
    });
});
