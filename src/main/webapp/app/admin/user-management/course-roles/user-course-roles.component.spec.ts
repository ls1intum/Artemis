/**
 * Vitest tests for UserCourseRolesComponent.
 * Verifies that the course roles of a user are loaded for the given login and rendered grouped by role.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { UserCourseRolesComponent } from 'app/admin/user-management/course-roles/user-course-roles.component';
import { UserCourseRole } from 'app/account/user/shared/user-course-role.model';

describe('UserCourseRolesComponent', () => {
    let fixture: ComponentFixture<UserCourseRolesComponent>;
    let httpMock: HttpTestingController;

    const courseRolesUrl = 'api/account/admin/users/student1/course-roles';

    const courseRoles: UserCourseRole[] = [
        { courseId: 1, courseTitle: 'Algorithms', courseShortName: 'ALGO', role: 'INSTRUCTOR' },
        { courseId: 2, courseTitle: 'Databases', courseShortName: 'DB', role: 'STUDENT' },
        { courseId: 1, courseTitle: 'Algorithms', courseShortName: 'ALGO', role: 'STUDENT' },
        { courseId: 3, courseTitle: 'Compilers', courseShortName: 'CC', role: 'STUDENT' },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserCourseRolesComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideTranslateService()],
        }).compileComponents();

        const translateService = TestBed.inject(TranslateService);
        translateService.setTranslation('en', {
            loading: 'Loading...',
            artemisApp: {
                userManagement: {
                    courseRoles: {
                        title: 'Course roles',
                        loadError: 'Could not load',
                        empty: { title: 'No course roles', description: 'No course.' },
                        roles: { instructor: 'Instructors', editor: 'Editors', tutor: 'Tutors', student: 'Students' },
                    },
                },
            },
        });
        translateService.use('en');

        httpMock = TestBed.inject(HttpTestingController);
        fixture = TestBed.createComponent(UserCourseRolesComponent);
        fixture.componentRef.setInput('login', 'student1');
    });

    afterEach(() => {
        httpMock.verify();
    });

    function element(): HTMLElement {
        return fixture.nativeElement;
    }

    function courseTitles(group: Element): (string | null)[] {
        return Array.from(group.querySelectorAll('[data-testid="user-course-roles-course"] a')).map((link) => link.textContent);
    }

    async function respondWith(response: UserCourseRole[]): Promise<void> {
        fixture.detectChanges();
        httpMock.expectOne({ method: 'GET', url: courseRolesUrl }).flush(response);
        await fixture.whenStable();
        fixture.detectChanges();
    }

    it('shows a loading indicator until the course roles arrive', async () => {
        fixture.detectChanges();
        expect(element().querySelector('[data-testid="user-course-roles-loading"]')).not.toBeNull();

        httpMock.expectOne(courseRolesUrl).flush([]);
        await fixture.whenStable();
        fixture.detectChanges();

        expect(element().querySelector('[data-testid="user-course-roles-loading"]')).toBeNull();
    });

    it('groups the courses by role, most privileged role first, and omits roles without a course', async () => {
        await respondWith(courseRoles);

        const groups = Array.from(element().querySelectorAll('[data-testid^="user-course-roles-group-"]'));
        expect(groups.map((group) => group.getAttribute('data-testid'))).toEqual(['user-course-roles-group-INSTRUCTOR', 'user-course-roles-group-STUDENT']);

        expect(courseTitles(groups[0])).toEqual(['Algorithms']);
        expect(courseTitles(groups[1])).toEqual(['Databases', 'Algorithms', 'Compilers']);
        expect(groups[1].querySelector('[data-testid="user-course-roles-course"] span')?.textContent).toBe('DB');
        expect(groups[1].querySelector('h3')?.textContent).toContain('Students');
        expect(groups[1].querySelector('h3')?.textContent).toContain('3');
    });

    it('links every course to its course management page', async () => {
        await respondWith(courseRoles);

        const hrefs = Array.from(element().querySelectorAll('[data-testid="user-course-roles-course"] a')).map((link) => link.getAttribute('href'));
        expect(hrefs).toEqual(['/course-management/1', '/course-management/2', '/course-management/1', '/course-management/3']);
    });

    it('shows the empty state when the user holds no course role', async () => {
        await respondWith([]);

        expect(element().querySelector('[data-testid="user-course-roles-empty"]')).not.toBeNull();
        expect(element().querySelector('[data-testid^="user-course-roles-group-"]')).toBeNull();
    });

    it('shows an error message when the course roles cannot be loaded', async () => {
        fixture.detectChanges();
        httpMock.expectOne(courseRolesUrl).flush('error', { status: 500, statusText: 'Server Error' });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(element().querySelector('[data-testid="user-course-roles-error"]')).not.toBeNull();
        expect(element().querySelector('[data-testid="user-course-roles-empty"]')).toBeNull();
    });

    it('reloads the course roles when the login changes', async () => {
        await respondWith([]);

        fixture.componentRef.setInput('login', 'student2');
        fixture.detectChanges();
        httpMock.expectOne('api/account/admin/users/student2/course-roles').flush([courseRoles[0]]);
        await fixture.whenStable();
        fixture.detectChanges();

        expect(element().querySelectorAll('[data-testid="user-course-roles-course"]')).toHaveLength(1);
    });
});
