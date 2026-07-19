import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
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
        { id: 1, tokenType: VcsAccessTokenType.REPOSITORY, repositoryType: RepositoryType.TEMPLATE, courseTitle: 'Course One', exerciseTitle: 'Exercise A' },
        { id: 2, tokenType: VcsAccessTokenType.REPOSITORY, repositoryType: RepositoryType.USER, courseTitle: 'Course Two', exerciseTitle: 'Exercise B', studentLogin: 'student1' },
        { id: 3, tokenType: VcsAccessTokenType.PARTICIPATION, courseTitle: 'Course One', exerciseTitle: 'Exercise C' },
    ];

    beforeEach(async () => {
        serviceMock.getTokens.mockReturnValue(of([...tokens]));
        serviceMock.revokeToken.mockReturnValue(of(undefined));

        await TestBed.configureTestingModule({
            providers: [
                { provide: VcsAccessTokenOverviewService, useValue: serviceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: alertServiceMock },
                provideHttpClient(),
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

    it('filters by the search term (course title, exercise title and student login) client-side', () => {
        comp.ngOnInit();

        comp.onDataRequest({ page: 0, pageSize: 20, searchTerm: 'student1' });
        expect(comp['rows']().map((token) => token.id)).toEqual([2]);

        comp.onDataRequest({ page: 0, pageSize: 20, searchTerm: 'exercise c' });
        expect(comp['rows']().map((token) => token.id)).toEqual([3]);

        comp.onDataRequest({ page: 0, pageSize: 20, searchTerm: 'course two' });
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

    it('revokes a token, removes it from the list and reports success', () => {
        comp.ngOnInit();
        comp.onDataRequest({ page: 0, pageSize: 20 });

        comp.revokeToken(tokens[1]);

        expect(serviceMock.revokeToken).toHaveBeenCalledWith(VcsAccessTokenType.REPOSITORY, 2);
        expect(comp['rows']().map((token) => token.id)).toEqual([1, 3]);
        expect(comp['totalCount']()).toBe(2);
        expect(alertServiceMock.success).toHaveBeenCalledWith('artemisApp.userSettings.vcsAccessTokensOverview.revoke.success');
    });

    it('reports an error and shows no tokens when loading fails', () => {
        serviceMock.getTokens.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));

        comp.ngOnInit();
        comp.onDataRequest({ page: 0, pageSize: 20 });

        expect(comp['rows']()).toHaveLength(0);
        expect(alertServiceMock.error).toHaveBeenCalledWith('error.http.404');
    });
});
