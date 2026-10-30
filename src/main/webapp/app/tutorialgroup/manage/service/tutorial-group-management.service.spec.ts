import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { User } from 'app/account/user/user.model';
import { TutorialGroupManagementService } from './tutorial-group-management.service';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSummary } from 'app/openapi/model/tutorial-group-summary';

describe('TutorialGroupManagementService', () => {
    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }] });
    });

    it.each([
        ['TEACHING_ASSISTANT', 'for-tutor'],
        ['EDITOR', 'for-tutor'],
        ['INSTRUCTOR', 'for-instructor'],
    ] as const)('selects %s projection from the current course roles and converts session dates', (role, suffix) => {
        // the roles loaded with the account are outdated, the projection follows the current ones
        TestBed.inject(AccountService).userIdentity.set(Object.assign(new User(), { id: 1, courseRoles: [] }));
        let groups: TutorialGroup[] = [];
        TestBed.inject(TutorialGroupManagementService)
            .getTutorialGroupsForCourse(7)
            .subscribe((response) => (groups = response));
        const http = TestBed.inject(HttpTestingController);
        http.expectOne({ method: 'GET', url: 'api/account/course-roles?courseId=7' }).flush({ courseId: 7, roles: [role] });
        const summary: TutorialGroupSummary = {
            id: 3,
            title: 'Tutorial',
            teachingAssistantName: 'Tutor',
            teachingAssistantId: 5,
            isUserTutor: true,
            numberOfRegisteredUsers: 8,
            averageAttendance: 6,
            tutorialGroupSchedule: { validFromInclusive: '2026-10-26', validToInclusive: '2027-02-01' },
            nextSession: { start: '2026-10-26T10:00:00Z', end: '2026-10-26T11:00:00Z' },
        };
        http.expectOne({ method: 'GET', url: `/api/tutorialgroup/courses/7/tutorial-groups/${suffix}` }).flush([summary]);
        expect(groups[0]).toBeInstanceOf(TutorialGroup);
        expect(groups[0].nextSession?.start?.toISOString()).toBe('2026-10-26T10:00:00.000Z');
        expect(groups[0].nextSession?.end?.toISOString()).toBe('2026-10-26T11:00:00.000Z');
        expect(groups[0].tutorialGroupSchedule?.validFromInclusive?.format('YYYY-MM-DD')).toBe('2026-10-26');
        expect(groups[0].tutorialGroupSchedule?.validToInclusive?.format('YYYY-MM-DD')).toBe('2027-02-01');
        expect(groups[0]).toMatchObject({
            title: 'Tutorial',
            teachingAssistantName: 'Tutor',
            teachingAssistantId: 5,
            isUserTutor: true,
            numberOfRegisteredUsers: 8,
            averageAttendance: 6,
        });
        http.verify();
    });

    it('uses the tutor route for a passkey-blocked administrator', () => {
        const account = TestBed.inject(AccountService);
        account.userIdentity.set(Object.assign(new User(), { id: 1, authorities: ['ROLE_ADMIN'] }));
        vi.spyOn(account, 'isPasskeyRequiredForAdministratorFeatures').mockReturnValue(true);
        TestBed.inject(TutorialGroupManagementService).getTutorialGroupsForCourse(7).subscribe();
        const http = TestBed.inject(HttpTestingController);
        http.expectOne('api/account/course-roles?courseId=7').flush({ courseId: 7 });
        http.expectOne(`/api/tutorialgroup/courses/7/tutorial-groups/for-tutor`).flush([]);
        http.verify();
    });

    it('preserves HTTP errors', () => {
        let status: number | undefined;
        TestBed.inject(TutorialGroupManagementService)
            .getTutorialGroupsForCourse(7)
            .subscribe({ error: (error) => (status = error.status) });
        const http = TestBed.inject(HttpTestingController);
        http.expectOne('api/account/course-roles?courseId=7').flush({ courseId: 7, roles: ['TEACHING_ASSISTANT'] });
        http.expectOne(`/api/tutorialgroup/courses/7/tutorial-groups/for-tutor`).flush({}, { status: 403, statusText: 'Forbidden' });
        expect(status).toBe(403);
        http.verify();
    });
});
