import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_IRIS } from 'app/app.constants';
import { learnerProfileGuard } from 'app/account/user/settings/learner-profile/learner-profile.guard';

describe('learnerProfileGuard', () => {
    let profileService: ProfileService;
    let router: MockRouter;

    // The guard ignores its arguments, so empty snapshots are sufficient.
    const route = {} as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            providers: [
                { provide: ProfileService, useClass: MockProfileService },
                { provide: Router, useValue: router },
            ],
        });
        profileService = TestBed.inject(ProfileService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    function runGuard(): boolean | UrlTree {
        return TestBed.runInInjectionContext(() => learnerProfileGuard(route, state)) as boolean | UrlTree;
    }

    /** Marks exactly the given module features active, so a test states the whole module configuration it means. */
    function setActiveModules(features: string[]): void {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature) => features.includes(feature));
    }

    it('should allow activation when the atlas module is active', () => {
        setActiveModules([MODULE_FEATURE_ATLAS]);

        expect(runGuard()).toBe(true);
        expect(router.createUrlTree).not.toHaveBeenCalled();
    });

    it('should redirect when only the iris module is active', () => {
        // The learner profile has no iris-backed section, so iris alone must not open an empty page
        setActiveModules([MODULE_FEATURE_IRIS]);
        const redirect = new UrlTree();
        router.createUrlTree.mockReturnValue(redirect);

        expect(runGuard()).toBe(redirect);
        expect(router.createUrlTree).toHaveBeenCalledWith(['/user-settings']);
    });

    it('should redirect when no module backs the page', () => {
        setActiveModules([]);
        const redirect = new UrlTree();
        router.createUrlTree.mockReturnValue(redirect);

        expect(runGuard()).toBe(redirect);
        expect(router.createUrlTree).toHaveBeenCalledWith(['/user-settings']);
    });
});
