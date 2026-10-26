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

describe('TutorialGroupManagementService', () => {
    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }] });
    });

    it.each([
        ['TEACHING_ASSISTANT', 'for-tutor'],
        ['EDITOR', 'for-tutor'],
        ['INSTRUCTOR', 'for-instructor'],
    ] as const)('selects %s projection from account and course ID and converts session dates', (role, suffix) => {
        TestBed.inject(AccountService).userIdentity.set(Object.assign(new User(), { id: 1, courseRoles: [{ courseId: 7, roles: [role] }] }));
        let groups: TutorialGroup[] = [];
        TestBed.inject(TutorialGroupManagementService)
            .getTutorialGroupsForCourse(7)
            .subscribe((response) => (groups = response));
        const http = TestBed.inject(HttpTestingController);
        http.expectOne({ method: 'GET', url: `/api/tutorialgroup/courses/7/tutorial-groups/${suffix}` }).flush([
            { id: 3, teachingAssistantName: 'Tutor', nextSession: { start: '2026-10-26T10:00:00Z', end: '2026-10-26T11:00:00Z' } },
        ]);
        expect(groups[0].nextSession?.start?.toISOString()).toBe('2026-10-26T10:00:00.000Z');
        expect(groups[0].teachingAssistantName).toBe('Tutor');
        http.verify();
    });

    it('uses the tutor route for a passkey-blocked administrator', () => {
        const account = TestBed.inject(AccountService);
        account.userIdentity.set(Object.assign(new User(), { id: 1, authorities: ['ROLE_ADMIN'] }));
        vi.spyOn(account, 'isPasskeyRequiredForAdministratorFeatures').mockReturnValue(true);
        TestBed.inject(TutorialGroupManagementService).getTutorialGroupsForCourse(7).subscribe();
        const http = TestBed.inject(HttpTestingController);
        http.expectOne(`/api/tutorialgroup/courses/7/tutorial-groups/for-tutor`).flush([]);
        http.verify();
    });

    it('preserves HTTP errors', () => {
        let status: number | undefined;
        TestBed.inject(TutorialGroupManagementService)
            .getTutorialGroupsForCourse(7)
            .subscribe({ error: (error) => (status = error.status) });
        const http = TestBed.inject(HttpTestingController);
        http.expectOne(`/api/tutorialgroup/courses/7/tutorial-groups/for-tutor`).flush({}, { status: 403, statusText: 'Forbidden' });
        expect(status).toBe(403);
        http.verify();
    });
});
