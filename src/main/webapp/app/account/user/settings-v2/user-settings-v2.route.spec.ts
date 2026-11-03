import { describe, expect, it } from 'vitest';
import { routes } from './user-settings-v2.route';
import { DataGuard } from 'app/account/user/settings/data-guard.service';
import { learnerProfileGuard } from 'app/account/user/settings/learner-profile/learner-profile.guard';

describe('user settings V2 routes', () => {
    it('provides the parallel settings route tree with Profile as its default', () => {
        const root = routes[0];

        expect(root.path).toBe('user-settings-v2');
        expect(root.data?.usesModuleBackground).toBe(false);
        expect(root.children?.find((route) => route.path === '')?.redirectTo).toBe('profile');
        expect(root.children?.map((route) => route.path)).toEqual([
            '',
            'profile',
            'credentials',
            'credentials/ssh/add',
            'credentials/ssh/fingerprints',
            'credentials/ssh/view/:keyId',
            'learning',
            'ai-experience',
            'exercises',
        ]);
        expect(root.children?.filter((route) => route.path).every((route) => route.loadComponent)).toBe(true);
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
});
