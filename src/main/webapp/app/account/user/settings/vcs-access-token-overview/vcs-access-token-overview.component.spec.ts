import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockDeleteDialogService } from 'test/helpers/mocks/service/mock-delete-dialog.service';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { VcsAccessTokenOverviewComponent } from 'app/account/user/settings/vcs-access-token-overview/vcs-access-token-overview.component';
import { VcsAccessTokenOverviewService } from 'app/account/user/settings/vcs-access-token-overview/vcs-access-token-overview.service';
import { VcsAccessTokenOverview, VcsAccessTokenType } from 'app/account/user/settings/vcs-access-token-overview/vcs-access-token-overview.model';

describe('VcsAccessTokenOverviewComponent', () => {
    let fixture: ComponentFixture<VcsAccessTokenOverviewComponent>;
    let comp: VcsAccessTokenOverviewComponent;

    const serviceMock = { getTokens: vi.fn(), revokeToken: vi.fn() };
    const alertServiceMock = { error: vi.fn(), success: vi.fn(), addAlert: vi.fn() };

    const tokens: VcsAccessTokenOverview[] = [
        {
            id: 1,
            tokenType: VcsAccessTokenType.REPOSITORY,
            repositoryType: RepositoryType.TEMPLATE,
            courseId: 10,
            courseTitle: 'Course One',
            exerciseId: 100,
            exerciseTitle: 'Exercise A',
            repositoryUri: 'https://artemis.tum.de/git/COURSE1/exercise-a-template.git',
        },
        {
            id: 2,
            tokenType: VcsAccessTokenType.REPOSITORY,
            repositoryType: RepositoryType.USER,
            courseId: 20,
            courseTitle: 'Course Two',
            exerciseId: 200,
            exerciseTitle: 'Exercise B',
            studentLogin: 'student1',
            repositoryUri: 'https://artemis.tum.de/git/COURSE2/exercise-b-student1.git',
        },
        {
            id: 3,
            tokenType: VcsAccessTokenType.PARTICIPATION,
            courseId: 10,
            courseTitle: 'Course One',
            exerciseId: 300,
            exerciseTitle: 'Exercise C',
            repositoryUri: 'https://artemis.tum.de/git/COURSE1/exercise-c.git',
        },
    ];

    beforeEach(async () => {
        serviceMock.getTokens.mockReturnValue(of([...tokens]));
        serviceMock.revokeToken.mockReturnValue(of(undefined));

        await TestBed.configureTestingModule({
            providers: [
                { provide: VcsAccessTokenOverviewService, useValue: serviceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DeleteDialogService, useClass: MockDeleteDialogService },
                { provide: AlertService, useValue: alertServiceMock },
                provideHttpClient(),
                provideRouter([]),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(VcsAccessTokenOverviewComponent);
        comp = fixture.componentInstance;
    });

    it('loads the tokens and exposes them for the current page', () => {
        comp.ngOnInit();
        comp.onDataRequest({ page: 0, pageSize: 20 });

        expect(serviceMock.getTokens).toHaveBeenCalledOnce();
        expect(comp['totalCount']()).toBe(3);
        expect(comp['rows']()).toHaveLength(3);
    });

    it('filters by the search term (course, exercise, student login, repository type and URI) client-side', () => {
        comp.ngOnInit();

        comp.onDataRequest({ page: 0, pageSize: 20, searchTerm: 'student1' });
        expect(comp['rows']().map((token) => token.id)).toEqual([2]);

        comp.onDataRequest({ page: 0, pageSize: 20, searchTerm: 'exercise c' });
        expect(comp['rows']().map((token) => token.id)).toEqual([3]);

        comp.onDataRequest({ page: 0, pageSize: 20, searchTerm: 'course two' });
        expect(comp['rows']().map((token) => token.id)).toEqual([2]);

        // Repository type label (only the template token maps to the "template" label / URI).
        comp.onDataRequest({ page: 0, pageSize: 20, searchTerm: 'template' });
        expect(comp['rows']().map((token) => token.id)).toEqual([1]);

        // Repository URI fragment.
        comp.onDataRequest({ page: 0, pageSize: 20, searchTerm: 'exercise-b-student1' });
        expect(comp['rows']().map((token) => token.id)).toEqual([2]);
    });

    it('paginates the tokens client-side', () => {
        comp.ngOnInit();

        comp.onDataRequest({ page: 0, pageSize: 2 });
        expect(comp['rows']()).toHaveLength(2);
        expect(comp['totalCount']()).toBe(3);

        comp.onDataRequest({ page: 1, pageSize: 2 });
        expect(comp['rows']().map((token) => token.id)).toEqual([3]);
    });

    it('maps every token to a short repository-type label', () => {
        expect(comp['tokenTypeLabelKey'](tokens[0])).toBe('artemisApp.userSettings.vcsAccessTokensOverview.type.template');
        expect(comp['tokenTypeLabelKey'](tokens[1])).toBe('artemisApp.userSettings.vcsAccessTokensOverview.type.assignment');
        expect(comp['tokenTypeLabelKey'](tokens[2])).toBe('artemisApp.userSettings.vcsAccessTokensOverview.type.participation');
    });

    it('links staff repository tokens to course management and participation tokens to the student routes', () => {
        // Repository (staff) token -> course-management (staff have access there).
        expect(comp['courseLink'](tokens[0])).toEqual(['/course-management', 10]);
        expect(comp['exerciseLink'](tokens[0])).toEqual(['/course-management', 10, 'programming-exercises', 100]);
        // Participation token -> student-facing routes (the owner may be a student without course-management access).
        expect(comp['courseLink'](tokens[2])).toEqual(['/courses', 10]);
        expect(comp['exerciseLink'](tokens[2])).toEqual(['/courses', 10, 'exercises', 300]);
    });

    it('routes exam exercises through the exam (participation) or exercise group (staff)', () => {
        const staffExamToken: VcsAccessTokenOverview = {
            id: 9,
            tokenType: VcsAccessTokenType.REPOSITORY,
            repositoryType: RepositoryType.TEMPLATE,
            courseId: 10,
            courseTitle: 'Course One',
            examId: 5,
            exerciseGroupId: 7,
            exerciseId: 100,
            exerciseTitle: 'Exam Exercise',
        };
        expect(comp['exerciseLink'](staffExamToken)).toEqual(['/course-management', 10, 'exams', 5, 'exercise-groups', 7, 'programming-exercises', 100]);

        const participationExamToken: VcsAccessTokenOverview = {
            id: 8,
            tokenType: VcsAccessTokenType.PARTICIPATION,
            courseId: 10,
            courseTitle: 'Course One',
            examId: 5,
            exerciseGroupId: 7,
            exerciseId: 100,
            exerciseTitle: 'Exam Exercise',
        };
        expect(comp['exerciseLink'](participationExamToken)).toEqual(['/courses', 10, 'exams', 5]);
    });

    it('revokes a token, removes it from the list and reports success', () => {
        comp.ngOnInit();
        comp.onDataRequest({ page: 0, pageSize: 20 });

        comp.revokeToken(tokens[1]);

        expect(serviceMock.revokeToken).toHaveBeenCalledWith(VcsAccessTokenType.REPOSITORY, 2);
        expect(comp['rows']().map((token) => token.id)).toEqual([1, 3]);
        expect(comp['totalCount']()).toBe(2);
        expect(alertServiceMock.success).toHaveBeenCalledWith('artemisApp.userSettings.vcsAccessTokensOverview.revoke.success');
    });

    it('revokes a token through the confirmation dialog', () => {
        comp.ngOnInit();
        comp.onDataRequest({ page: 0, pageSize: 20 });

        // The mock delete dialog immediately triggers the confirm callback, so this drives the full revoke flow.
        comp['openRevokeDialog'](tokens[0]);

        expect(serviceMock.revokeToken).toHaveBeenCalledWith(VcsAccessTokenType.REPOSITORY, 1);
        expect(comp['rows']().map((token) => token.id)).toEqual([2, 3]);
    });

    it('reports an error and shows no tokens when loading fails', () => {
        serviceMock.getTokens.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));

        comp.ngOnInit();
        comp.onDataRequest({ page: 0, pageSize: 20 });

        expect(comp['rows']()).toHaveLength(0);
        expect(alertServiceMock.error).toHaveBeenCalledWith('error.http.404');
    });
});
