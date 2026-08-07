import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CourseRoleSlug } from 'app/course/shared/entities/course.model';
import dayjs from 'dayjs/esm';
import { MockProvider } from 'ng-mocks';
import { Subject, of } from 'rxjs';
import { CourseGroupMembershipComponent } from 'app/course/manage/course-group-membership/course-group-membership.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('Course Group Membership Component', () => {
    let comp: CourseGroupMembershipComponent;
    let fixture: ComponentFixture<CourseGroupMembershipComponent>;
    const courseRoleSlug = CourseRoleSlug.STUDENTS;
    const course = { id: 123, title: 'Course Title', isAtLeastInstructor: true, endDate: dayjs().subtract(5, 'minutes'), courseArchivePath: 'some-path' };
    const parentRoute = {
        data: of({ course }),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute, params: of({ courseRoleSlug }) } as any as ActivatedRoute;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [CourseGroupMembershipComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                MockProvider(CourseManagementService),
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).overrideTemplate(CourseGroupMembershipComponent, '');
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseGroupMembershipComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize without loading all users upfront', () => {
        const courseService = TestBed.inject(CourseManagementService);
        const getAllUsersSpy = vi.spyOn(courseService, 'getAllUsersInCourseRole');
        fixture.detectChanges();
        expect(comp).not.toBeNull();
        expect(getAllUsersSpy).not.toHaveBeenCalled();
    });

    describe('OnInit', () => {
        it('should load course and role from route', () => {
            fixture.detectChanges();
            expect(comp.course()).toEqual(course);
            expect(comp.courseRoleSlug()).toEqual(courseRoleSlug);
        });

        it('should set isAdmin from accountService', () => {
            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'isAdmin').mockReturnValue(true);
            fixture.detectChanges();
            expect(comp.isAdmin()).toBe(true);
        });

        it('should set isAdmin to false when user is not admin', () => {
            const accountService = TestBed.inject(AccountService);
            vi.spyOn(accountService, 'isAdmin').mockReturnValue(false);
            fixture.detectChanges();
            expect(comp.isAdmin()).toBe(false);
        });
    });

    describe('removeFromRole', () => {
        it('should call courseService.removeUserFromCourseRole with course id, role slug, and login', () => {
            const courseService = TestBed.inject(CourseManagementService);
            fixture.detectChanges();
            const removeUserSpy = vi.spyOn(courseService, 'removeUserFromCourseRole').mockReturnValue(of(new HttpResponse<void>()));
            comp.removeFromRole('testLogin');
            expect(removeUserSpy).toHaveBeenCalledWith(123, CourseRoleSlug.STUDENTS, 'testLogin');
        });
    });

    describe('route reused across role tabs', () => {
        it('should update courseRoleSlug to the newly navigated role without recreating the component', async () => {
            const paramsSubject = new Subject<{ courseRoleSlug: CourseRoleSlug }>();
            const reusedRoute = { parent: parentRoute, params: paramsSubject.asObservable() } as any as ActivatedRoute;

            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                imports: [CourseGroupMembershipComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: reusedRoute },
                    MockProvider(CourseManagementService),
                    { provide: AccountService, useClass: MockAccountService },
                    provideHttpClient(),
                    provideHttpClientTesting(),
                ],
            }).overrideTemplate(CourseGroupMembershipComponent, '');
            await TestBed.compileComponents();
            const reusedFixture = TestBed.createComponent(CourseGroupMembershipComponent);
            const reusedComp = reusedFixture.componentInstance;

            reusedFixture.detectChanges();
            paramsSubject.next({ courseRoleSlug: CourseRoleSlug.STUDENTS });
            expect(reusedComp.courseRoleSlug()).toEqual(CourseRoleSlug.STUDENTS);

            // Angular reuses this component instance when only the role-slug route param changes (no re-navigation to a new route).
            paramsSubject.next({ courseRoleSlug: CourseRoleSlug.TUTORS });

            expect(reusedComp.courseRoleSlug()).toEqual(CourseRoleSlug.TUTORS);
            expect(reusedComp.course()).toEqual(course);
        });
    });

    describe('loadAll with invalid course role slug', () => {
        it('should redirect to course-management when role slug is not in predefined groups', async () => {
            const invalidCourseGroup = 'invalid-group';
            const invalidParentRoute = { data: of({ course }) } as any as ActivatedRoute;
            const invalidRoute = { parent: invalidParentRoute, params: of({ courseRoleSlug: invalidCourseGroup }) } as any as ActivatedRoute;

            TestBed.resetTestingModule();
            TestBed.configureTestingModule({
                imports: [CourseGroupMembershipComponent],
                providers: [
                    { provide: ActivatedRoute, useValue: invalidRoute },
                    MockProvider(CourseManagementService),
                    { provide: AccountService, useClass: MockAccountService },
                    MockProvider(Router),
                    provideHttpClient(),
                    provideHttpClientTesting(),
                ],
            }).overrideTemplate(CourseGroupMembershipComponent, '');

            await TestBed.compileComponents();
            const newFixture = TestBed.createComponent(CourseGroupMembershipComponent);
            const newRouter = TestBed.inject(Router);
            const newNavigateSpy = vi.spyOn(newRouter, 'navigate').mockResolvedValue(true);

            newFixture.detectChanges();
            expect(newNavigateSpy).toHaveBeenCalledWith(['/course-management']);
        });
    });
});
