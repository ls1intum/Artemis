/**
 * Vitest tests for UserCourseRolesComponent.
 * Verifies that the course roles of a user are loaded for the given login and rendered grouped by role.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import { TumAetUiConfirmationService } from '@tumaet/ui-angular';

import { UserCourseRolesComponent } from 'app/admin/user-management/course-roles/user-course-roles.component';
import { UserCourseRole } from 'app/account/user/shared/user-course-role.model';
import { AlertService } from 'app/foundation/service/alert.service';

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
                        title: 'Course Roles',
                        loadError: 'Could not load',
                        retry: 'Try again',
                        courseCount: 'Courses: {{ count }}',
                        empty: { title: 'No course roles', description: 'No course.' },
                        roles: { instructor: 'Instructors', editor: 'Editors', tutor: 'Tutors', student: 'Students' },
                        role: { instructor: 'Instructor', editor: 'Editor', tutor: 'Tutor', student: 'Student' },
                        immediateHint: 'Saved immediately',
                        add: { title: 'Add to a course' },
                        remove: { label: 'Remove {{ role }} in {{ course }}', header: 'Remove', message: 'Remove {{ role }} of {{ login }} in {{ course }}?', accept: 'Remove' },
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
        expect(courseTitles(groups[1])).toEqual(['Algorithms', 'Compilers', 'Databases']);
    });

    it('sorts every group by the displayed label according to the language, then by id, whatever order the server sends', async () => {
        await respondWith([
            { courseId: 9, courseTitle: 'Zebra', courseShortName: 'ZEB', role: 'STUDENT' },
            { courseId: 8, courseTitle: 'algorithms', courseShortName: 'ALGO-B', courseSemester: 'SS26', role: 'STUDENT' },
            { courseId: 7, courseTitle: 'Algorithms', courseShortName: 'ALGO-A', courseSemester: 'WS25', role: 'STUDENT' },
            { courseId: 6, courseShortName: 'Courses without title sort by short name', role: 'STUDENT' },
            { courseId: 5, courseTitle: 'Äpfel', courseShortName: 'AEP', role: 'STUDENT' },
            { courseId: 4, courseTitle: 'Course 10', role: 'STUDENT' },
            { courseId: 3, courseTitle: 'Course 2', role: 'STUDENT' },
        ]);

        const group = element().querySelector('[data-testid="user-course-roles-group-STUDENT"]')!;
        expect(courseTitles(group)).toEqual(['Algorithms', 'algorithms', 'Äpfel', 'Course 2', 'Course 10', 'Courses without title sort by short name', 'Zebra']);
        // Equal titles keep their id order.
        const links = Array.from(group.querySelectorAll('[data-testid="user-course-roles-course"] a')).map((link) => link.getAttribute('href'));
        expect(links.slice(0, 2)).toEqual(['/course-management/7', '/course-management/8']);
    });

    it('shows short name and semester next to the title, so courses with the same title can be told apart', async () => {
        await respondWith([
            { courseId: 1, courseTitle: 'Algorithms', courseShortName: 'ALGO25', courseSemester: 'WS25', role: 'STUDENT' },
            { courseId: 2, courseTitle: 'Databases', courseShortName: 'DB', role: 'STUDENT' },
            { courseId: 3, courseTitle: 'Compilers', role: 'STUDENT' },
        ]);

        const details = Array.from(element().querySelectorAll('[data-testid="user-course-roles-course"]')).map(
            (course) => course.querySelector('[data-testid="user-course-roles-details"]')?.textContent ?? null,
        );
        expect(details).toEqual(['ALGO25 · WS25', null, 'DB']);
    });

    it('presents a real heading for the card and each role without landmarks, and keeps the list semantics', async () => {
        await respondWith(courseRoles);

        expect(element().querySelector('h2')?.textContent).toBe('Course Roles');
        expect(element().querySelector('section')).toBeNull();
        const group = element().querySelector('[data-testid="user-course-roles-group-STUDENT"]')!;
        expect(group.getAttribute('role')).toBe('group');
        const heading = group.querySelector('h3')!;
        expect(group.getAttribute('aria-labelledby')).toBe(heading.id);
        expect(heading.textContent).toBe('Students');
        expect(group.querySelector('ul')?.getAttribute('role')).toBe('list');
        // The count is spoken as text of its own, outside the heading.
        expect(group.querySelector('.sr-only')?.textContent).toBe('Courses: 3');
    });

    it.each([
        ['INSTRUCTOR', 'Instructors'],
        ['EDITOR', 'Editors'],
        ['TEACHING_ASSISTANT', 'Tutors'],
        ['STUDENT', 'Students'],
    ] as const)('names the %s group with the translation of %s', async (role, label) => {
        await respondWith([{ courseId: 1, courseTitle: 'Algorithms', role }]);

        expect(element().querySelector(`[data-testid="user-course-roles-group-${role}"] h3`)?.textContent).toBe(label);
    });

    it('links every course to its course management page', async () => {
        await respondWith(courseRoles);

        const hrefs = Array.from(element().querySelectorAll('[data-testid="user-course-roles-course"] a')).map((link) => link.getAttribute('href'));
        expect(hrefs).toEqual(['/course-management/1', '/course-management/1', '/course-management/3', '/course-management/2']);
    });

    it('labels a course without a title by its short name or id, so the link is never empty', async () => {
        await respondWith([
            { courseId: 5, courseShortName: 'NOTITLE', role: 'STUDENT' },
            { courseId: 6, role: 'STUDENT' },
        ]);

        const links = Array.from(element().querySelectorAll('[data-testid="user-course-roles-course"] a'));
        expect(links.map((link) => link.textContent)).toEqual(['6', 'NOTITLE']);
        expect(element().querySelectorAll('[data-testid="user-course-roles-course"] a + span')).toHaveLength(0);
    });

    it('shows the empty state when the user holds no course role', async () => {
        await respondWith([]);

        expect(element().querySelector('[data-testid="user-course-roles-empty"]')).not.toBeNull();
        expect(element().querySelector('[data-testid^="user-course-roles-group-"]')).toBeNull();
    });

    it('shows an error message with a retry that loads the course roles again', async () => {
        fixture.detectChanges();
        httpMock.expectOne(courseRolesUrl).flush('error', { status: 500, statusText: 'Server Error' });
        await fixture.whenStable();
        fixture.detectChanges();

        expect(element().querySelector('[data-testid="user-course-roles-error"]')).not.toBeNull();
        expect(element().querySelector('[data-testid="user-course-roles-empty"]')).toBeNull();

        (element().querySelector('[data-testid="user-course-roles-retry"] button') as HTMLButtonElement).click();
        fixture.detectChanges();
        httpMock.expectOne({ method: 'GET', url: courseRolesUrl }).flush(courseRoles);
        await fixture.whenStable();
        fixture.detectChanges();

        expect(element().querySelector('[data-testid="user-course-roles-error"]')).toBeNull();
        expect(element().querySelectorAll('[data-testid="user-course-roles-course"]')).toHaveLength(4);
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

    describe('when editable', () => {
        let courseRolesChanged: number;
        let inProgress: boolean[];

        beforeEach(() => {
            courseRolesChanged = 0;
            inProgress = [];
            fixture.componentInstance.changeInProgress.subscribe((state) => inProgress.push(state));
            fixture.componentRef.setInput('editable', true);
            fixture.componentInstance.courseRolesChanged.subscribe(() => courseRolesChanged++);
        });

        function removeButtons(): HTMLElement[] {
            return Array.from(element().querySelectorAll('[data-testid="user-course-roles-remove"] button'));
        }

        it('offers no way to change the roles when read only', async () => {
            fixture.componentRef.setInput('editable', false);
            await respondWith(courseRoles);

            expect(removeButtons()).toHaveLength(0);
            expect(element().querySelector('[data-testid="user-course-role-add"]')).toBeNull();
        });

        it('shows a remove button for every role and the form to add a role', async () => {
            await respondWith(courseRoles);

            expect(removeButtons()).toHaveLength(4);
            expect(removeButtons()[0].getAttribute('aria-label')).toBe('Remove Instructor in Algorithms (ALGO)');
            expect(element().querySelector('[data-testid="user-course-role-add"]')).not.toBeNull();
        });

        it('shows the add form below the empty state for a user without roles', async () => {
            await respondWith([]);

            expect(element().querySelector('[data-testid="user-course-roles-empty"]')).not.toBeNull();
            expect(element().querySelector('[data-testid="user-course-role-add"]')).not.toBeNull();
        });

        it('asks for confirmation before it removes a role and removes nothing when the request is rejected', async () => {
            await respondWith(courseRoles);

            removeButtons()[0].click();

            const request = fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined);
            expect(request?.message).toBe('Remove Instructor of student1 in Algorithms (ALGO)?');
            request?.reject?.();
            httpMock.expectNone({ method: 'DELETE' });
            expect(courseRolesChanged).toBe(0);
        });

        it('removes the role once confirmed and keeps its entry busy until the reloaded list answered', async () => {
            const successSpy = vi.spyOn(TestBed.inject(AlertService), 'success');
            await respondWith(courseRoles);

            removeButtons()[0].click();
            fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined)?.accept();

            httpMock.expectOne({ method: 'DELETE', url: 'api/course/courses/1/instructors/student1' }).flush(null);
            fixture.detectChanges();
            const reload = httpMock.expectOne({ method: 'GET', url: courseRolesUrl });
            // The removed entry is still listed but cannot be used again, and the list does not flash a spinner.
            expect(removeButtons()).toHaveLength(4);
            expect(removeButtons().every((button) => button.hasAttribute('disabled'))).toBe(true);
            expect(element().querySelector('[data-testid="user-course-roles-loading"]')).toBeNull();
            reload.flush(courseRoles.slice(1));
            await fixture.whenStable();
            fixture.detectChanges();

            expect(successSpy).toHaveBeenCalledWith(
                'artemisApp.userManagement.courseRoles.remove.success',
                expect.objectContaining({ login: 'student1', course: 'Algorithms (ALGO)' }),
            );
            expect(courseRolesChanged).toBe(1);
            expect(inProgress).toEqual([true, false]);
            expect(element().querySelector('[data-testid="user-course-roles-group-INSTRUCTOR"]')).toBeNull();
            expect(removeButtons().some((button) => button.hasAttribute('disabled'))).toBe(false);
        });

        it('looks at the server again and reports a change when the removal fails, because the role may be gone although the response got lost', async () => {
            await respondWith(courseRoles);

            removeButtons()[0].click();
            fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined)?.accept();
            httpMock.expectOne({ method: 'DELETE' }).flush('error', { status: 504, statusText: 'Gateway Timeout' });
            fixture.detectChanges();
            httpMock.expectOne({ method: 'GET', url: courseRolesUrl }).flush(courseRoles);
            await fixture.whenStable();
            fixture.detectChanges();

            expect(courseRolesChanged).toBe(1);
            expect(inProgress).toEqual([true, false]);
            expect(removeButtons()).toHaveLength(4);
            expect(removeButtons().some((button) => button.hasAttribute('disabled'))).toBe(false);
        });

        it('moves the focus to the next remove button after a removal, and to the course field when none is left', async () => {
            document.body.appendChild(element());
            await respondWith(courseRoles);

            removeButtons()[1].click();
            fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined)?.accept();
            httpMock.expectOne({ method: 'DELETE' }).flush(null);
            fixture.detectChanges();
            httpMock.expectOne({ method: 'GET', url: courseRolesUrl }).flush([courseRoles[0], courseRoles[2], courseRoles[3]]);
            await fixture.whenStable();
            fixture.detectChanges();
            await fixture.whenStable();

            // The entries are listed Instructors, then Students (Algorithms, Compilers): the removed Students entry was the second button.
            expect(document.activeElement).toBe(removeButtons()[1]);
            element().remove();
        });

        it('looks at the server again and reports a change when adding failed', async () => {
            await respondWith([]);

            fixture.debugElement.query((debugElement) => debugElement.name === 'jhi-user-course-role-add').componentInstance.failed.emit();
            fixture.detectChanges();
            httpMock.expectOne({ method: 'GET', url: courseRolesUrl }).flush(courseRoles);
            await fixture.whenStable();
            fixture.detectChanges();

            expect(courseRolesChanged).toBe(1);
        });

        it('keeps the add form and offers a retry when loading the roles fails', async () => {
            fixture.detectChanges();
            httpMock.expectOne(courseRolesUrl).flush('error', { status: 500, statusText: 'Server Error' });
            await fixture.whenStable();
            fixture.detectChanges();

            expect(element().querySelector('[data-testid="user-course-roles-retry"]')).not.toBeNull();
            expect(element().querySelector('[data-testid="user-course-role-add"]')).not.toBeNull();
        });

        it('does not emit after it was destroyed', async () => {
            await respondWith(courseRoles);
            removeButtons()[0].click();
            fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined)?.accept();
            const request = httpMock.expectOne({ method: 'DELETE' });

            fixture.destroy();
            request.flush(null);

            expect(courseRolesChanged).toBe(0);
            expect(inProgress).toEqual([true]);
        });

        it('reloads the roles and reports the change when a role was added', async () => {
            await respondWith([]);

            fixture.debugElement.query((debugElement) => debugElement.name === 'jhi-user-course-role-add').componentInstance.added.emit();
            fixture.detectChanges();
            httpMock.expectOne({ method: 'GET', url: courseRolesUrl }).flush(courseRoles);
            await fixture.whenStable();
            fixture.detectChanges();

            expect(courseRolesChanged).toBe(1);
            expect(element().querySelectorAll('[data-testid="user-course-roles-course"]')).toHaveLength(4);
        });

        it('blocks every other change while a role is being removed', async () => {
            await respondWith(courseRoles);

            removeButtons()[0].click();
            const confirmation = fixture.debugElement.injector.get(TumAetUiConfirmationService);
            confirmation.request(undefined)?.accept();
            confirmation.close(undefined);
            fixture.detectChanges();

            expect(removeButtons().every((button) => button.hasAttribute('disabled'))).toBe(true);
            expect(fixture.debugElement.query((debugElement) => debugElement.name === 'jhi-user-course-role-add').componentInstance.disabled()).toBe(true);
            removeButtons()[1].click();
            expect(fixture.debugElement.injector.get(TumAetUiConfirmationService).request(undefined)).toBeUndefined();
            httpMock.expectOne({ method: 'DELETE' }).flush(null);
            fixture.detectChanges();
            httpMock.expectOne({ method: 'GET', url: courseRolesUrl }).flush(courseRoles.slice(1));
        });

        it('blocks removing a role while a role is being added', async () => {
            await respondWith(courseRoles);

            fixture.debugElement.query((debugElement) => debugElement.name === 'jhi-user-course-role-add').componentInstance.changing.emit(true);
            fixture.detectChanges();

            expect(removeButtons().every((button) => button.hasAttribute('disabled'))).toBe(true);
        });

        it('blocks every change while the host disables the component', async () => {
            fixture.componentRef.setInput('disabled', true);
            await respondWith(courseRoles);

            expect(removeButtons().every((button) => button.hasAttribute('disabled'))).toBe(true);
            expect(fixture.debugElement.query((debugElement) => debugElement.name === 'jhi-user-course-role-add').componentInstance.disabled()).toBe(true);
        });

        it('forwards the progress of adding a role', async () => {
            await respondWith([]);

            fixture.debugElement.query((debugElement) => debugElement.name === 'jhi-user-course-role-add').componentInstance.changing.emit(true);

            expect(inProgress).toEqual([true]);
        });
    });
});
