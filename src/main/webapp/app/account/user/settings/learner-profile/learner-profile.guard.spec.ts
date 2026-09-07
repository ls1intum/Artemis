import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { Observable, firstValueFrom, isObservable, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockFeatureToggleService } from 'test/helpers/mocks/service/mock-feature-toggle.service';
import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_IRIS } from 'app/app.constants';
import { FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { learnerProfileGuard } from 'app/account/user/settings/learner-profile/learner-profile.guard';

describe('learnerProfileGuard', () => {
    let profileService: ProfileService;
    let featureToggleService: FeatureToggleService;
    let router: MockRouter;

    // The guard ignores its arguments, so empty snapshots are sufficient.
    const route = {} as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;

    beforeEach(() => {
        router = new MockRouter();
        TestBed.configureTestingModule({
            providers: [
                { provide: ProfileService, useClass: MockProfileService },
                { provide: FeatureToggleService, useClass: MockFeatureToggleService },
                { provide: Router, useValue: router },
            ],
        });
        profileService = TestBed.inject(ProfileService);
        featureToggleService = TestBed.inject(FeatureToggleService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    /**
     * Runs the guard and resolves its verdict, asserting on the way that it stays a single-value observable — the
     * router only ever reads the first emission.
     */
    async function runGuard(): Promise<boolean | UrlTree> {
        const result = TestBed.runInInjectionContext(() => learnerProfileGuard(route, state));
        expect(isObservable(result)).toBe(true);
        return await firstValueFrom(result as Observable<boolean | UrlTree>);
    }

    /** Marks exactly the given module features active, so a test states the whole module configuration it means. */
    function setActiveModules(features: string[]): void {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature) => features.includes(feature));
    }

    function setMemirisActive(active: boolean): void {
        vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(active));
    }

    function expectRedirectToUserSettings(result: boolean | UrlTree, redirect: UrlTree): void {
        expect(router.createUrlTree).toHaveBeenCalledWith(['/user-settings']);
        expect(result).toBe(redirect);
    }

    it('should allow activation when the atlas module is active', async () => {
        setActiveModules([MODULE_FEATURE_ATLAS]);
        setMemirisActive(false);

        await expect(runGuard()).resolves.toBe(true);
        expect(router.createUrlTree).not.toHaveBeenCalled();
    });

    it('should allow activation for the insights section when the iris module and Memiris are active', async () => {
        setActiveModules([MODULE_FEATURE_IRIS]);
        setMemirisActive(true);

        await expect(runGuard()).resolves.toBe(true);
        expect(router.createUrlTree).not.toHaveBeenCalled();
    });

    it('should redirect when Memiris is active but the iris module serving it is not', async () => {
        // IrisMemoryResource is @Conditional(IrisEnabled) as well as @FeatureToggle(Memiris), and the toggle is
        // seeded independently of the module, so the toggle alone must not open a page that would answer 404
        setActiveModules([]);
        setMemirisActive(true);
        const redirect = new UrlTree();
        router.createUrlTree.mockReturnValue(redirect);

        expectRedirectToUserSettings(await runGuard(), redirect);
    });

    it('should redirect when the iris module is active but Memiris is off', async () => {
        setActiveModules([MODULE_FEATURE_IRIS]);
        setMemirisActive(false);
        const redirect = new UrlTree();
        router.createUrlTree.mockReturnValue(redirect);

        expectRedirectToUserSettings(await runGuard(), redirect);
    });

    it('should redirect when no module backs any section of the page', async () => {
        setActiveModules([]);
        setMemirisActive(false);
        const redirect = new UrlTree();
        router.createUrlTree.mockReturnValue(redirect);

        expectRedirectToUserSettings(await runGuard(), redirect);
    });
});
