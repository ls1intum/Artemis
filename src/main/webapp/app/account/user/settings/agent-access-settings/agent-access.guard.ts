import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { MODULE_FEATURE_AGENT_ACCESS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AccountService } from 'app/core/auth/account.service';

/**
 * Guards the "/user-settings/agent-access" route. Agent access is off unless an instance enables it, and while it is off the
 * server has no token endpoints, so the page would only report failed requests. The sidebar hides the tab; this guard also
 * stops a bookmark or typed URL from opening the page. Only editors and instructors can create tokens, so the page is closed to everybody else.
 *
 * @returns true if agent access is enabled and the user may create tokens, otherwise a redirect to the user-settings root
 */
export const agentAccessGuard: CanActivateFn = (): boolean | UrlTree => {
    const profileService = inject(ProfileService);
    const accountService = inject(AccountService);
    const router = inject(Router);
    return profileService.isModuleFeatureActive(MODULE_FEATURE_AGENT_ACCESS) && accountService.mayCreateAgentTokens() ? true : router.createUrlTree(['/user-settings']);
};
