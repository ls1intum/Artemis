import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
// DeleteDialogService is still built on PrimeNG's dynamic dialog, so its dependency has to be provided here.
import { DialogService } from 'primeng/dynamicdialog';
import { AgentAccessSettingsComponent } from 'app/account/user/settings/agent-access-settings/agent-access-settings.component';
import { PersonalAccessTokenApiService } from 'app/account/user/settings/agent-access-settings/personal-access-token-api.service';
import { PersonalAccessTokenDTO } from 'app/account/user/settings/agent-access-settings/dto/personal-access-token.dto';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

const existingToken: PersonalAccessTokenDTO = {
    id: 1,
    name: 'existing',
    tokenPrefix: 'art_pat_Zz9Y',
    scopes: ['courses:read'],
    createdDate: '2026-10-30T10:00:00Z',
    expiryDate: '2026-11-30T10:00:00Z',
};

describe('AgentAccessSettingsComponent', () => {
    let fixture: ComponentFixture<AgentAccessSettingsComponent>;
    let component: AgentAccessSettingsComponent;
    let api: PersonalAccessTokenApiService;
    let alertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                DialogService,
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        api = TestBed.inject(PersonalAccessTokenApiService);
        alertService = TestBed.inject(AlertService);
        vi.spyOn(alertService, 'success');
        vi.spyOn(alertService, 'error');
        vi.spyOn(api, 'getTokens').mockResolvedValue([existingToken]);
        vi.spyOn(api, 'getScopes').mockResolvedValue(['courses:read', 'competencies:write']);

        fixture = TestBed.createComponent(AgentAccessSettingsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('loads the tokens and the available scopes on init', () => {
        expect(component.tokens()).toEqual([existingToken]);
        expect(component.availableScopes()).toEqual(['courses:read', 'competencies:write']);
    });

    it('only allows creating a token with a name, a scope and a lifetime', () => {
        expect(component.canCreate()).toBe(false);

        component.tokenName.set('agent');
        expect(component.canCreate()).toBe(false);

        component.setScopeSelected('competencies:write', true);
        expect(component.canCreate()).toBe(true);

        component.setScopeSelected('competencies:write', false);
        expect(component.canCreate()).toBe(false);
    });

    it('does not select a scope twice', () => {
        component.setScopeSelected('courses:read', true);
        component.setScopeSelected('courses:read', true);

        expect(component.selectedScopes()).toEqual(['courses:read']);
    });

    it('creates a token, shows its secret once and resets the form', async () => {
        const created = { token: { ...existingToken, id: 2, name: 'agent' }, secret: 'art_pat_the_secret' };
        const createSpy = vi.spyOn(api, 'createToken').mockResolvedValue(created);
        component.startCreation();
        component.tokenName.set(' agent ');
        component.setScopeSelected('competencies:write', true);
        component.onLifetimeInput('14');

        await component.createToken();

        expect(createSpy).toHaveBeenCalledWith({ name: 'agent', scopes: ['competencies:write'], expiresInDays: 14 });
        expect(component.createdSecret()).toBe('art_pat_the_secret');
        expect(component.creating()).toBe(false);
        expect(component.tokenName()).toBe('');
        expect(component.selectedScopes()).toEqual([]);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.userSettings.agentAccess.createSuccess');
    });

    it('reports a failed creation and keeps the form', async () => {
        vi.spyOn(api, 'createToken').mockRejectedValue(new Error('400'));
        component.startCreation();
        component.tokenName.set('agent');
        component.setScopeSelected('competencies:write', true);

        await component.createToken();

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.userSettings.agentAccess.createFailure');
        expect(component.creating()).toBe(true);
        expect(component.createdSecret()).toBeUndefined();
    });

    it('drops a displayed secret when a new creation starts', () => {
        component.createdSecret.set('art_pat_old');

        component.startCreation();

        expect(component.createdSecret()).toBeUndefined();
    });

    it('revokes a token and reloads the list', async () => {
        const revokeSpy = vi.spyOn(api, 'revokeToken').mockResolvedValue();
        vi.mocked(api.getTokens).mockResolvedValue([]);

        await component.revokeToken(1);

        expect(revokeSpy).toHaveBeenCalledWith(1);
        expect(component.tokens()).toEqual([]);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.userSettings.agentAccess.revokeSuccess');
    });

    it('reports a failed revocation', async () => {
        vi.spyOn(api, 'revokeToken').mockRejectedValue(new Error('404'));

        await component.revokeToken(1);

        expect(alertService.error).toHaveBeenCalledWith('artemisApp.userSettings.agentAccess.revokeFailure');
    });

    it('builds a translation key without a colon for a scope', () => {
        expect(component.scopeLabelKey('competencies:write')).toBe('artemisApp.userSettings.agentAccess.scopes.competencies_write');
    });
});
