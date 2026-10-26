import { describe, expect, it } from 'vitest';
import { routes } from './user-settings-v2.route';

describe('user settings V2 routes', () => {
    it('provides the parallel settings route tree with Profile as its default', () => {
        const root = routes[0];

        expect(root.path).toBe('user-settings-v2');
        expect(root.data?.usesModuleBackground).toBe(false);
        expect(root.children?.find((route) => route.path === '')?.redirectTo).toBe('profile');
        expect(root.children?.map((route) => route.path)).toEqual(['', 'profile', 'credentials', 'learning', 'ai-experience', 'exercises']);
        expect(root.children?.filter((route) => route.path).every((route) => route.loadComponent)).toBe(true);
    });
});
