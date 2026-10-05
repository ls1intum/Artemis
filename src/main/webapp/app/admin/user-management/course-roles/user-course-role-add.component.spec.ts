/**
 * Vitest tests for UserCourseRoleAddComponent.
 * Verifies the course search, the guard against adding an existing role and the request that adds the role.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { UserCourseRoleAddComponent } from 'app/admin/user-management/course-roles/user-course-role-add.component';
import { CourseForRoleAssignment, UserCourseRole } from 'app/account/user/shared/user-course-role.model';
import { AlertService } from 'app/foundation/service/alert.service';

describe('UserCourseRoleAddComponent', () => {
    let fixture: ComponentFixture<UserCourseRoleAddComponent>;
    let httpMock: HttpTestingController;
    let added: number;

    const algorithms: CourseForRoleAssignment = { id: 1, title: 'Algorithms', shortName: 'ALGO', semester: 'WS25' };
    const existingRoles: UserCourseRole[] = [{ courseId: 1, courseTitle: 'Algorithms', courseShortName: 'ALGO', role: 'STUDENT' }];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [UserCourseRoleAddComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
        }).compileComponents();

        const translateService = TestBed.inject(TranslateService);
        translateService.setTranslation('en', {
            artemisApp: {
                userManagement: {
                    courseRoles: {
                        role: { instructor: 'Instructor', editor: 'Editor', tutor: 'Tutor', student: 'Student' },
                    },
                },
            },
        });
        translateService.use('en');

        httpMock = TestBed.inject(HttpTestingController);
        fixture = TestBed.createComponent(UserCourseRoleAddComponent);
        fixture.componentRef.setInput('login', 'student1');
        fixture.componentRef.setInput('existingRoles', existingRoles);
        added = 0;
        fixture.componentInstance.added.subscribe(() => added++);
        fixture.detectChanges();
    });

    afterEach(() => {
        httpMock.verify();
    });

    function component(): any {
        return fixture.componentInstance;
    }

    function addButton(): HTMLButtonElement {
        return fixture.nativeElement.querySelector('[data-testid="user-course-role-add-button"] button');
    }

    function chooseCourse(course: CourseForRoleAssignment | string | undefined): void {
        component().onCourseChange(course);
        fixture.detectChanges();
    }

    it('offers the roles from the least to the most privileged one, with the student role preselected', () => {
        expect(
            component()
                .roleOptions()
                .map((option: { value: string }) => option.value),
        ).toEqual(['STUDENT', 'TEACHING_ASSISTANT', 'EDITOR', 'INSTRUCTOR']);
        expect(component().roleOptions()[1].label).toBe('Tutor');
        expect(component().role()).toBe('STUDENT');
    });

    it('cannot add before a course is chosen, and not when only text was typed', () => {
        expect(addButton().disabled).toBe(true);

        chooseCourse('alg');

        expect(addButton().disabled).toBe(true);
    });

    it('suggests the courses the search finds', () => {
        component().searchCourses({ query: 'alg' });

        httpMock
            .expectOne((request) => request.url === 'api/admin/courses/for-role-assignment' && request.params.get('searchTerm') === 'alg' && request.params.get('size') === '10')
            .flush([algorithms]);

        expect(component().courseSuggestions()).toEqual([algorithms]);
    });

    it('enables adding once a course is chosen that the user does not hold the role in', () => {
        component().onRoleChange('EDITOR');
        chooseCourse(algorithms);

        expect(addButton().disabled).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="user-course-role-add-duplicate"]')).toBeNull();
    });

    it('refuses to add a role the user already holds in the chosen course and says so', () => {
        chooseCourse(algorithms);

        expect(addButton().disabled).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="user-course-role-add-duplicate"]')).not.toBeNull();

        component().add();
        httpMock.expectNone({ method: 'POST' });
    });

    it('adds the role, announces it, clears the course and reports the change', () => {
        const successSpy = vi.spyOn(TestBed.inject(AlertService), 'success');
        component().onRoleChange('INSTRUCTOR');
        chooseCourse(algorithms);

        component().add();

        httpMock.expectOne({ method: 'POST', url: 'api/course/courses/1/instructors/student1' }).flush(null);
        fixture.detectChanges();
        expect(successSpy).toHaveBeenCalledWith('artemisApp.userManagement.courseRoles.add.success', { login: 'student1', course: 'Algorithms', role: 'Instructor' });
        expect(added).toBe(1);
        expect(component().course()).toBeUndefined();
        expect(addButton().disabled).toBe(true);
    });

    it('keeps the chosen course and reports nothing when the request fails', () => {
        component().onRoleChange('EDITOR');
        chooseCourse(algorithms);

        component().add();
        httpMock.expectOne({ method: 'POST', url: 'api/course/courses/1/editors/student1' }).flush('error', { status: 500, statusText: 'Server Error' });
        fixture.detectChanges();

        expect(added).toBe(0);
        expect(component().course()).toEqual(algorithms);
        expect(addButton().disabled).toBe(false);
    });
});
