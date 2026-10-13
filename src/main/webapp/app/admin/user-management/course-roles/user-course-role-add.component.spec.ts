/**
 * Vitest tests for UserCourseRoleAddComponent.
 * Types into the real course input like a user does, and verifies the search, the guard against adding an existing role and the request that adds the role.
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
    let failed: number;

    const algorithms: CourseForRoleAssignment = { id: 1, title: 'Algorithms', shortName: 'ALGO25', semester: 'WS25' };
    const existingRoles: UserCourseRole[] = [{ courseId: 1, courseTitle: 'Algorithms', courseShortName: 'ALGO25', role: 'STUDENT' }];
    const algorithmsLabel = 'ALGO25 · Algorithms (WS25)';

    beforeEach(async () => {
        vi.useFakeTimers();
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
                        add: { noCourseFound: 'No course found' },
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
        failed = 0;
        fixture.componentInstance.added.subscribe(() => added++);
        fixture.componentInstance.failed.subscribe(() => failed++);
        fixture.detectChanges();
    });

    afterEach(() => {
        httpMock.verify();
        vi.useRealTimers();
    });

    function component(): any {
        return fixture.componentInstance;
    }

    function courseInput(): HTMLInputElement {
        return fixture.nativeElement.querySelector('[data-testid="user-course-role-add-course"] input');
    }

    function addButton(): HTMLButtonElement {
        return fixture.nativeElement.querySelector('[data-testid="user-course-role-add-button"] button');
    }

    function options(): HTMLElement[] {
        return Array.from(document.querySelectorAll<HTMLElement>('.cdk-overlay-container [role="option"]'));
    }

    /** Types into the real input like a user: focus, then the text arrives as an input event, and the search follows after the debounce. */
    function type(text: string): void {
        const input = courseInput();
        input.dispatchEvent(new Event('focus'));
        input.value = text;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        fixture.detectChanges();
    }

    function searchRequest(term: string) {
        return httpMock.expectOne(
            (request) => request.url === 'api/admin/courses/for-role-assignment' && request.params.get('searchTerm') === term && request.params.get('size') === '10',
        );
    }

    /** Types the text, lets the search answer and picks the first suggestion. */
    function typeAndChoose(text: string, suggestions: CourseForRoleAssignment[]): void {
        type(text);
        vi.advanceTimersByTime(300);
        searchRequest(text).flush(suggestions);
        fixture.detectChanges();
        options()[0].click();
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

        type('alg');

        expect(addButton().disabled).toBe(true);
        vi.advanceTimersByTime(300);
        searchRequest('alg').flush([]);
    });

    it('suggests the courses the search finds, with the short name first and the semester last', () => {
        type('alg');
        vi.advanceTimersByTime(300);
        searchRequest('alg').flush([
            algorithms,
            { id: 2, title: 'Algorithms', shortName: 'ALGO26', semester: 'SS26' },
            { id: 3, title: 'Databases', shortName: 'DB', semester: 'WS25' },
        ]);
        fixture.detectChanges();

        expect(options().map((option) => option.textContent?.trim())).toEqual([algorithmsLabel, 'ALGO26 · Algorithms (SS26)', 'DB · Databases (WS25)']);
    });

    it('keeps what is typed after a course was chosen instead of clearing the field', () => {
        typeAndChoose('alg', [algorithms]);
        expect(courseInput().value).toBe(algorithmsLabel);
        expect(addButton().disabled).toBe(true);
        component().onRoleChange('EDITOR');
        fixture.detectChanges();
        expect(addButton().disabled).toBe(false);

        // The next keystroke edits the text, it must not wipe it.
        type(`${algorithmsLabel}x`);
        fixture.detectChanges();

        expect(courseInput().value).toBe(`${algorithmsLabel}x`);
        // The text no longer names a course.
        expect(addButton().disabled).toBe(true);
        vi.advanceTimersByTime(300);
        searchRequest(`${algorithmsLabel}x`).flush([]);
        fixture.detectChanges();
        expect(courseInput().value).toBe(`${algorithmsLabel}x`);
    });

    it('answers only the latest search, so a slower earlier search cannot replace its suggestions', () => {
        type('al');
        vi.advanceTimersByTime(300);
        const earlier = searchRequest('al');
        type('alg');
        vi.advanceTimersByTime(300);
        expect(earlier.cancelled).toBe(true);
        searchRequest('alg').flush([algorithms]);
        fixture.detectChanges();

        expect(options().map((option) => option.textContent?.trim())).toEqual([algorithmsLabel]);
    });

    it('shows no suggestions when the search fails', () => {
        type('alg');
        vi.advanceTimersByTime(300);
        searchRequest('alg').flush('error', { status: 500, statusText: 'Server Error' });
        fixture.detectChanges();

        expect(component().courseSuggestions()).toEqual([]);
    });

    it('enables adding once a course is chosen that the user does not hold the role in', () => {
        component().onRoleChange('EDITOR');
        typeAndChoose('alg', [algorithms]);

        expect(addButton().disabled).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="user-course-role-add-duplicate"]')).toBeNull();
    });

    it('refuses to add a role the user already holds in the chosen course and says so', () => {
        typeAndChoose('alg', [algorithms]);

        expect(addButton().disabled).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="user-course-role-add-duplicate"]')).not.toBeNull();

        component().add();
        httpMock.expectNone({ method: 'POST' });
    });

    it('adds the role, announces it with the course and its short name, clears the course and reports the change', async () => {
        const successSpy = vi.spyOn(TestBed.inject(AlertService), 'success');
        component().onRoleChange('INSTRUCTOR');
        typeAndChoose('alg', [algorithms]);

        addButton().click();

        httpMock.expectOne({ method: 'POST', url: 'api/course/courses/1/instructors/student1' }).flush(null);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(successSpy).toHaveBeenCalledWith('artemisApp.userManagement.courseRoles.add.success', { login: 'student1', course: 'Algorithms (ALGO25)', role: 'Instructor' });
        expect(added).toBe(1);
        expect(courseInput().value).toBe('');
        expect(addButton().disabled).toBe(true);
    });

    it('keeps the chosen course and reports the failure when the request fails', () => {
        component().onRoleChange('EDITOR');
        typeAndChoose('alg', [algorithms]);

        addButton().click();
        httpMock.expectOne({ method: 'POST', url: 'api/course/courses/1/editors/student1' }).flush('error', { status: 500, statusText: 'Server Error' });
        fixture.detectChanges();

        expect(added).toBe(0);
        expect(failed).toBe(1);
        expect(courseInput().value).toBe(algorithmsLabel);
        expect(addButton().disabled).toBe(false);
    });

    it('announces the role that was submitted even when another role is selected while the request is pending', () => {
        const successSpy = vi.spyOn(TestBed.inject(AlertService), 'success');
        component().onRoleChange('INSTRUCTOR');
        typeAndChoose('alg', [algorithms]);

        addButton().click();
        component().onRoleChange('EDITOR');
        httpMock.expectOne({ method: 'POST', url: 'api/course/courses/1/instructors/student1' }).flush(null);

        expect(successSpy).toHaveBeenCalledWith('artemisApp.userManagement.courseRoles.add.success', expect.objectContaining({ role: 'Instructor' }));
    });

    it('reports when a request is running and when it ended', () => {
        const states: boolean[] = [];
        fixture.componentInstance.changing.subscribe((inProgress) => states.push(inProgress));
        component().onRoleChange('EDITOR');
        typeAndChoose('alg', [algorithms]);

        addButton().click();
        expect(states).toEqual([true]);
        httpMock.expectOne({ method: 'POST' }).flush('error', { status: 500, statusText: 'Server Error' });

        expect(states).toEqual([true, false]);
    });

    it('does not emit when the response arrives after the component was destroyed', () => {
        component().onRoleChange('EDITOR');
        typeAndChoose('alg', [algorithms]);
        addButton().click();
        const request = httpMock.expectOne({ method: 'POST' });

        fixture.destroy();
        request.flush(null);

        expect(added).toBe(0);
    });

    it('cannot add while the host disables it', () => {
        component().onRoleChange('EDITOR');
        typeAndChoose('alg', [algorithms]);
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();

        expect(addButton().disabled).toBe(true);
        component().add();
        httpMock.expectNone({ method: 'POST' });
    });

    it('does not let the enter key in the course field submit a surrounding form', () => {
        const enter = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });

        courseInput().dispatchEvent(enter);

        expect(enter.defaultPrevented).toBe(true);
    });
});
