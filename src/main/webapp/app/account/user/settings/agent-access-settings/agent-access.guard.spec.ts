import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { MODULE_FEATURE_AGENT_ACCESS } from 'app/app.constants';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { agentAccessGuard } from 'app/account/user/settings/agent-access-settings/agent-access.guard';

describe('agentAccessGuard', () => {
    let activeFeatures: string[];
    let mayCreateAgentTokens: boolean;

    beforeEach(() => {
        activeFeatures = [];
        mayCreateAgentTokens = true;
        TestBed.configureTestingModule({
            providers: [
                provideRouter([]),
                { provide: ProfileService, useValue: { isModuleFeatureActive: (feature: string) => activeFeatures.includes(feature) } },
                { provide: AccountService, useValue: { mayCreateAgentTokens: () => mayCreateAgentTokens } },
            ],
        });
    });

    it('lets the route open when agent access is enabled and the user may create tokens', () => {
        activeFeatures = [MODULE_FEATURE_AGENT_ACCESS];

        expect(TestBed.runInInjectionContext(() => agentAccessGuard({} as never, {} as never))).toBe(true);
    });

    it('redirects to the user settings when agent access is disabled', () => {
        const result = TestBed.runInInjectionContext(() => agentAccessGuard({} as never, {} as never));

        expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/user-settings');
    });

    it('redirects to the user settings when the user may not create tokens', () => {
        activeFeatures = [MODULE_FEATURE_AGENT_ACCESS];
        mayCreateAgentTokens = false;

        const result = TestBed.runInInjectionContext(() => agentAccessGuard({} as never, {} as never));

        expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/user-settings');
    });
});
