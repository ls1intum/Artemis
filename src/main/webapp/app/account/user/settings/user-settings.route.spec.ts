import { describe, expect, it } from 'vitest';
import { routes } from './user-settings.route';
import { DataGuard } from 'app/account/user/settings/ai-experience/data-guard.service';
import { learnerProfileGuard } from 'app/account/user/settings/learning/learner-profile.guard';

describe('user settings routes', () => {
    it('provides the canonical settings route tree with Profile as its default', () => {
        const root = routes[0];

        expect(root.path).toBe('user-settings');
        expect(root.data?.usesModuleBackground).toBe(false);
        expect(root.children?.find((route) => route.path === '')?.redirectTo).toBe('profile');
        expect(root.children?.filter((route) => route.loadComponent).map((route) => route.path)).toEqual([
            'profile',
            'credentials',
            'credentials/ssh/add',
            'credentials/ssh/fingerprints',
            'credentials/ssh/view/:keyId',
            'learning',
            'ai-experience',
            'exercises',
        ]);
    });

    it('provides SSH detail pages with the existing SSH title', () => {
        const sshRoutes = routes[0].children?.filter((route) => route.path?.startsWith('credentials/ssh/'));

        expect(sshRoutes?.map((route) => route.path)).toEqual(['credentials/ssh/add', 'credentials/ssh/fingerprints', 'credentials/ssh/view/:keyId']);
        expect(sshRoutes?.every((route) => route.data?.pageTitle === 'artemisApp.userSettings.categories.SSH_SETTINGS')).toBe(true);
        expect(sshRoutes?.every((route) => route.loadComponent)).toBe(true);
    });

    it('protects conditional tabs with their existing availability guards', () => {
        const children = routes[0].children;

        expect(children?.find((route) => route.path === 'learning')?.canActivate).toEqual([learnerProfileGuard]);
        expect(children?.find((route) => route.path === 'ai-experience')?.canActivate).toEqual([DataGuard]);
    });

    it('redirects former leaf routes to their canonical grouped pages', () => {
        const redirects = Object.fromEntries(routes[0].children?.filter((route) => route.path && route.redirectTo).map((route) => [route.path, route.redirectTo]) ?? []);

        expect(redirects).toEqual({
            account: 'profile',
            notifications: 'profile',
            science: 'profile',
            passkeys: 'credentials',
            ssh: 'credentials',
            'ssh/add': 'credentials/ssh/add',
            'ssh/fingerprints': 'credentials/ssh/fingerprints',
            'ssh/view/:keyId': 'credentials/ssh/view/:keyId',
            'vcs-token': 'credentials',
            'access-tokens': 'credentials',
            'llm-usage': 'ai-experience',
            'ide-preferences': 'exercises',
            'quiz-training': 'exercises',
        });
        expect(routes[0].children?.filter((route) => route.redirectTo).every((route) => route.pathMatch === 'full')).toBe(true);
    });
});
