import { beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { MODULE_FEATURE_AGENT_ACCESS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { agentAccessGuard } from 'app/account/user/settings/agent-access-settings/agent-access.guard';

describe('agentAccessGuard', () => {
    let activeFeatures: string[];

    beforeEach(() => {
        activeFeatures = [];
        TestBed.configureTestingModule({
            providers: [provideRouter([]), { provide: ProfileService, useValue: { isModuleFeatureActive: (feature: string) => activeFeatures.includes(feature) } }],
        });
    });

    it('lets the route open when agent access is enabled', () => {
        activeFeatures = [MODULE_FEATURE_AGENT_ACCESS];

        expect(TestBed.runInInjectionContext(() => agentAccessGuard({} as never, {} as never))).toBe(true);
    });

    it('redirects to the user settings when agent access is disabled', () => {
        const result = TestBed.runInInjectionContext(() => agentAccessGuard({} as never, {} as never));

        expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/user-settings');
    });
});
